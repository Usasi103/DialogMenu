package online.toraka.playersettings

import java.nio.file.Path
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*

class ItemSourceTest {
    @TempDir lateinit var directory: Path
    private val page =
        """
        Title: 物品菜单
        Layout: [彩虹鱼]
        Icons:
          彩虹鱼:
            Display:
              Material: "source:CE:customfishing:rainbow_fish"
              Name: {zh_cn: 彩虹鱼, en_us: Rainbow fish}
              Lore: [查看鱼类模型]
              Amount: 2
            Permission: example.fish
            Actions: ["close", "command: spawn"]
        """
            .trimIndent()

    private fun parse(source: String = page) =
        SimpleMenuParser.parse("Version: 2\nPages: [items]\n") { source }

    @Test
    fun `source aliases preserve the namespaced CE id`() {
        for (alias in listOf("CE", "CRAFTENGINE", "ce", "CraftEngine")) {
            assertEquals(
                ItemReference("craftengine", "fish:rainbow/rare"),
                ItemReference.parse("source:$alias:fish:rainbow/rare", "test"),
            )
        }
        for (value in
            listOf(
                "DIAMOND",
                "minecraft:diamond",
                "source:VANILLA:DIAMOND",
                "source:MINECRAFT:minecraft:diamond",
            )) {
            assertEquals(ItemReference("minecraft", "diamond"), ItemReference.parse(value, "test"))
        }
        for (bad in
            listOf(
                "source:CE:fish",
                "source:CE:Fish:rainbow",
                "source:CE:fish:%id%",
                "source:XX:fish:id",
                "AIR",
                "not_an_item",
                "DIAMOND ",
                "custom:diamond",
            )) {
            assertThrows(Exception::class.java, { ItemReference.parse(bad, "test") }, bad)
        }
    }

    @Test
    fun `item layout retains bilingual captions and secure actions`() {
        val menu = parse()
        val page = menu.pages.getValue("items")
        assertTrue(page.itemLayout)
        assertTrue(page.widgets.isEmpty())
        val entry = page.itemEntries.single()
        assertEquals("Rainbow fish", menu.text(MenuLanguage.ENGLISH, entry.name))
        assertEquals(2, entry.display!!.amount)
        val action = menu.actions.getValue(entry.action)
        assertEquals("example.fish", action.permission)
        assertTrue(action.close)
        assertEquals("spawn", action.steps.single().command)
        assertEquals("player-command", action.steps.single().type)
    }

    @Test
    fun `invalid layout fields and unsafe configurations are rejected`() {
        for (bad in
            listOf(
                "Renderer: canvas\n$page",
                "Renderer: typo\n$page",
                page.replace("Amount: 2", "Amount: 0"),
                page.replace("Amount: 2", "Amount: '2'"),
                page.replace("Amount: 2", "Amount: 100"),
                page.replace("Amount: 2", "Fallback: source:CE:fish:fallback"),
                page.replace("Display:", "Type: toggle\n    Display:"),
                page.replace("Display:", "Name: duplicate\n    Display:"),
                page.replace("Amount: 2", "Model: typo"),
                page.replace("Display:", "Position: [10, 20]\n    Display:"),
                page.replace("command: spawn", "console: say %input%"),
                page.replace("Display:", "Type: item\n    Display:"),
            )) assertThrows(Exception::class.java, { parse(bad) }, bad)
        assertFalse(
            SimpleMenuParser.parse(MenuRepository.resource("simple/config.yml")) {
                    MenuRepository.resource("simple/menus/$it.yml")
                }
                .pages
                .values
                .any { it.itemLayout }
        )
    }

    @Test
    fun `fresh source lookup returns isolated copies and gracefully falls back`() {
        var ready = true
        var calls = 0
        val original = mock(ItemStack::class.java)
        val clone = mock(ItemStack::class.java)
        `when`(original.type).thenReturn(Material.DIAMOND)
        `when`(original.clone()).thenReturn(clone)
        val source =
            object : MenuItemSource {
                override fun problem(input: String) = if (ready) null else "missing"

                override fun build(input: String, player: Player?): ItemStack {
                    calls++
                    return original
                }
            }
        val registry =
            MenuItemSources(
                mapOf(
                    "craftengine" to source,
                    "minecraft" to
                        object : MenuItemSource {
                            override fun problem(input: String): String? = null

                            override fun build(input: String, player: Player?): ItemStack = original
                        },
                )
            )
        val display = parse().pages.getValue("items").itemEntries.single().display!!
        assertSame(clone, registry.resolve(display, null).item)
        verify(clone).amount = 2
        verify(original, never()).amount = anyInt()
        registry.resolve(display, null)
        assertEquals(2, calls)
        ready = false
        assertNull(registry.resolve(display, null).item)
        val fallback =
            registry.resolve(display.copy(fallback = ItemReference("minecraft", "barrier")), null)
        assertSame(clone, fallback.item)
        assertFalse(fallback.available)
        ready = true
        assertTrue(registry.resolve(display, null).available)
    }

    @Test
    fun `unavailable source rejects reload without installing snapshot and fallback warns`() {
        val repository = MenuRepository(directory.toFile())
        repository.initialize()
        val original = repository.current
        val unavailable = MenuItemSources(emptyMap())
        assertThrows(Exception::class.java) {
            val next = parse()
            ItemSources.validate(next, unavailable)
            repository.install(next)
        }
        assertSame(original, repository.current)
        val registry =
            MenuItemSources(
                mapOf(
                    "minecraft" to
                        object : MenuItemSource {
                            override fun problem(input: String): String? = null

                            override fun build(input: String, player: Player?): ItemStack? = null
                        }
                )
            )
        val warnings =
            ItemSources.validate(parse(page.replace("Amount: 2", "Fallback: BARRIER")), registry)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("menus/items.yml.Icons.彩虹鱼.Display.Material"))
        assertTrue(warnings.single().contains("动作停用"))
    }
}
