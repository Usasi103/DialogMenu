package online.toraka.dialogmenu

import java.io.File
import java.util.Locale
import org.bukkit.configuration.ConfigurationSection

/** Menu-owned translations; independent of CraftEngine and the settings language preference. */
data class MenuTranslations(
    val defaultLanguage: String = "zh_cn",
    val messages: Map<String, Map<String, String>> = emptyMap(),
) {
    fun get(key: String, locale: Locale?): String? {
        val requested = locale?.toLanguageTag()?.replace('-', '_')?.lowercase(Locale.ROOT)
        val candidates = listOfNotNull(requested, requested?.substringBefore('_'), defaultLanguage)
        return candidates.distinct().firstNotNullOfOrNull { messages[it]?.get(key) }
    }

    companion object {
        fun initialize(directory: File) {
            for (name in listOf("text.yml", "translations/zh_cn.yml", "translations/en_us.yml")) {
                val file = File(directory, name)
                if (!file.exists()) {
                    file.parentFile.mkdirs()
                    file.writeText(MenuRepository.resource(name), Charsets.UTF_8)
                }
            }
        }

        fun read(directory: File): MenuTranslations {
            val settingsFile = File(directory, "text.yml")
            val settings = MenuConfigParser.yaml(settingsFile.readText(Charsets.UTF_8), "text.yml")
            require(settings.getKeys(false).all { it == "DefaultLanguage" }) {
                "text.yml: 仅支持 DefaultLanguage"
            }
            val default =
                settings
                    .getString("DefaultLanguage", "zh_cn")!!
                    .replace('-', '_')
                    .lowercase(Locale.ROOT)
            val translations = linkedMapOf<String, Map<String, String>>()
            File(directory, "translations")
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension == "yml" }
                .sortedBy { it.name }
                .forEach { file ->
                    val locale = file.nameWithoutExtension.replace('-', '_').lowercase(Locale.ROOT)
                    require(locale.matches(Regex("[a-z]{2,8}(?:_[a-z0-9]{2,8})*"))) {
                        "translations/${file.name}: 无效语言文件名"
                    }
                    require(locale !in translations) { "translations: 重复语言 $locale" }
                    val root = MenuConfigParser.yaml(file.readText(Charsets.UTF_8), file.name)
                    val values = root.getValues(true).filterValues { it !is ConfigurationSection }
                    require(values.values.all { it is String && it.length <= 8192 }) {
                        "translations/${file.name}: 翻译必须为不超过 8192 字符的字符串"
                    }
                    translations[locale] = values.mapValues { it.value as String }
                }
            require(default in translations) {
                "text.yml.DefaultLanguage: 缺少 translations/$default.yml"
            }
            return MenuTranslations(default, translations)
        }
    }
}
