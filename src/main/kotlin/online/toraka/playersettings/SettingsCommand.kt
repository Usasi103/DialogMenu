package online.toraka.playersettings

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.platform.util.sendLang

@CommandHeader(
    name = "playersettings",
    aliases = ["settings", "player-settings"],
    permission = "playersettings.use",
    permissionDefault = PermissionDefault.TRUE,
)
object SettingsCommand {
    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            if (sender is Player) SettingsDialog.open(sender) else sender.sendLang("player-only")
        }
    }
}
