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

    fun wrap(text: String, width: Int, textSize: Int = 8, bold: Boolean = false): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (character in text) {
            if (
                DialogCanvas.textWidth(line + character, textSize, bold) > width &&
                    line.isNotEmpty()
            ) {
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
        richText: RichMenuText = RichMenuText(),
        click: (String) -> ClickEvent,
    ): DialogCanvas {
        val canvas =
            DialogCanvas(
                MenuTheme.DARK,
                template.width,
                template.rows,
                net.kyori.adventure.key.Key.key("dialogmenu_dialogue:labels"),
                net.kyori.adventure.key.Key.key("dialogmenu_dialogue:button_labels"),
                richText,
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
                            canvas
                                .prepare(
                                    expand(element.lines.single()),
                                    bold = element.bold,
                                    raised = true,
                                )
                                .fit(element.width - 8)
                        canvas.text(
                            element.x + (element.width - label.width) / 2,
                            element.row + 1,
                            label,
                            element.color,
                            element.id,
                            true,
                            bold = element.bold,
                        )
                    }
                    "text" -> {
                        val stride = TitleFont.lineRows(element.textSize)
                        val capacity = element.rows / stride
                        val lines =
                            element.lines.flatMap {
                                canvas
                                    .prepare(expand(it), element.textSize, element.bold)
                                    .wrap(element.width)
                            }
                        lines.take(capacity).forEachIndexed { row, line ->
                            val label =
                                if (row == capacity - 1 && lines.size > capacity)
                                    canvas.prepare("..", element.textSize, element.bold).let {
                                        suffix ->
                                        line.fit(element.width - suffix.width) + suffix
                                    }
                                else line
                            canvas.text(
                                element.x,
                                element.row + row * stride,
                                label,
                                element.color,
                                textSize = element.textSize,
                                bold = element.bold,
                            )
                        }
                    }
                }
            }
        return canvas
    }
}
