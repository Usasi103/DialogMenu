package online.toraka.dialogmenu

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.entity.Player

/** Loaded only after Bukkit confirms that the optional PlaceholderAPI plugin is enabled. */
object PapiAdapter {
    fun resolve(player: Player, value: String): String =
        PlaceholderAPI.setPlaceholders(player, value)
}
