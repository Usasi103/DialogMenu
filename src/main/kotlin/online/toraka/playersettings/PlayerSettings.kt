package online.toraka.playersettings

import org.bukkit.Bukkit
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import taboolib.common.platform.function.getDataFolder

object PlayerSettings : taboolib.common.platform.Plugin() {
    override fun onEnable() {
        if (Bukkit.getPluginManager().getPermission("playersettings.admin") == null) {
            Bukkit.getPluginManager()
                .addPermission(
                    Permission(
                        "playersettings.admin",
                        "Reload and validate the player menu",
                        PermissionDefault.OP,
                    )
                )
        }
        MenuRuntime.initialize(getDataFolder())
    }

    override fun onDisable() {
        SettingsDialog.shutdown()
    }
}
