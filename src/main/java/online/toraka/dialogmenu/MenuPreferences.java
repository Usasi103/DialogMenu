package online.toraka.dialogmenu;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Stored in the player's PDC under the {@code playersettings} namespace (unchanged since 0.1). */
public record MenuPreferences(MenuLanguage language, MenuTheme theme) {

    private static final NamespacedKey LANGUAGE_KEY =
            new NamespacedKey("playersettings", "menu_language");
    private static final NamespacedKey THEME_KEY =
            new NamespacedKey("playersettings", "menu_theme");

    public MenuPreferences() {
        this(MenuLanguage.CHINESE, MenuTheme.DARK);
    }

    public void save(PersistentDataContainer data) {
        data.set(LANGUAGE_KEY, PersistentDataType.STRING, language.id());
        data.set(THEME_KEY, PersistentDataType.STRING, theme.id());
    }

    public MenuPreferences withLanguage(MenuLanguage value) {
        return new MenuPreferences(value, theme);
    }

    public MenuPreferences withTheme(MenuTheme value) {
        return new MenuPreferences(language, value);
    }

    /** Defaults come from the active settings menu. */
    public static MenuPreferences read(PersistentDataContainer data) {
        return read(data, MenuRuntime.current());
    }

    public static MenuPreferences read(PersistentDataContainer data, MenuDefinition defaults) {
        String language = data.get(LANGUAGE_KEY, PersistentDataType.STRING);
        String theme = data.get(THEME_KEY, PersistentDataType.STRING);
        MenuLanguage chosenLanguage = defaults.language();
        for (MenuLanguage value : MenuLanguage.values()) {
            if (value.id().equals(language)) {
                chosenLanguage = value;
                break;
            }
        }
        MenuTheme chosenTheme = defaults.theme();
        for (MenuTheme value : MenuTheme.values()) {
            if (value.id().equals(theme)) {
                chosenTheme = value;
                break;
            }
        }
        return new MenuPreferences(chosenLanguage, chosenTheme);
    }
}
