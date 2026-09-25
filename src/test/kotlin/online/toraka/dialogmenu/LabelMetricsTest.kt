package online.toraka.dialogmenu

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LabelMetricsTest {
    @Test
    fun `existing custom menu sprite fonts resolve to the renamed pack`() {
        val yaml =
            MenuRepository.resource("catalog/menus/demo-boss.yml")
                .replace("dialogmenu_dialogue:rewards", "toraka_dialogue:rewards")
        val catalog =
            MenuCatalogParser.parse(
                MenuRepository.resource("catalog/config.yml"),
                CatalogRepository.defaults.associateWith {
                    if (it == "demo-boss") yaml
                    else MenuRepository.resource("catalog/menus/$it.yml")
                },
            )
        val rewards =
            catalog.templates
                .getValue("demo-boss/intro")
                .elements
                .mapNotNull { it.sprite }
                .filter { it.font.value() == "rewards" }
        assertEquals(3, rewards.size)
        assertTrue(rewards.all { it.font.namespace() == "dialogmenu_dialogue" })
        assertEquals(Key.key("custom:icon"), resourceFont("custom:icon"))
    }

    @Test
    fun `currency symbols and mixed bold fonts use the actual client advances`() {
        // U+26C2 has 14 ink pixels in the 16px Unihex source: 14 / 2 + 1 = 8.
        assertEquals(8f, LabelMetrics.width("⛂", DialogCanvas.LABEL_FONT, false))
        assertEquals(5f, LabelMetrics.width(" ", DialogCanvas.LABEL_FONT, true))
        assertEquals(9.5f, LabelMetrics.width("\u3000", DialogCanvas.LABEL_FONT, false))
        assertEquals(
            "中",
            DialogCanvas.fit("中文", 19, bold = true, font = DialogCanvas.BUTTON_LABEL_FONT),
        )
        assertEquals(34, DialogCanvas.textWidth("3,030⛂"))
        assertEquals(8.5f, LabelMetrics.width("⛂", DialogCanvas.LABEL_FONT, true))
        assertEquals(9.5f, LabelMetrics.width("中", DialogCanvas.LABEL_FONT, true))
        assertEquals(10f, LabelMetrics.width("中", DialogCanvas.BUTTON_LABEL_FONT, true))
        assertEquals("⛂", DialogCanvas.fit("⛂⛂", 8))
    }

    @Test
    fun `coin row returns to the same origin before drawing the appearance category`() {
        val canvas = DialogCanvas { DialogClicks.custom(Key.key("test", it)) }
        // Same row as the profile balance and the appearance navigation label.
        canvas.text(123, 14, "金币：3,030⛂")
        canvas.button(0, 13, DialogCanvas.NAV, "界面与语言", "appearance")
        canvas.text(200, 16, "⛂", bold = true)
        canvas.text(20, 16, "中", bold = true)
        var row = 0
        var cursor = 0f
        fun visit(component: Component) {
            if (component is TextComponent) {
                val text = component.content()
                if (text == "\n") {
                    assertEquals(DialogCanvas.LINE_WIDTH.toFloat(), cursor, "row $row")
                    cursor = 0f
                    row++
                } else if (component.font() == DialogCanvas.FONT) {
                    text.codePoints().forEach { cp ->
                        cursor +=
                            when (cp) {
                                0xE7F0 -> 0.5f
                                0xE7F1 -> -0.5f
                                in 0xE800..0xEC00 -> (cp - 0xEA00).toFloat()
                                else -> DialogCanvas.glyphWidth(cp).toFloat()
                            }
                    }
                } else if (text.isNotEmpty()) {
                    if (text == "界面与语言") assertEquals(20f, cursor)
                    // Independent expected widths for this fixture, not the implementation's
                    // estimator.
                    cursor +=
                        when (text) {
                            "金币：3,030⛂" -> 61f
                            "界面与语言" -> 45f
                            "⛂" -> 8.5f
                            "中" -> 9.5f
                            else -> error(text)
                        }
                }
            }
            component.children().forEach(::visit)
        }
        visit(canvas.build())
        assertEquals(DialogCanvas.LINE_WIDTH.toFloat(), cursor)
        assertEquals(DialogCanvas.ROWS - 1, row)
    }
}
