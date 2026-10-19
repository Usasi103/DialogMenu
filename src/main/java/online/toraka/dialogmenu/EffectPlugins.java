package online.toraka.dialogmenu;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Resolves current modules while preserving old standalone names in menu files. */
final class EffectPlugins {

    private static final String LOOT_BEAM_PREFS = "online.toraka.lootbeam.data.PrefsCache";

    private EffectPlugins() {}

    static Plugin provider(String name) {
        PluginManager manager = Bukkit.getPluginManager();
        if ("LootBeam".equals(name)) {
            Plugin itemLoom = moduleProvider(manager, "ItemLoom", LOOT_BEAM_PREFS);
            if (itemLoom != null) {
                return itemLoom;
            }
        }
        Plugin direct = manager.getPlugin(name);
        if (direct != null && direct.isEnabled()) {
            return direct;
        }
        String module;
        switch (name) {
            case "LootBeam" -> module = LOOT_BEAM_PREFS;
            case "PickupNotifier" -> module = "online.toraka.pickupnotifier.NoticeEngine";
            default -> {
                return null;
            }
        }
        return moduleProvider(manager, "Ambience", module);
    }

    private static Plugin moduleProvider(PluginManager manager, String name, String module) {
        Plugin plugin = manager.getPlugin(name);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            Class.forName(module, false, plugin.getClass().getClassLoader());
            return plugin;
        } catch (Throwable error) {
            return null;
        }
    }
}
