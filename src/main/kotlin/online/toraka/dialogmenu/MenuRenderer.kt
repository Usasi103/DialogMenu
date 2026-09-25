package online.toraka.dialogmenu

import net.kyori.adventure.text.event.ClickEvent

object MenuRenderer {
    fun render(
        menu: MenuDefinition,
        pageId: String,
        language: MenuLanguage,
        theme: MenuTheme,
        state: (String) -> String?,
        expand: (String) -> String,
        click: (String) -> ClickEvent,
        dropdown: Int = -1,
    ): DialogCanvas {
        val canvas = DialogCanvas(theme, click = click)
        fun text(value: String) =
            expand(menu.text(language, value))
                .map { if (it.isISOControl()) ' ' else it }
                .joinToString("")
        fun action(id: String) = id.takeIf { it.isNotEmpty() }?.let { "action/$it" }
        fun color(value: String) =
            when (value) {
                "heading" -> theme.heading
                "text" -> theme.text
                "muted" -> theme.muted
                else -> value.drop(1).toInt(16)
            }
        var widgetIndex = 0
        var expanded: MenuWidget? = null
        fun draw(widget: MenuWidget) {
            val index = widgetIndex++
            val current = widget.state.takeIf { it.isNotEmpty() }?.let(state)
            if (widget.label.isNotEmpty()) {
                val room =
                    widget.x - widget.labelX - if (widget.kind == WidgetKind.SLIDER) 60 else 6
                val raised =
                    widget.textSize != 8 ||
                        widget.kind == WidgetKind.SLIDER ||
                        (widget.kind == WidgetKind.TOGGLE &&
                            widget.toggleStyle == ToggleStyle.SWITCH)
                canvas.text(
                    widget.labelX,
                    widget.labelRow,
                    DialogCanvas.fit(
                        text(widget.label),
                        room,
                        widget.textSize,
                        widget.bold,
                        if (raised) DialogCanvas.BUTTON_LABEL_FONT else DialogCanvas.LABEL_FONT,
                    ),
                    theme.text,
                    raised = raised,
                    textSize = widget.textSize,
                    bold = widget.bold,
                )
            }
            when (widget.kind) {
                WidgetKind.TEXT,
                WidgetKind.HEADING ->
                    canvas.text(
                        widget.x,
                        widget.row,
                        DialogCanvas.fit(
                            text(widget.text),
                            widget.width,
                            widget.textSize,
                            widget.bold,
                        ),
                        color(widget.color),
                        action(widget.action),
                        textSize = widget.textSize,
                        bold = widget.bold,
                    )
                WidgetKind.SPRITE ->
                    canvas.sprite(
                        widget.x,
                        widget.row,
                        MenuConfigParser.skins.getValue(widget.skin),
                        action(widget.action),
                    )
                WidgetKind.BUTTON -> {
                    val base = MenuConfigParser.skins.getValue(widget.skin)
                    val selected = widget.selected.isNotEmpty() && widget.selected == current
                    val skin =
                        if (!selected) base
                        else if (base == DialogCanvas.NAV || base == DialogCanvas.SEARCH)
                            DialogCanvas.SELECTED_NAV
                        else DialogCanvas.SELECTED_CONTROL
                    canvas.button(
                        widget.x,
                        widget.row,
                        skin,
                        text(widget.text),
                        "action/${widget.action}",
                        textSize = widget.textSize,
                        bold = widget.bold,
                    )
                }
                WidgetKind.TOGGLE -> {
                    val on = MenuDialog.booleanState(current)
                    val label =
                        menu.text(
                            language,
                            "$" +
                                when (on) {
                                    true -> "on"
                                    false -> "off"
                                    null -> "unavailable"
                                },
                        )
                    if (widget.toggleStyle == ToggleStyle.SWITCH) {
                        canvas.toggleSwitch(
                            widget.x,
                            widget.row,
                            on,
                            label,
                            "action/${widget.action}",
                            textSize = widget.textSize,
                            bold = widget.bold,
                        )
                    } else {
                        canvas.button(
                            widget.x,
                            widget.row,
                            if (on == true) DialogCanvas.SELECTED_CONTROL else DialogCanvas.CONTROL,
                            label,
                            "action/${widget.action}",
                            textSize = widget.textSize,
                            bold = widget.bold,
                        )
                    }
                }
                WidgetKind.SLIDER -> {
                    val index = widget.options.indexOfFirst { it.value.equals(current, true) }
                    val label =
                        widget.options.getOrNull(index)?.let { text(it.label) }
                            ?: menu.text(language, "$" + "unavailable")
                    canvas.slider(
                        widget.x,
                        widget.row,
                        index,
                        label,
                        widget.options.map { "action/${it.action}" },
                        textSize = widget.textSize,
                        bold = widget.bold,
                    )
                }
                WidgetKind.DROPDOWN -> {
                    val opened = dropdown == index
                    val route = "dropdown/$index"
                    val label =
                        widget.options
                            .firstOrNull { it.value.equals(current, true) }
                            ?.let { text(it.label) } ?: menu.text(language, "$" + "unavailable")
                    canvas.button(
                        widget.x,
                        widget.row,
                        DialogCanvas.CONTROL,
                        label,
                        route,
                        22,
                        widget.textSize,
                        widget.bold,
                    )
                    canvas.sprite(
                        widget.x + 98,
                        widget.row,
                        if (opened) DialogCanvas.DROPDOWN_UP else DialogCanvas.DROPDOWN_DOWN,
                        route,
                    )
                    if (opened) expanded = widget
                }
            }
        }
        menu.common.forEach(::draw)
        menu.pages.values.forEachIndexed { index, page ->
            val row = menu.navRow + index * menu.navStep
            canvas.button(
                menu.navX,
                row,
                if (page.id == pageId) DialogCanvas.SELECTED_NAV else DialogCanvas.NAV,
                text(page.label),
                "page/${page.id}",
                textSize = menu.navTextSize,
                bold = menu.navBold,
            )
            if (page.icon.isNotEmpty())
                canvas.sprite(
                    menu.navX + 6,
                    row,
                    MenuConfigParser.skins.getValue(page.icon),
                    "page/${page.id}",
                )
        }
        menu.pages.getValue(pageId).widgets.forEach(::draw)
        expanded?.let { widget ->
            canvas.coverLabels(widget.x, widget.row + 2, 114, widget.options.size * 2)
            val current = state(widget.state)
            widget.options.forEachIndexed { index, option ->
                canvas.button(
                    widget.x,
                    widget.row + 2 + index * 2,
                    if (option.value.equals(current, true)) DialogCanvas.SELECTED_CONTROL
                    else DialogCanvas.CONTROL,
                    text(option.label),
                    "action/${option.action}",
                    textSize = widget.textSize,
                    bold = widget.bold,
                )
            }
        }
        return canvas
    }
}
