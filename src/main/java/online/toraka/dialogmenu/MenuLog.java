package online.toraka.dialogmenu;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/** Colour this plugin's messages without changing Paper's loggers. */
public final class MenuLog {

    private static final String PREFIX = "§8[§bDialogMenu§8]§r";

    private MenuLog() {}

    public static void info(String message) {
        write("§b信息", message);
    }

    public static void loaded(String message) {
        write("§a载入", message);
    }

    public static void warning(String message) {
        write("§c警告", message);
    }

    public static void severe(String message) {
        write("§c错误", message);
    }

    public static void reply(CommandSender sender, String message) {
        if (sender instanceof ConsoleCommandSender) {
            info(Kt.removePrefix(message, "DialogMenu "));
        } else {
            sender.sendMessage(message);
        }
    }

    private static void write(String category, String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX + " " + category + " §8| §f" + message + "§r");
    }
}
