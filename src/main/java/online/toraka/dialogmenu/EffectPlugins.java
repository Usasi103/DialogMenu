package online.toraka.dialogmenu;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Old menu files can still name the standalone effect providers. */
final class EffectPlugins {

    private EffectPlugins() {}

    static Plugin provider(String name) {
        PluginManager manager = Bukkit.getPluginManager();
        Plugin direct = manager.getPlugin(name);
        if (direct != null && direct.isEnabled()) {
            return direct;
        }
        String module;
        switch (name) {
            case "LootBeam" -> module = "online.toraka.lootbeam.data.PrefsCache";
            case "PickupNotifier" -> module = "online.toraka.pickupnotifier.NoticeEngine";
            default -> {
                return null;
            }
        }
        Plugin ambience = manager.getPlugin("Ambience");
        if (ambience == null || !ambience.isEnabled()) {
            return null;
        }
        try {
            Class.forName(module, false, ambience.getClass().getClassLoader());
            return ambience;
        } catch (Throwable error) {
            return null;
        }
    }
}
