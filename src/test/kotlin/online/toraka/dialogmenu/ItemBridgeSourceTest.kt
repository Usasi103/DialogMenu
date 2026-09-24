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
    fun `provider catalog covers every hook detected by the actual library without optional plugin classes`() {
        val manager = mock(org.bukkit.plugin.PluginManager::class.java)
        val requested = linkedSetOf<String>()
        `when`(manager.getPlugin(anyString())).thenAnswer { invocation ->
            requested += invocation.getArgument<String>(0)
            null
        }
        mockStatic(org.bukkit.Bukkit::class.java).use { bukkit ->
            bukkit
                .`when`<org.bukkit.plugin.PluginManager> { org.bukkit.Bukkit.getPluginManager() }
                .thenReturn(manager)
            val failures = mutableListOf<Throwable>()
            val bridge =
                BukkitItemBridge.builder()
                    .detectSupportedPlugins({}, { _, error -> failures += error }, { it.isEnabled })
                    .build()
            assertTrue(bridge.providers().isEmpty())
            assertTrue(failures.isEmpty(), failures.toString())
        }
        assertEquals(requested, ItemBridgeSources.plugins.values.toSet())
    }

    @Test
    fun `all bundled integrations accepted with aliases and preserved item IDs`() {
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
                "NEXO:MySword" to ItemReference("nexo", "MySword"),
                "MM:MySword" to ItemReference("mythicmobs", "MySword"),
                "MI:SWORD:MySword" to ItemReference("mmoitems", "SWORD:MySword"),
                "HDB:123" to ItemReference("headdatabase", "123"),
            )
        cases.forEach { (input, expected) ->
            assertEquals(expected, ItemReference.parse("source:$input", "test"))
        }
        assertEquals(39, ItemBridgeSources.plugins.size)
        ItemBridgeSources.plugins.forEach { (id, name) ->
            val input = if (id in setOf("craftengine", "itemsadder")) "test:sword" else "TestSword"
            assertEquals(
                ItemReference(id, input),
                ItemReference.parse("source:$name:$input", "test"),
            )
            assertEquals(ItemReference(id, input), ItemReference.parse("source:$id:$input", "test"))
        }
        for (input in
            listOf(
                "unknown:blade",
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
