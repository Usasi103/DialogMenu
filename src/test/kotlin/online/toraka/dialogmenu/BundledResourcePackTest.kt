package online.toraka.dialogmenu

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BundledResourcePackTest {
    @TempDir lateinit var directory: Path

    private val font = "assets/toraka_settings/font/ui.json"
    private val shader = "assets/minecraft/shaders/core/gui.vsh"
    private val automatic = MenuResourcePack("Menu", "auto", "default", "", null, "", false)

    private fun archive(vararg assets: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, contents) in
                listOf("pack.mcmeta" to "{}", "LICENSE-test.txt" to "license") + assets) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun provider(type: ResourcePackProvider) =
        AvailableResourceProvider(type, directory.resolve(type.pluginName))

    private fun install(
        bytes: ByteArray,
        available: List<AvailableResourceProvider>,
        config: MenuResourcePack = automatic,
    ) = BundledResourcePack(directory.resolve("DialogMenu"), bytes).install(config, available)

    private fun write(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }

    @Test
    fun `without a provider export a usable unchanged ZIP and do not invent provider directories`() {
        val bytes = archive(font to "original", shader to "shader")
        val result = install(bytes, emptyList())
        assertNull(result.provider)
        assertArrayEquals(bytes, Files.readAllBytes(result.exportedZip))
        assertTrue(result.conflicts.isEmpty())
        assertFalse(Files.exists(directory.resolve("CraftEngine")))
    }

    @Test
    fun `CraftEngine wins regardless of provider order and has a proper pack manifest`() {
        val ce = provider(ResourcePackProvider.CRAFT_ENGINE)
        val others = ResourcePackProvider.entries.reversed().map { provider(it) }
        val result = install(archive(font to "font"), others)
        assertEquals(ce, result.provider)
        assertEquals(
            "font",
            Files.readString(ce.directory.resolve("resources/dialogmenu/resourcepack/$font")),
        )
        assertTrue(
            Files.readString(ce.directory.resolve("resources/dialogmenu/pack.yml"))
                .contains("namespace: dialogmenu")
        )
        assertEquals(
            "license",
            Files.readString(
                ce.directory.resolve("resources/dialogmenu/resourcepack/LICENSE-test.txt")
            ),
        )
        assertFalse(Files.exists(directory.resolve("Nexo")))
    }

    @Test
    fun `repeat installation changes nothing and an upgrade preserves modified files`() {
        val ce = provider(ResourcePackProvider.CRAFT_ENGINE)
        val first = archive(font to "font-v1", shader to "shader-v1")
        assertTrue(install(first, listOf(ce)).changed > 0)
        val target = ce.directory.resolve("resources/dialogmenu/resourcepack/$font")
        val timestamp = Files.getLastModifiedTime(target)
        assertEquals(0, install(first, listOf(ce)).changed)
        assertEquals(timestamp, Files.getLastModifiedTime(target))
        write(target, "server-custom-font")
        val second = archive(font to "font-v2", shader to "shader-v2")
        val result = install(second, listOf(ce))
        assertEquals("server-custom-font", Files.readString(target))
        assertEquals(
            "shader-v2",
            Files.readString(ce.directory.resolve("resources/dialogmenu/resourcepack/$shader")),
        )
        assertTrue(result.conflicts.any { it.contains(font) })
        assertEquals(0, install(second, listOf(ce)).changed)
        assertEquals("server-custom-font", Files.readString(target))
    }

    @Test
    fun `a shader in another CE resource source is never overwritten or duplicated`() {
        val ce = provider(ResourcePackProvider.CRAFT_ENGINE)
        val other = ce.directory.resolve("resources/betterhud/resourcepack/$shader")
        write(other, "betterhud-custom-shader")
        val result = install(archive(font to "font", shader to "menu-shader"), listOf(ce))
        assertEquals("betterhud-custom-shader", Files.readString(other))
        assertFalse(Files.exists(ce.directory.resolve("resources/dialogmenu/resourcepack/$shader")))
        assertTrue(Files.exists(ce.directory.resolve("resources/dialogmenu/resourcepack/$font")))
        assertTrue(result.conflicts.any { it.contains(shader) })
    }

    @Test
    fun `if another source adds a conflicting shader remove only our unchanged managed copy`() {
        val ce = provider(ResourcePackProvider.CRAFT_ENGINE)
        val bytes = archive(font to "font", shader to "menu-shader")
        install(bytes, listOf(ce))
        write(ce.directory.resolve("resources/custom/resourcepack/$shader"), "custom-shader")
        install(bytes, listOf(ce))
        assertFalse(Files.exists(ce.directory.resolve("resources/dialogmenu/resourcepack/$shader")))
        assertEquals(0, install(bytes, listOf(ce)).changed)
    }

    @Test
    fun `provider roots match documented layouts and preserve generator pack metadata`() {
        for (type in
            listOf(
                ResourcePackProvider.ITEMS_ADDER,
                ResourcePackProvider.NEXO,
                ResourcePackProvider.ORAXEN,
            )) {
            val target = provider(type)
            val metadata = target.directory.resolve("pack/pack.mcmeta")
            if (type == ResourcePackProvider.ORAXEN) write(metadata, "provider-metadata")
            install(archive(font to type.name), listOf(target))
            val resource =
                when (type) {
                    ResourcePackProvider.ITEMS_ADDER -> "contents/dialogmenu/resourcepack/$font"
                    ResourcePackProvider.NEXO -> "pack/external_packs/dialogmenu/$font"
                    else -> "pack/$font"
                }
            assertEquals(type.name, Files.readString(target.directory.resolve(resource)))
            if (type == ResourcePackProvider.ORAXEN)
                assertEquals("provider-metadata", Files.readString(metadata))
        }
    }

    @Test
    fun `existing URL and External sending configurations do not enable resource installation`() {
        val id = UUID.randomUUID()
        val url =
            MenuResourcePack.parse(
                MenuConfigParser.yaml(
                    "Provider: URL\nURL: https://example.com/pack.zip\nUUID: $id\n",
                    "config",
                )
            )
        val external =
            MenuResourcePack.parse(
                MenuConfigParser.yaml("Provider: External\nUUID: $id\n", "config")
            )
        for (config in listOf(url, external)) {
            val result =
                install(
                    archive(font to "font"),
                    listOf(provider(ResourcePackProvider.CRAFT_ENGINE)),
                    config,
                )
            assertNull(result.provider)
            assertEquals(id, config.uuid)
            assertTrue(config.requireLoaded)
        }
        assertFalse(Files.exists(directory.resolve("CraftEngine")))
        assertEquals("https://example.com/pack.zip", url.url)
    }

    @Test
    fun `CE explicit mode does not fall back to another plugin and opt-out exports only`() {
        val ia = provider(ResourcePackProvider.ITEMS_ADDER)
        val explicit = automatic.copy(provider = "craftengine")
        assertNull(install(archive(font to "font"), listOf(ia), explicit).provider)
        assertNull(
            install(archive(font to "font"), listOf(ia), automatic.copy(autoInstall = false))
                .provider
        )
        val ce = provider(ResourcePackProvider.CRAFT_ENGINE)
        assertEquals(
            ce,
            BundledResourcePack.detect(automatic.copy(autoInstall = false), listOf(ia, ce)),
        )
    }

    @Test
    fun `archive traversal is rejected before exporting or installing files`() {
        for (name in listOf("../escape", "assets/../../escape", "assets\\escape", "C:/escape")) {
            assertThrows(IllegalArgumentException::class.java) {
                install(
                    archive(font to "font", name to "unsafe"),
                    listOf(provider(ResourcePackProvider.CRAFT_ENGINE)),
                )
            }
        }
        assertFalse(
            Files.exists(directory.resolve("DialogMenu/resourcepack/DialogMenu-resourcepack.zip"))
        )
    }

    @Test
    fun `file occupying a required directory preserves user contents and other assets install`() {
        val ce = provider(ResourcePackProvider.CRAFT_ENGINE)
        val blocked =
            ce.directory.resolve("resources/dialogmenu/resourcepack/assets/toraka_settings/font")
        write(blocked, "user-file")
        val result = install(archive(font to "font", shader to "shader"), listOf(ce))
        assertEquals("user-file", Files.readString(blocked))
        assertTrue(result.conflicts.any { it.contains(font) })
        assertTrue(Files.exists(ce.directory.resolve("resources/dialogmenu/resourcepack/$shader")))
    }

    @Test
    fun `Nexo ZIP conflicts and corrupt archives preserve shaders without blocking namespaced assets`() {
        val nexo = provider(ResourcePackProvider.NEXO)
        val otherZip = nexo.directory.resolve("pack/external_packs/other.zip")
        Files.createDirectories(otherZip.parent)
        Files.write(otherZip, archive(shader to "other-shader"))
        val result = install(archive(font to "font", shader to "menu-shader"), listOf(nexo))
        assertTrue(result.conflicts.any { it.contains(shader) })
        assertFalse(Files.exists(nexo.directory.resolve("pack/external_packs/dialogmenu/$shader")))
        write(otherZip, "invalid-zip")
        val broken = install(archive(font to "new-font", shader to "menu-shader"), listOf(nexo))
        assertEquals(
            "new-font",
            Files.readString(nexo.directory.resolve("pack/external_packs/dialogmenu/$font")),
        )
        assertTrue(broken.conflicts.any { it.contains("other.zip") })
    }

    @Test
    fun `Oraxen uploaded folders including dialogmenu are checked before importing shaders`() {
        val oraxen = provider(ResourcePackProvider.ORAXEN)
        write(oraxen.directory.resolve("pack/uploads/dialogmenu/$shader"), "uploaded-shader")
        val result = install(archive(font to "font", shader to "menu-shader"), listOf(oraxen))
        assertTrue(result.conflicts.any { it.contains(shader) })
        assertFalse(Files.exists(oraxen.directory.resolve("pack/$shader")))
    }

    @Test
    fun `CE external sources use plugins as base and shader filtering is reported`() {
        val ce = provider(ResourcePackProvider.CRAFT_ENGINE)
        write(
            ce.directory.resolve("config.yml"),
            "resource-pack:\n  merge-external-folders: [BetterHud/build]\n  exclude-core-shaders: true\n",
        )
        write(directory.resolve("BetterHud/build/$shader"), "hud-shader")
        val configured = AvailableResourceProvider.read(ce.provider, ce.directory)
        val result = install(archive(font to "font", shader to "menu-shader"), listOf(configured))
        assertTrue(result.conflicts.any { it.contains("exclude-core-shaders") })
        assertTrue(result.conflicts.any { it.contains("BetterHud") })
        assertFalse(Files.exists(ce.directory.resolve("resources/dialogmenu/resourcepack/$shader")))
    }
}
