package online.toraka.playersettings

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

enum class MenuLanguage(val id: String, val label: String) {
    CHINESE("zh_cn", "简体中文"),
    ENGLISH("en_us", "English");

    companion object {
        fun parse(value: String?): MenuLanguage = entries.firstOrNull { it.id == value } ?: CHINESE
    }
}

enum class MenuTheme(val id: String, val text: Int, val muted: Int, val heading: Int) {
    DARK("dark", 0xEEEEEE, 0xCCCCCC, 0xFFFFFF),
    LIGHT("light", 0x252525, 0x444444, 0x111111);

    fun skin(skin: DialogCanvas.Skin): DialogCanvas.Skin =
        if (this == LIGHT && (skin.glyph in 0xE000..0xE080 || skin.glyph in 0xE200..0xE223))
            skin.copy(glyph = skin.glyph + 0x100)
        else skin

    companion object {
        fun parse(value: String?): MenuTheme = entries.firstOrNull { it.id == value } ?: DARK
    }
}

data class MenuPreferences(
    val language: MenuLanguage = MenuLanguage.CHINESE,
    val theme: MenuTheme = MenuTheme.DARK,
) {
    fun save(data: PersistentDataContainer) {
        data.set(LANGUAGE_KEY, PersistentDataType.STRING, language.id)
        data.set(THEME_KEY, PersistentDataType.STRING, theme.id)
    }

    companion object {
        private val LANGUAGE_KEY = NamespacedKey("playersettings", "menu_language")
        private val THEME_KEY = NamespacedKey("playersettings", "menu_theme")

        fun read(data: PersistentDataContainer) =
            MenuPreferences(
                MenuLanguage.entries.firstOrNull {
                    it.id == data.get(LANGUAGE_KEY, PersistentDataType.STRING)
                } ?: MenuRuntime.current.language,
                MenuTheme.entries.firstOrNull {
                    it.id == data.get(THEME_KEY, PersistentDataType.STRING)
                } ?: MenuRuntime.current.theme,
            )
    }
}
