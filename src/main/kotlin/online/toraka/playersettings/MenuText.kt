package online.toraka.playersettings

import java.util.Properties

/** Bundled menu translations are selected per player, independent of the client locale. */
object MenuText {
    private val bundles =
        MenuLanguage.entries.associateWith { language ->
            Properties().apply {
                requireNotNull(
                        MenuText::class.java.getResourceAsStream("/menu/${language.id}.properties")
                    )
                    .reader(Charsets.UTF_8)
                    .use { load(it) }
            }
        }

    fun keys(language: MenuLanguage): Set<String> = bundles.getValue(language).stringPropertyNames()

    fun get(language: MenuLanguage, key: String, vararg values: Any): String {
        val template =
            requireNotNull(bundles.getValue(language).getProperty(key)) {
                "Missing menu translation: ${language.id}/$key"
            }
        // Replace placeholders once so player-provided values cannot expand more placeholders.
        return Regex("\\{(\\d+)\\}").replace(template) { match ->
            values.getOrNull(match.groupValues[1].toInt())?.toString() ?: match.value
        }
    }
}
