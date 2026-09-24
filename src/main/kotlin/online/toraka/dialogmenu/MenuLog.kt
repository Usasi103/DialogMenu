package online.toraka.dialogmenu

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender

/** Colour this plugin's messages without changing Paper or TabooLib loggers. */
object MenuLog {
    private const val prefix = "§8[§bDialogMenu§8]§r"

    fun info(message: String) = write("§b信息", message)

    fun loaded(message: String) = write("§a载入", message)

    fun warning(message: String) = write("§c警告", message)

    fun severe(message: String) = write("§c错误", message)

    fun reply(sender: CommandSender, message: String) {
        if (sender is ConsoleCommandSender) info(message.removePrefix("DialogMenu "))
        else sender.sendMessage(message)
    }

    private fun write(category: String, message: String) {
        Bukkit.getConsoleSender().sendMessage("$prefix $category §8| §f$message§r")
    }
}
