package online.toraka.playersettings

import java.util.Properties
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor

/** Whole sprites share an origin with the nine-pixel click grid. */
class DialogCanvas(
    private val theme: MenuTheme = MenuTheme.DARK,
    private val click: (String) -> ClickEvent<*>,
) {
    data class Skin(val glyph: Int, val width: Int, val rows: Int)

    private data class Sprite(val x: Int, val row: Int, val skin: Skin, val action: String? = null)

    private data class Label(
        val x: Int,
        val row: Int,
        val text: String,
        val color: Int,
        val action: String? = null,
        val raised: Boolean = false,
    )

    data class Hit(val x: Int, val row: Int, val width: Int, val rows: Int, val action: String)

    private val sprites = mutableListOf<Sprite>()
    private val labels = mutableListOf<Label>()
    val hits = mutableListOf<Hit>()

    fun sprite(x: Int, row: Int, skin: Skin, action: String? = null) {
        sprites += Sprite(x, row, theme.skin(skin), action)
        if (action != null) hits += Hit(x, row, skin.width, skin.rows, action)
    }

    fun text(
        x: Int,
        row: Int,
        text: String,
        color: Int = theme.text,
        action: String? = null,
        raised: Boolean = false,
    ) {
        labels += Label(x, row, text, color, action, raised)
    }

    fun button(x: Int, row: Int, skin: Skin, label: String, action: String) {
        // Keep the click event on the visual glyphs and label as well as on the
        // invisible hit grid.  Some clients resolve a Dialog body's hit style
        // from the painted glyph instead of the preceding spacing component.
        // Having both representations makes the whole visible button reliable.
        sprite(x, row, skin, action)
        val inset = if (skin == NAV || skin == SELECTED_NAV || skin == SEARCH) 20 else 6
        labels +=
            Label(
                x + inset,
                row + 1,
                fit(label, skin.width - inset - 6),
                if (skin == SELECTED_NAV || skin == SELECTED_CONTROL) 0x122408 else theme.text,
                action,
                true,
            )
    }

    fun build(): Component {
        val result = Component.text()
        for (row in 0 until ROWS) {
            // Hit advances come first so getStyleAtWidth sees a positive, monotonic click grid.
            // Following negative advances paint the visual layers on the same coordinates.
            val cuts = sortedSetOf(0, LINE_WIDTH)
            hits
                .filter { row in it.row until it.row + it.rows }
                .forEach {
                    cuts += it.x
                    cuts += it.x + it.width
                }
            cuts.toList().zipWithNext().forEach { (start, end) ->
                val hit = hits.lastOrNull {
                    row in it.row until it.row + it.rows && start >= it.x && end <= it.x + it.width
                }
                var region = space(end - start)
                if (hit != null) region = region.clickEvent(click(hit.action))
                result.append(region)
            }
            result.append(space(-LINE_WIDTH))
            sprites
                .filter { row == it.row }
                .forEach {
                    result.append(space(it.x))
                    val columns = if (it.skin.width > 256) 2 else 1
                    repeat(columns) { column ->
                        var glyph: Component =
                            Component.text((it.skin.glyph + column).toChar().toString())
                                .font(FONT)
                                .color(NamedTextColor.WHITE)
                        if (it.action != null) glyph = glyph.clickEvent(click(it.action))
                        result.append(glyph)
                        // Bitmap advances trim transparent right edges. Restore the
                        // texture cell width using the compiled, measured advance.
                        result.append(
                            space(it.skin.width / columns - glyphWidth(it.skin.glyph + column))
                        )
                    }
                    result.append(space(-it.x - it.skin.width))
                }
            labels
                .filter { it.row == row }
                .forEach {
                    result.append(space(it.x))
                    var label: Component =
                        Component.text(it.text, TextColor.color(it.color))
                            .font(if (it.raised) BUTTON_LABEL_FONT else LABEL_FONT)
                    if (it.action != null) label = label.clickEvent(click(it.action))
                    result.append(label)
                    result.append(space(-it.x - textWidth(it.text)))
                }
            result.append(space(LINE_WIDTH))
            if (row < ROWS - 1) result.append(Component.newline())
        }
        return result.build().shadowColor(ShadowColor.none())
    }

    fun densitySlider(x: Int, row: Int, selected: String, language: MenuLanguage) {
        val ids = listOf("off", "low", "medium", "high")
        val index = ids.indexOf(selected.lowercase()).takeIf { it >= 0 } ?: ids.size
        val label = fit(SettingsDialog.densityLabel(selected, language), 52)
        text(x - 8 - textWidth(label), row + 1, label, raised = true)
        val previous = ids.getOrNull(index - 1).takeIf { index in 1..3 }
        val next = ids.getOrNull(index + 1)
        sprite(
            x,
            row,
            Skin(if (previous == null) 0xE222 else 0xE220, 18, 2),
            previous?.let { "density_$it" },
        )
        sprite(
            x + 146,
            row,
            Skin(if (next == null) 0xE223 else 0xE221, 18, 2),
            next?.let { "density_$it" },
        )
        ids.forEachIndexed { position, id ->
            sprite(
                x + 22 + position * 30,
                row,
                Skin(0xE200 + index * 4 + position, 30, 2),
                "density_$id",
            )
        }
    }

    companion object {
        const val WIDTH = 450
        const val ROWS = 29
        // PlainMessageHandler builds FocusableTextWidget with 4px padding on
        // EACH side. Its maxWidth includes that padding; text receives less.
        const val BODY_PADDING = 4
        const val WRAP_SLACK = 2
        // Keep slack in the measured line itself: updateHeight() wraps again at
        // the measured content width, not at the requested widget width.
        const val LINE_WIDTH = WIDTH + WRAP_SLACK
        const val BODY_WIDTH = LINE_WIDTH + BODY_PADDING * 2
        // Opt-in geometry used by the scoped GUI shader. Ordinary dialogs retain
        // their native width. Keep in sync with the resource-pack selector.
        const val FRAMELESS_BODY_WIDTH = BODY_WIDTH + 14
        val FONT = Key.key("toraka_settings:ui")
        val LABEL_FONT = Key.key("toraka_settings:labels")
        val BUTTON_LABEL_FONT = Key.key("toraka_settings:button_labels")
        private val metrics =
            Properties().apply {
                DialogCanvas::class.java.getResourceAsStream("/ui-metrics.properties").use {
                    requireNotNull(it) { "Missing compiled UI font metrics" }
                    load(it)
                }
            }

        fun glyphWidth(glyph: Int): Int = metrics.getProperty("glyph.$glyph").toInt()

        val PANEL_TOP = Skin(0xE000, 336, 9)
        val PANEL_BOTTOM = Skin(0xE020, 336, 14)
        val NAV = Skin(0xE040, 102, 2)
        val SELECTED_NAV = Skin(0xE050, 102, 2)
        val CONTROL = Skin(0xE060, 114, 2)
        val SELECTED_CONTROL = Skin(0xE070, 114, 2)
        val SEARCH = Skin(0xE080, 102, 2)
        val SEARCH_ICON = Skin(0xE096, 9, 1)
        val PANEL_ACTION = Skin(0xE097, 9, 2)
        val DROPDOWN_DOWN = Skin(0xE098, 9, 1)
        val DROPDOWN_UP = Skin(0xE099, 9, 1)

        fun space(width: Int): Component {
            require(width in -512..512)
            return Component.text((0xE800 + width + 512).toChar().toString()).font(FONT)
        }

        // ASCII metrics come from the bundled menu font, independent of GUI
        // scale, Force Unicode Font, or another pack's minecraft:default.
        fun textWidth(text: String): Int = text.sumOf { c ->
            metrics.getProperty("label.${c.code}")?.toInt() ?: 9
        }

        fun fit(text: String, pixels: Int): String {
            var result = ""
            for (c in text) {
                if (textWidth(result + c) > pixels) break
                result += c
            }
            return result
        }
    }
}
