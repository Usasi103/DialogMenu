package online.toraka.dialogmenu

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ToggleSwitchTest {
    private fun parse(style: String = "", binding: String = "Bind: particles") =
        SimpleMenuParser.parse("Version: 2\nPages: [test]") {
            "Title: 测试\nLayout: [开关]\nIcons:\n  开关:\n    Type: toggle\n    $binding\n    $style"
        }

    @Test
    fun `switch is opt in and preserves builtin and custom actions`() {
        val old = parse().pages.getValue("test").widgets.last()
        val menu = parse("Style: switch")
        val widget = menu.pages.getValue("test").widgets.last()
        assertEquals(ToggleStyle.BUTTON, old.toggleStyle)
        assertEquals(ToggleStyle.SWITCH, widget.toggleStyle)
        assertEquals(old.copy(toggleStyle = ToggleStyle.SWITCH), widget)
        assertEquals("particles toggle", menu.actions.getValue(widget.action).command)
        val custom =
            parse("Style: switch", "State: '%custom_toggle%'\n    Actions: ['command: toggle']")
        val customWidget = custom.pages.getValue("test").widgets.last()
        assertEquals("%custom_toggle%", custom.states.getValue(customWidget.state))
        assertEquals("toggle", custom.actions.getValue(customWidget.action).steps.single().command)
        for (style in listOf("Style: typo", "Style: true", "Style: ''")) {
            assertThrows(Exception::class.java) { parse(style) }
        }
        assertThrows(Exception::class.java) {
            SimpleMenuParser.parse("Version: 2\nPages: [test]") {
                "Title: 测试\nLayout: [按钮]\nIcons:\n  按钮: {Type: button, Style: switch, Actions: [close]}"
            }
        }
    }

    @Test
    fun `switch states themes languages and painted click events keep exact canvas width`() {
        val menu = parse("Style: switch")
        val widget = menu.pages.getValue("test").widgets.last()
        for (theme in MenuTheme.entries) for (language in MenuLanguage.entries) {
            for ((stateIndex, state) in listOf("true", "false", null).withIndex()) {
                val canvas =
                    MenuRenderer.render(
                        menu,
                        "test",
                        language,
                        theme,
                        { state },
                        { it },
                        { ClickEvent.custom(Key.key("test", it)) },
                    )
                val parts = canvas.build().children().map { it as TextComponent }
                val glyph = parts.single { it.font() == DialogCanvas.SWITCH_FONT }
                assertEquals(
                    0xE700 + stateIndex + if (theme == MenuTheme.LIGHT) 3 else 0,
                    glyph.content().single().code,
                )
                val action = "action/${widget.action}"
                assertEquals(ClickEvent.custom(Key.key("test", action)), glyph.clickEvent())
                val status =
                    menu.text(language, "$" + listOf("on", "off", "unavailable")[stateIndex])
                assertTrue(
                    parts.any { it.content() == status && it.clickEvent() == glyph.clickEvent() }
                )
                for (x in widget.x + 78 until widget.x + 114) for (row in
                    widget.row..widget.row + 1) {
                    assertEquals(
                        action,
                        canvas.hits
                            .last {
                                x in it.x until it.x + it.width &&
                                    row in it.row until it.row + it.rows
                            }
                            .action,
                    )
                }
                var width = 0
                var lines = 1
                for (part in parts) {
                    if (part.content() == "\n") {
                        assertEquals(452, width)
                        width = 0
                        lines++
                    } else if (part.font() == DialogCanvas.SWITCH_FONT) width += 37
                    else if (part.font() == DialogCanvas.FONT) {
                        val code = part.content().single().code
                        width +=
                            if (code in 0xE800..0xEC00) code - 0xEA00
                            else DialogCanvas.glyphWidth(code)
                    } else width += DialogCanvas.textWidth(part.content())
                    assertTrue(width in 0..452, "$theme $language $state width=$width")
                }
                assertEquals(29, lines)
                assertEquals(452, width)
            }
        }
    }
}
