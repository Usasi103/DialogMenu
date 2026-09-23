package online.toraka.playersettings

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

/** Old menu files can still name the standalone effect providers. */
object EffectPlugins {
    fun provider(name: String): Plugin? {
        val manager = Bukkit.getPluginManager()
        manager
            .getPlugin(name)
            ?.takeIf { it.isEnabled }
            ?.let {
                return it
            }
        val module =
            when (name) {
                "LootBeam" -> "online.toraka.lootbeam.data.PrefsCache"
                "PickupNotifier" -> "online.toraka.pickupnotifier.NoticeEngine"
                else -> return null
            }
        val ambience = manager.getPlugin("Ambience")?.takeIf { it.isEnabled } ?: return null
        return runCatching {
            Class.forName(module, false, ambience.javaClass.classLoader)
            ambience
        }
            .getOrNull()
    }
}
