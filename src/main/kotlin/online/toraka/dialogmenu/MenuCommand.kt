package online.toraka.dialogmenu

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.platform.util.sendLang

@CommandHeader(
    name = "dialogmenu",
    aliases = ["dmenu", "playersettings", "settings", "player-settings"],
    permission = "playersettings.use",
    permissionDefault = PermissionDefault.TRUE,
)
object MenuCommand {
    @CommandBody
    val open = subCommand {
        dynamic("page") {
            suggestion<CommandSender> { _, _ -> MenuRuntime.current.pages.keys.toList() }
            execute<Player> { player, _, argument -> MenuDialog.open(player, argument) }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ -> MenuRuntime.reload(sender, false) }
    }

    @CommandBody
    val check = subCommand {
        execute<CommandSender> { sender, _, _ -> MenuRuntime.reload(sender, true) }
    }

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            if (sender is Player) MenuDialog.open(sender) else sender.sendLang("player-only")
        }
    }
}
