package online.toraka.dialogmenu

import org.bukkit.Bukkit
import org.bukkit.entity.Player

/** PlaceholderAPI stays optional; a value it cannot supply is absent, never guessed. */
object MenuPlaceholders {
    val enabled: Boolean
        get() = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")

    fun resolve(player: Player, token: String): String? {
        if (!enabled) return null
        val value = runCatching { PapiAdapter.resolve(player, token) }.getOrNull() ?: return null
        return value
            .takeUnless { it.isBlank() || it == token }
            ?.replace(Regex("[&§][0-9a-fk-or]", RegexOption.IGNORE_CASE), "")
    }

    /** Declared canvas values are trimmed so conditions compare what players see. */
    fun values(player: Player, declared: Map<String, String>): Map<String, String> =
        declared
            .mapNotNull { (name, token) ->
                resolve(player, token)?.trim()?.takeIf { it.isNotEmpty() }?.let { name to it }
            }
            .toMap()

    fun unavailable(player: Player): String =
        MenuText.get(MenuPreferences.read(player.persistentDataContainer).language, "unavailable")
}
