package online.toraka.dialogmenu;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** PlaceholderAPI stays optional; a value it cannot supply is absent, never guessed. */
public final class MenuPlaceholders {

    private static final Pattern COLOR_CODE =
            Pattern.compile("[&§][0-9a-fk-or]", Pattern.CASE_INSENSITIVE);

    private MenuPlaceholders() {}

    public static boolean enabled() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static String resolve(Player player, String token) {
        if (!enabled()) {
            return null;
        }
        String value;
        try {
            value = PapiAdapter.resolve(player, token);
        } catch (Throwable error) {
            value = null;
        }
        if (value == null) {
            return null;
        }
        if (Kt.isBlank(value) || value.equals(token)) {
            return null;
        }
        return COLOR_CODE.matcher(value).replaceAll("");
    }

    /** Declared canvas values are trimmed so conditions compare what players see. */
    public static Map<String, String> values(Player player, Map<String, String> declared) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String value = resolve(player, entry.getValue());
            if (value == null) {
                continue;
            }
            String trimmed = Kt.trim(value);
            if (!trimmed.isEmpty()) {
                result.put(entry.getKey(), trimmed);
            }
        }
        return result;
    }

    public static String unavailable(Player player) {
        return MenuText.get(
                MenuPreferences.read(player.getPersistentDataContainer()).language(),
                "unavailable");
    }
}
