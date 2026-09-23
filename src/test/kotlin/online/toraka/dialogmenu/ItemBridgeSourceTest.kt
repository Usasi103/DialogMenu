package online.toraka.dialogmenu

import cn.gtemc.itembridge.api.context.BuildContext
import cn.gtemc.itembridge.core.BukkitItemBridge
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class ItemBridgeSourceTest {
    @Test
    fun `only five integrations accepted with aliases and preserved item IDs`() {
        val cases =
            mapOf(
                "ORAXEN:MySword" to ItemReference("oraxen", "MySword"),
                "IA:weapons:blade" to ItemReference("itemsadder", "weapons:blade"),
                "ITEMSADDER:weapons:blade" to ItemReference("itemsadder", "weapons:blade"),
                "SX-Item:测试武器" to ItemReference("sxitem", "测试武器"),
                "SXITEM:MySword" to ItemReference("sxitem", "MySword"),
                "SI:MySword" to ItemReference("sxitem", "MySword"),
                "NeigeItems:MySword" to ItemReference("neigeitems", "MySword"),
                "NI:group:MySword" to ItemReference("neigeitems", "group:MySword"),
                "CE:fish:rainbow_fish" to ItemReference("craftengine", "fish:rainbow_fish"),
            )
        cases.forEach { (input, expected) ->
            assertEquals(expected, ItemReference.parse("source:$input", "test"))
        }
        assertEquals(
            setOf("Oraxen", "ItemsAdder", "SX-Item", "NeigeItems", "CraftEngine"),
            ItemBridgeSources.plugins.values.toSet(),
        )
        for (input in
            listOf(
                "NEXO:blade",
                "MM:blade",
                "HDB:blade",
                "IA:missing_namespace",
                "IA:Mixed:case",
                "NI:%dynamic%",
                "SXITEM: name",
            )) {
            assertThrows(
                Exception::class.java,
                { ItemReference.parse("source:$input", "test") },
                input,
            )
        }
    }

    @Test
    fun `all providers validate without generation and forward the viewing player`() {
        ItemBridgeSources.plugins.forEach { (id, name) ->
            val bridge = mock(BukkitItemBridge::class.java)
            val player = mock(Player::class.java)
            val item = mock(ItemStack::class.java)
            `when`(bridge.hasProvider(id)).thenReturn(true)
            `when`(bridge.has(id, "test:item")).thenReturn(true)
            `when`(
                    bridge.buildOrNull(
                        eq(id),
                        eq("test:item"),
                        same(player),
                        any(BuildContext::class.java),
                    )
                )
                .thenReturn(item)
            val source = ItemBridgeSource(id, name, { bridge }, { true })
            assertNull(source.problem("test:item"))
            verify(bridge, never())
                .buildOrNull(eq(id), eq("test:item"), same(player), any(BuildContext::class.java))
            assertSame(item, source.build("test:item", player))
            val context = org.mockito.ArgumentCaptor.forClass(BuildContext::class.java)
            verify(bridge).buildOrNull(eq(id), eq("test:item"), same(player), context.capture())
            assertTrue(context.value.contextData().isEmpty())
            assertTrue(source.problem("missing")!!.contains("不存在物品"))
        }
    }

    @Test
    fun `absent plugins never load bridge hooks and incompatible APIs degrade safely`() {
        val source =
            ItemBridgeSource("oraxen", "Oraxen", { error("must not initialize") }, { false })
        assertTrue(source.problem("sword")!!.contains("未安装"))
        assertNull(source.build("sword", null))
        val failed =
            MenuItemSources(
                mapOf(
                    "oraxen" to
                        object : MenuItemSource {
                            override fun problem(input: String): String? =
                                throw NoSuchMethodError("incompatible provider")

                            override fun build(input: String, player: Player?): ItemStack? =
                                error("must not generate")
                        }
                )
            )
        val display = ItemDisplay(ItemReference("oraxen", "sword"), null, 1, "test")
        assertTrue(failed.problem(display.material)!!.contains("API 不兼容"))
        assertFalse(failed.resolve(display, null).available)
        val bridge = mock(BukkitItemBridge::class.java)
        assertTrue(
            ItemBridgeSource("oraxen", "Oraxen", { bridge }, { true })
                .problem("sword")!!
                .contains("未能接入")
        )
    }
}
