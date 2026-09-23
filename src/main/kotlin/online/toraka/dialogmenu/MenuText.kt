package online.toraka.dialogmenu

/** External YAML translations are selected per player. */
object MenuText {
    fun keys(language: MenuLanguage): Set<String> =
        MenuRuntime.current.translations.getValue(language).keys

    fun get(language: MenuLanguage, key: String, vararg values: Any): String {
        val template = MenuRuntime.current.translations.getValue(language).getValue(key)
        // Replace placeholders once so player-provided values cannot expand more placeholders.
        return Regex("\\{(\\d+)\\}").replace(template) { match ->
            values.getOrNull(match.groupValues[1].toInt())?.toString() ?: match.value
        }
    }
}
