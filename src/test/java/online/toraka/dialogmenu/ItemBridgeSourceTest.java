package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gtemc.itembridge.api.context.BuildContext;
import cn.gtemc.itembridge.core.BukkitItemBridge;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class ItemBridgeSourceTest {
    @Test
    @DisplayName(
            "provider catalog covers every hook detected by the actual library without optional"
                    + " plugin classes")
    void providerCatalogCoversEveryHookDetectedByTheActualLibraryWithoutOptionalPluginClasses() {
        PluginManager manager = mock(PluginManager.class);
        Set<String> requested = new LinkedHashSet<>();
        when(manager.getPlugin(anyString()))
                .thenAnswer(
                        invocation -> {
                            requested.add(invocation.<String>getArgument(0));
                            return null;
                        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            List<Throwable> failures = new ArrayList<>();
            BukkitItemBridge bridge =
                    BukkitItemBridge.builder()
                            .detectSupportedPlugins(
                                    plugin -> {},
                                    (plugin, error) -> failures.add(error),
                                    plugin -> plugin.isEnabled())
                            .build();
            assertTrue(bridge.providers().isEmpty());
            assertTrue(failures.isEmpty(), failures.toString());
        }
        assertEquals(requested, new LinkedHashSet<>(ItemBridgeSources.plugins().values()));
    }

    @Test
    @DisplayName(
            "plugin.yml soft-depends on the bundled integrations and every provider plugin, sorted")
    void pluginYmlSoftDependsOnEveryProvider() throws Exception {
        // The TabooLib build generated this list: fixed plugins first, then the provider plugins
        // of itembridge-providers.properties in natural String order.
        YamlConfiguration descriptor = null;
        Enumeration<URL> resources =
                ItemBridgeSourceTest.class.getClassLoader().getResources("plugin.yml");
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            YamlConfiguration yaml;
            try (InputStream input = resource.openStream()) {
                yaml =
                        YamlConfiguration.loadConfiguration(
                                new InputStreamReader(input, StandardCharsets.UTF_8));
            }
            if ("online.toraka.dialogmenu.DialogMenu".equals(yaml.getString("main"))) {
                assertNull(descriptor, "more than one DialogMenu plugin.yml on the classpath");
                descriptor = yaml;
            }
        }
        assertNotNull(descriptor, "DialogMenu plugin.yml is not on the classpath");
        List<String> expected =
                new ArrayList<>(
                        List.of("PlaceholderAPI", "Ambience", "LootBeam", "PickupNotifier"));
        List<String> providers = new ArrayList<>(ItemBridgeSources.plugins().values());
        providers.sort(null);
        expected.addAll(providers);
        assertEquals(expected, descriptor.getStringList("softdepend"));
    }

    @Test
    @DisplayName("all bundled integrations accepted with aliases and preserved item IDs")
    void allBundledIntegrationsAcceptedWithAliasesAndPreservedItemIds() {
        Map<String, ItemReference> cases = new LinkedHashMap<>();
        cases.put("ORAXEN:MySword", new ItemReference("oraxen", "MySword"));
        cases.put("IA:weapons:blade", new ItemReference("itemsadder", "weapons:blade"));
        cases.put("ITEMSADDER:weapons:blade", new ItemReference("itemsadder", "weapons:blade"));
        cases.put("SX-Item:测试武器", new ItemReference("sxitem", "测试武器"));
        cases.put("SXITEM:MySword", new ItemReference("sxitem", "MySword"));
        cases.put("SI:MySword", new ItemReference("sxitem", "MySword"));
        cases.put("NeigeItems:MySword", new ItemReference("neigeitems", "MySword"));
        cases.put("NI:group:MySword", new ItemReference("neigeitems", "group:MySword"));
        cases.put("CE:fish:rainbow_fish", new ItemReference("craftengine", "fish:rainbow_fish"));
        cases.put("NEXO:MySword", new ItemReference("nexo", "MySword"));
        cases.put("MM:MySword", new ItemReference("mythicmobs", "MySword"));
        cases.put("MI:SWORD:MySword", new ItemReference("mmoitems", "SWORD:MySword"));
        cases.put("HDB:123", new ItemReference("headdatabase", "123"));
        for (Map.Entry<String, ItemReference> entry : cases.entrySet()) {
            assertEquals(entry.getValue(), ItemReference.parse("source:" + entry.getKey(), "test"));
        }
        assertEquals(39, ItemBridgeSources.plugins().size());
        for (Map.Entry<String, String> plugin : ItemBridgeSources.plugins().entrySet()) {
            String id = plugin.getKey();
            String name = plugin.getValue();
            String input =
                    Kt.setOf("craftengine", "itemsadder").contains(id) ? "test:sword" : "TestSword";
            assertEquals(
                    new ItemReference(id, input),
                    ItemReference.parse("source:" + name + ":" + input, "test"));
            assertEquals(
                    new ItemReference(id, input),
                    ItemReference.parse("source:" + id + ":" + input, "test"));
        }
        for (String input :
                List.of(
                        "unknown:blade",
                        "IA:missing_namespace",
                        "IA:Mixed:case",
                        "NI:%dynamic%",
                        "SXITEM: name")) {
            assertThrows(
                    Exception.class, () -> ItemReference.parse("source:" + input, "test"), input);
        }
    }

    @Test
    @DisplayName("all providers validate without generation and forward the viewing player")
    void allProvidersValidateWithoutGenerationAndForwardTheViewingPlayer() {
        for (Map.Entry<String, String> plugin : ItemBridgeSources.plugins().entrySet()) {
            String id = plugin.getKey();
            String name = plugin.getValue();
            BukkitItemBridge bridge = mock(BukkitItemBridge.class);
            Player player = mock(Player.class);
            ItemStack item = mock(ItemStack.class);
            when(bridge.hasProvider(id)).thenReturn(true);
            when(bridge.has(id, "test:item")).thenReturn(true);
            when(bridge.buildOrNull(eq(id), eq("test:item"), same(player), any(BuildContext.class)))
                    .thenReturn(item);
            ItemBridgeSource source = new ItemBridgeSource(id, name, () -> bridge, () -> true);
            assertNull(source.problem("test:item"));
            verify(bridge, never())
                    .buildOrNull(eq(id), eq("test:item"), same(player), any(BuildContext.class));
            assertSame(item, source.build("test:item", player));
            ArgumentCaptor<BuildContext> context = ArgumentCaptor.forClass(BuildContext.class);
            verify(bridge).buildOrNull(eq(id), eq("test:item"), same(player), context.capture());
            assertTrue(context.getValue().contextData().isEmpty());
            assertTrue(source.problem("missing").contains("不存在物品"));
        }
    }

    @Test
    @DisplayName("absent plugins never load bridge hooks and incompatible APIs degrade safely")
    void absentPluginsNeverLoadBridgeHooksAndIncompatibleApisDegradeSafely() {
        ItemBridgeSource source =
                new ItemBridgeSource(
                        "oraxen",
                        "Oraxen",
                        () -> {
                            throw new IllegalStateException("must not initialize");
                        },
                        () -> false);
        assertTrue(source.problem("sword").contains("未安装"));
        assertNull(source.build("sword", null));
        Map<String, MenuItemSource> failing = new LinkedHashMap<>();
        failing.put(
                "oraxen",
                new MenuItemSource() {
                    @Override
                    public String problem(String input) {
                        throw new NoSuchMethodError("incompatible provider");
                    }

                    @Override
                    public ItemStack build(String input, Player player) {
                        throw new IllegalStateException("must not generate");
                    }
                });
        MenuItemSources failed = new MenuItemSources(failing);
        ItemDisplay display =
                new ItemDisplay(new ItemReference("oraxen", "sword"), null, 1, "test");
        assertTrue(failed.problem(display.material()).contains("API 不兼容"));
        assertFalse(failed.resolve(display, null).available());
        BukkitItemBridge bridge = mock(BukkitItemBridge.class);
        assertTrue(
                new ItemBridgeSource("oraxen", "Oraxen", () -> bridge, () -> true)
                        .problem("sword")
                        .contains("未能接入"));
    }
}
