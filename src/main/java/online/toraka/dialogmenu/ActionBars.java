package online.toraka.dialogmenu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Where the {@code actionbar} action sends its text. */
final class ActionBars {

    private ActionBars() {}

    static void send(Player player, Component text) {
        player.sendActionBar(text);
    }
}
