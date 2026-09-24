package online.toraka.dialogmenu

/** One open demo, owned by one player's View; never reads plugins or writes player data. */
class SettingsDemoSession(private val menu: MenuDefinition, initial: MenuPreferences) {
    private val controls = menu.pages.values.flatMap { it.widgets }.filter { it.state.isNotEmpty() }
    private val values =
        controls
            .associate { widget ->
                widget.state to
                    when (menu.states[widget.state]) {
                        "language" -> initial.language.id
                        "theme" -> initial.theme.id
                        else ->
                            if (widget.kind == WidgetKind.TOGGLE) "true"
                            else widget.options.last().value
                    }
            }
            .toMutableMap()

    val preferences: MenuPreferences
        get() =
            MenuPreferences(
                MenuLanguage.parse(valueFor("language") ?: menu.language.id),
                MenuTheme.parse(valueFor("theme") ?: menu.theme.id),
            )

    private fun valueFor(binding: String) =
        menu.states.entries.firstOrNull { it.value == binding }?.key?.let(values::get)

    fun state(id: String): String? = values[id]

    fun apply(action: String): Boolean {
        for (widget in controls) {
            if (widget.kind == WidgetKind.TOGGLE && widget.action == action) {
                values[widget.state] = (values[widget.state] != "true").toString()
                return true
            }
            val option = widget.options.firstOrNull { it.action == action }
            if (option != null) {
                values[widget.state] = option.value
                return true
            }
        }
        return false
    }

    companion object {
        fun validate(menu: MenuDefinition) {
            require(menu.pages.values.none { it.itemLayout }) {
                "settings-demo: 演示菜单使用字体控件，不使用外部物品源"
            }
            val controls = menu.pages.values.flatMap { it.widgets }
            val simulated =
                controls
                    .flatMap { widget ->
                        (if (widget.kind == WidgetKind.TOGGLE) listOf(widget.action)
                        else emptyList()) + widget.options.map { it.action }
                    }
                    .toSet()
            fun safe(action: MenuAction): Boolean =
                when {
                    action.steps.isNotEmpty() -> action.steps.all(::safe)
                    action.type == "page" -> true
                    action.type == "builtin" -> action.value in setOf("close", "refresh", "search")
                    else -> false
                }
            val reachable = menu.common + controls
            val actions = reachable.map { it.action }.filter { it.isNotEmpty() } + menu.footerAction
            require(actions.all { it in simulated || safe(menu.actions.getValue(it)) }) {
                "settings-demo: 按钮仅支持关闭、刷新、搜索和页内跳转；绑定控件自动模拟，不执行外部指令"
            }
        }
    }
}
