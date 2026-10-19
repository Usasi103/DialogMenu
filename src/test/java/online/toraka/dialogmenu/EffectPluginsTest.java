package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class EffectPluginsTest {

    private static final String LOOT_PREFS = "online.toraka.lootbeam.data.PrefsCache";
    private static final String PICKUP_ENGINE = "online.toraka.pickupnotifier.NoticeEngine";
    private static final Set<String> MODULES = Set.of(LOOT_PREFS, PICKUP_ENGINE);

    @Test
    void itemLoomWinsWhenBothLegacyProvidersAreInstalled() {
        PluginManager manager = mock(PluginManager.class);
        Plugin itemLoom = plugin(true, LOOT_PREFS);
        when(manager.getPlugin("ItemLoom")).thenReturn(itemLoom);
        when(manager.getPlugin("LootBeam")).thenReturn(plugin(true, LOOT_PREFS));
        when(manager.getPlugin("Ambience")).thenReturn(plugin(true, LOOT_PREFS));

        assertSame(itemLoom, provider(manager, "LootBeam"));
        verify(manager, never()).getPlugin("LootBeam");
        verify(manager, never()).getPlugin("Ambience");
    }

    @Test
    void itemLoomWithoutTheModuleFallsBackToStandaloneLootBeam() {
        PluginManager manager = mock(PluginManager.class);
        Plugin standalone = plugin(true, LOOT_PREFS);
        when(manager.getPlugin("ItemLoom")).thenReturn(plugin(true));
        when(manager.getPlugin("LootBeam")).thenReturn(standalone);

        assertSame(standalone, provider(manager, "LootBeam"));
        verify(manager, never()).getPlugin("Ambience");
    }

    @Test
    void disabledItemLoomFallsBackToAmbience() {
        PluginManager manager = mock(PluginManager.class);
        Plugin ambience = plugin(true, LOOT_PREFS);
        when(manager.getPlugin("ItemLoom")).thenReturn(plugin(false, LOOT_PREFS));
        when(manager.getPlugin("Ambience")).thenReturn(ambience);

        assertSame(ambience, provider(manager, "LootBeam"));
    }

    @Test
    void disabledStandaloneAndOldItemLoomStillAllowAmbience() {
        PluginManager manager = mock(PluginManager.class);
        Plugin ambience = plugin(true, LOOT_PREFS);
        when(manager.getPlugin("ItemLoom")).thenReturn(plugin(true));
        when(manager.getPlugin("LootBeam")).thenReturn(plugin(false, LOOT_PREFS));
        when(manager.getPlugin("Ambience")).thenReturn(ambience);

        assertSame(ambience, provider(manager, "LootBeam"));
    }

    @Test
    void unavailableOrMissingModulesDoNotExposeLootControls() {
        PluginManager manager = mock(PluginManager.class);
        assertNull(provider(manager, "LootBeam"));

        when(manager.getPlugin("ItemLoom")).thenReturn(plugin(true));
        when(manager.getPlugin("Ambience")).thenReturn(plugin(true));
        assertNull(provider(manager, "LootBeam"));

        when(manager.getPlugin("Ambience")).thenReturn(plugin(false, LOOT_PREFS));
        assertNull(provider(manager, "LootBeam"));
    }

    @Test
    void standaloneLootBeamStillWorksWithoutItemLoom() {
        PluginManager manager = mock(PluginManager.class);
        Plugin standalone = plugin(true, LOOT_PREFS);
        when(manager.getPlugin("LootBeam")).thenReturn(standalone);

        assertSame(standalone, provider(manager, "LootBeam"));
    }

    @Test
    void pickupNotifierKeepsItsStandalonePriority() {
        PluginManager manager = mock(PluginManager.class);
        Plugin standalone = plugin(true, PICKUP_ENGINE);
        when(manager.getPlugin("PickupNotifier")).thenReturn(standalone);
        when(manager.getPlugin("Ambience")).thenReturn(plugin(true, PICKUP_ENGINE));

        assertSame(standalone, provider(manager, "PickupNotifier"));
        verify(manager, never()).getPlugin("ItemLoom");
        verify(manager, never()).getPlugin("Ambience");
    }

    @Test
    void pickupNotifierKeepsItsAmbienceFallback() {
        PluginManager manager = mock(PluginManager.class);
        Plugin ambience = plugin(true, PICKUP_ENGINE);
        when(manager.getPlugin("Ambience")).thenReturn(ambience);

        assertSame(ambience, provider(manager, "PickupNotifier"));
        verify(manager, never()).getPlugin("ItemLoom");
    }

    @Test
    void unrelatedPluginsKeepTheirDirectLookup() {
        PluginManager manager = mock(PluginManager.class);
        Plugin direct = plugin(true);
        when(manager.getPlugin("CraftEngine")).thenReturn(direct);

        assertSame(direct, provider(manager, "CraftEngine"));
        assertNull(provider(manager, "MissingPlugin"));
        verify(manager, never()).getPlugin("ItemLoom");
        verify(manager, never()).getPlugin("Ambience");
    }

    private static Plugin provider(PluginManager manager, String name) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            return EffectPlugins.provider(name);
        }
    }

    private static Plugin plugin(boolean enabled, String... modules) {
        Set<String> visibleModules = Set.of(modules);
        ClassLoader loader =
                new ClassLoader(EffectPluginsTest.class.getClassLoader()) {
                    @Override
                    protected Class<?> loadClass(String name, boolean resolve)
                            throws ClassNotFoundException {
                        if (MODULES.contains(name) && !visibleModules.contains(name)) {
                            throw new ClassNotFoundException(name);
                        }
                        return super.loadClass(name, resolve);
                    }
                };
        return (Plugin)
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {Plugin.class},
                        (proxy, method, arguments) ->
                                switch (method.getName()) {
                                    case "isEnabled" -> enabled;
                                    case "equals" -> proxy == arguments[0];
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "toString" -> "TestPlugin";
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
