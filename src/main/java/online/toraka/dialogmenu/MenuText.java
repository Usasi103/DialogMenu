package online.toraka.dialogmenu;

import java.util.Set;
import java.util.regex.Pattern;

/** External YAML translations are selected per player. */
public final class MenuText {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");

    private MenuText() {}

    public static Set<String> keys(MenuLanguage language) {
        return Kt.getValue(MenuRuntime.current().translations(), language).keySet();
    }

    public static String get(MenuLanguage language, String key, Object... values) {
        String template =
                Kt.getValue(Kt.getValue(MenuRuntime.current().translations(), language), key);
        // Replace placeholders once so player-provided values cannot expand more placeholders.
        return Kt.replace(
                PLACEHOLDER,
                template,
                match -> {
                    int index = Integer.parseInt(match.group(1));
                    Object value = index >= 0 && index < values.length ? values[index] : null;
                    return value != null ? value.toString() : match.group();
                });
    }
}
