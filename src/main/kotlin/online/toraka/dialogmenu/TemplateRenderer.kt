package online.toraka.dialogmenu

import net.kyori.adventure.text.event.ClickEvent

object TemplateRenderer {
    fun visible(element: TemplateElement, values: Map<String, String>): Boolean =
        element.condition?.let { values[it.first] == it.second } ?: true

    fun expand(text: String, values: Map<String, String>, player: String, uuid: String): String =
        Regex("\\{([a-zA-Z0-9_-]+)}").replace(text) {
            when (val key = it.groupValues[1]) {
                "player" -> player
                "uuid" -> uuid
                else -> values[key] ?: it.value
            }
        }

    fun wrap(text: String, width: Int): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (character in text) {
            if (DialogCanvas.textWidth(line + character) > width && line.isNotEmpty()) {
                lines += line
                line = ""
            }
            line += character
        }
        lines += line
        return lines
    }

    fun render(
        template: DialogTemplate,
        values: Map<String, String>,
        expand: (String) -> String,
        click: (String) -> ClickEvent<*>,
    ): DialogCanvas {
        val canvas =
            DialogCanvas(
                MenuTheme.DARK,
                template.width,
                template.rows,
                net.kyori.adventure.key.Key.key("toraka_dialogue:labels"),
                net.kyori.adventure.key.Key.key("toraka_dialogue:button_labels"),
                click,
            )
        template.background?.let { canvas.sprite(0, 0, it) }
        template.elements
            .filter { visible(it, values) }
            .forEach { element ->
                val selected = element.selected?.let { values[it.first] == it.second } ?: false
                val sprite =
                    if (selected) element.selectedSprite ?: element.sprite else element.sprite
                when (element.type) {
                    "sprite" -> canvas.sprite(element.x, element.row, requireNotNull(sprite))
                    "button" -> {
                        canvas.sprite(element.x, element.row, requireNotNull(sprite), element.id)
                        val label =
                            DialogCanvas.fit(expand(element.lines.single()), element.width - 8)
                        canvas.text(
                            element.x + (element.width - DialogCanvas.textWidth(label)) / 2,
                            element.row + 1,
                            label,
                            element.color,
                            element.id,
                            true,
                        )
                    }
                    "text" -> {
                        val lines = element.lines.flatMap { wrap(expand(it), element.width) }
                        lines.take(element.rows).forEachIndexed { row, line ->
                            val label =
                                if (row == element.rows - 1 && lines.size > element.rows)
                                    DialogCanvas.fit(line, element.width - 12) + ".."
                                else line
                            canvas.text(element.x, element.row + row, label, element.color)
                        }
                    }
                }
            }
        return canvas
    }
}
