package online.toraka.dialogmenu;

import static dev.keystone.command.CommandBuilder.argument;
import static dev.keystone.command.CommandBuilder.command;
import static dev.keystone.command.CommandBuilder.literal;

import dev.keystone.lang.Lang;
import java.util.ArrayList;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

/**
 * {@code /dialogmenu} and its aliases. Subcommands keep the names, order and permissions of the
 * TabooLib build: everything needs {@code playersettings.use} (default true); reload and check
 * test {@code playersettings.admin} themselves.
 */
final class MenuCommand {

    private MenuCommand() {}

    static void register() {
        command("dialogmenu")
                .aliases("dmenu", "playersettings", "settings", "player-settings")
                .permission("playersettings.use", PermissionDefault.TRUE)
                .executes(
                        (sender, context, argument) -> {
                            if (sender instanceof Player player) {
                                MenuRuntime.open(player);
                            } else {
                                showHelp(sender);
                            }
                        })
                .then(literal("help").executes((sender, context, argument) -> showHelp(sender)))
                .then(
                        literal("pack")
                                .executes(
                                        Player.class,
                                        (player, context, argument) -> MenuResources.send(player)))
                .then(
                        literal("template")
                                .then(
                                        argument("template")
                                                .suggestUnchecked(
                                                        (sender, context) ->
                                                                new ArrayList<>(
                                                                        MenuRuntime.templates()
                                                                                .keySet()))
                                                .executes(
                                                        Player.class,
                                                        (player, context, argument) ->
                                                                TemplateDialog.open(
                                                                        player, argument))))
                .then(
                        literal("open")
                                .then(
                                        argument("menu")
                                                .suggestUnchecked(
                                                        (sender, context) -> MenuRuntime.menuIds())
                                                .executes(
                                                        Player.class,
                                                        (player, context, argument) ->
                                                                MenuRuntime.open(player, argument))
                                                .then(
                                                        argument("page")
                                                                .optional()
                                                                .suggest(
                                                                        CommandSender.class,
                                                                        (sender, context) ->
                                                                                MenuRuntime.pages(
                                                                                        context.get(
                                                                                                "menu")))
                                                                .executes(
                                                                        Player.class,
                                                                        (player,
                                                                                context,
                                                                                argument) ->
                                                                                MenuRuntime.open(
                                                                                        player,
                                                                                        context.get(
                                                                                                "menu"),
                                                                                        argument)))))
                .then(
                        literal("reload")
                                .executes(
                                        (sender, context, argument) ->
                                                MenuRuntime.reload(sender, false)))
                .then(
                        literal("check")
                                .executes(
                                        (sender, context, argument) ->
                                                MenuRuntime.reload(sender, true)))
                .register();
    }

    /** The lang help, list lines one by one; the admin lines only for admins. */
    private static void showHelp(CommandSender sender) {
        Lang.send(sender, "command-help-title");
        Lang.send(sender, "command-help-player");
        if (sender.hasPermission("playersettings.admin")) {
            Lang.send(sender, "command-help-admin");
        }
        Lang.send(sender, "command-help-aliases");
    }
}
