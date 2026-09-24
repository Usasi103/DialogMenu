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
    val help = subCommand {
        execute<CommandSender> { sender, _, _ -> showHelp(sender) }
    }

    @CommandBody
    val pack = subCommand {
        execute<Player> { player, _, _ -> MenuResources.send(player) }
    }

    @CommandBody
    val scale = subCommand {
        execute<Player> { player, _, _ ->
            player.sendLang(
                "menu-scale-current",
                MenuPreferences.readScale(player.persistentDataContainer).id,
            )
        }
        dynamic("percent") {
            suggestion<CommandSender> { _, _ -> MenuScale.entries.map { it.id } }
            execute<Player> { player, _, argument ->
                val selected = MenuScale.entries.firstOrNull { it.id == argument }
                if (selected == null) player.sendLang("menu-scale-invalid")
                else {
                    MenuPreferences.saveScale(player.persistentDataContainer, selected)
                    player.sendLang("menu-scale-saved", selected.id)
                    MenuRuntime.open(player)
                }
            }
        }
    }

    @CommandBody
    val template = subCommand {
        dynamic("template") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                MenuRuntime.templates.keys.toList()
            }
            execute<Player> { player, _, argument -> TemplateDialog.open(player, argument) }
        }
    }

    @CommandBody
    val open = subCommand {
        dynamic("menu") {
            suggestion<CommandSender>(uncheck = true) { _, _ -> MenuRuntime.menuIds }
            execute<Player> { player, _, argument -> MenuRuntime.open(player, argument) }
            dynamic("page", optional = true) {
                suggestion<CommandSender> { _, context -> MenuRuntime.pages(context["menu"]) }
                execute<Player> { player, context, argument ->
                    MenuRuntime.open(player, context["menu"], argument)
                }
            }
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
            if (sender is Player) MenuRuntime.open(sender) else showHelp(sender)
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendLang("command-help-title")
        sender.sendLang("command-help-player")
        if (sender.hasPermission("playersettings.admin")) sender.sendLang("command-help-admin")
        sender.sendLang("command-help-aliases")
    }
}
