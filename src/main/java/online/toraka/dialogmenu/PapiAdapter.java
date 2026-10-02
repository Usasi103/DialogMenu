package online.toraka.dialogmenu;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Loaded only after Bukkit confirms that the optional PlaceholderAPI plugin is enabled. */
final class PapiAdapter {

    private PapiAdapter() {}

    static String resolve(Player player, String value) {
        return PlaceholderAPI.setPlaceholders(player, value);
    }
}
