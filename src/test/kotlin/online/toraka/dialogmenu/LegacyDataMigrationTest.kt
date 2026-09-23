package online.toraka.dialogmenu

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LegacyDataMigrationTest {
    @TempDir lateinit var root: Path

    private fun write(relative: String, text: String): Path {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        return Files.writeString(path, text)
    }

    @Test
    fun importsOldFilesAndPreservesEditsAcrossRestarts() {
        val config = write("PlayerSettings/config.yml", "# 自定义菜单\nPages: [custom]\n")
        val menu = write("PlayerSettings/menus/custom.yml", "Title: 我的菜单\n")
        write("PlayerSettings/update-check.yml", "enabled: false\n")
        assertTrue(LegacyDataMigration.migrate(root.resolve("DialogMenu")))
        assertEquals(-1L, Files.mismatch(config, root.resolve("DialogMenu/config.yml")))
        assertEquals(-1L, Files.mismatch(menu, root.resolve("DialogMenu/menus/custom.yml")))
        write("DialogMenu/config.yml", "Pages: [changed]\n")
        assertFalse(LegacyDataMigration.migrate(root.resolve("DialogMenu")))
        assertEquals("Pages: [changed]\n", Files.readString(root.resolve("DialogMenu/config.yml")))
        assertTrue(Files.exists(config))
    }

    @Test
    fun existingNewConfigurationTakesPrecedence() {
        write("PlayerSettings/menu.yml", "legacy")
        write("DialogMenu/menu.yml", "new")
        assertFalse(LegacyDataMigration.migrate(root.resolve("DialogMenu")))
        assertEquals("new", Files.readString(root.resolve("DialogMenu/menu.yml")))
    }

    @Test
    fun refusesConflictsBeforeCopyingConfiguration() {
        write("PlayerSettings/config.yml", "legacy")
        write("PlayerSettings/menus/custom.yml", "old menu")
        val edited = write("DialogMenu/menus/custom.yml", "edited menu")
        assertThrows(IllegalStateException::class.java) {
            LegacyDataMigration.migrate(root.resolve("DialogMenu"))
        }
        assertEquals("edited menu", Files.readString(edited))
        assertFalse(Files.exists(root.resolve("DialogMenu/config.yml")))
        assertFalse(Files.exists(root.resolve("DialogMenu/.playersettings-import")))
    }

    @Test
    fun importsLegacyLanguageOverFreshlyGeneratedBundledLanguage() {
        write("PlayerSettings/menu.yml", "v1 menu")
        write("PlayerSettings/lang/zh_CN.yml", "管理员自定义提示")
        write("DialogMenu/lang/zh_CN.yml", "bundled")
        assertTrue(
            LegacyDataMigration.migrate(
                root.resolve("DialogMenu"),
                mapOf("lang/zh_CN.yml" to "bundled".toByteArray()),
            )
        )
        assertEquals("管理员自定义提示", Files.readString(root.resolve("DialogMenu/lang/zh_CN.yml")))
    }

    @Test
    fun retriesInterruptedImportAfterMainConfigurationWasCopied() {
        write("PlayerSettings/config.yml", "legacy")
        write("PlayerSettings/menus/custom.yml", "menu")
        write("DialogMenu/config.yml", "legacy")
        write("DialogMenu/.playersettings-import", "in progress")
        assertTrue(LegacyDataMigration.migrate(root.resolve("DialogMenu")))
        assertEquals("menu", Files.readString(root.resolve("DialogMenu/menus/custom.yml")))
        assertFalse(Files.exists(root.resolve("DialogMenu/.playersettings-import")))
    }
}
