package online.toraka.dialogmenu

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SettingsLayoutTest {
    private fun parse(page: String, navigation: String = "") =
        SimpleMenuParser.parse("Version: 2\nPages: [test]\n$navigation") { page.trimIndent() }

    @Test
    fun `settings positions and typography reach rendered text controls and click regions`() {
        val menu =
            parse(
                """
            Title: 测试
            TitleStyle: {Position: [130, 1], FontSize: 12, Bold: true}
            Layout: [文字, 标题, 开关, 主题]
            Icons:
              文字: {Type: text, Name: 文字预览, Position: [140, 4], Width: 240, FontSize: 16, Bold: true, Color: '#12ABCD'}
              标题: {Type: heading, Name: 控件, Position: [130, 11], FontSize: 12, Bold: true}
              开关: {Type: toggle, Style: switch, Name: 测试开关, Bind: particles, Position: [322, 14], LabelPosition: [132, 14], FontSize: 10, Bold: true}
              主题: {Type: dropdown, Name: 主题, Bind: theme, Position: [324, 18], FontSize: 10, Bold: true, Options: {dark: 深色, light: 浅色}}
        """,
                "Navigation: {Position: [4, 3], Step: 3, FontSize: 10, Bold: true}",
            )
        assertEquals(4, menu.navX)
        assertEquals(10, menu.navTextSize)
        assertTrue(menu.navBold)
        val widgets = menu.pages.getValue("test").widgets
        val text = widgets.single { it.text == "文字预览" }
        assertEquals(listOf(140, 4, 240, 16), listOf(text.x, text.row, text.width, text.textSize))
        val toggle = widgets.single { it.kind == WidgetKind.TOGGLE }
        assertEquals(
            listOf(322, 14, 132, 14),
            listOf(toggle.x, toggle.row, toggle.labelX, toggle.labelRow),
        )
        val canvas =
            MenuRenderer.render(
                menu,
                "test",
                MenuLanguage.CHINESE,
                MenuTheme.DARK,
                { if (it == "theme") "dark" else "true" },
                { it },
                { ClickEvent.custom(Key.key("test", it)) },
            )
        val parts = canvas.build().children().filterIsInstance<TextComponent>()
        val renderedText = parts.single { it.content() == "文字预览" }
        assertEquals(TitleFont.font(16), renderedText.font())
        assertEquals(TextDecoration.State.TRUE, renderedText.decoration(TextDecoration.BOLD))
        assertEquals(0x12ABCD, renderedText.color()!!.value())
        assertTrue(
            parts.any {
                it.content() == "开启" &&
                    it.font() == TitleFont.font(10, true) &&
                    it.clickEvent() != null
            }
        )
        assertTrue(
            canvas.hits.any {
                it.x == 400 && it.row == 14 && it.action == "action/${toggle.action}"
            }
        )
        assertTrue(
            canvas.hits.any { it.x == 324 && it.row == 18 && it.action.startsWith("dropdown/") }
        )
    }

    @Test
    fun `automatic text layout reserves rows for larger fonts`() {
        val menu =
            parse(
                """
            Title: 测试
            Layout: [标题, 下行]
            Icons:
              标题: {Type: text, Name: 大字, FontSize: 16}
              下行: {Type: text, Name: 下一行}
        """
            )
        val widgets = menu.pages.getValue("test").widgets
        assertEquals(3, widgets.single { it.text == "大字" }.row)
        assertEquals(6, widgets.single { it.text == "下一行" }.row)
    }

    @Test
    fun `invalid coordinates fonts types and overlapping click targets are rejected`() {
        val prefix = "Title: 测试\nLayout: [控件]\nIcons:\n  控件: "
        for (fields in
            listOf(
                "Type: text, Position: [1.5, 3]",
                "Type: text, Position: [123, 29]",
                "Type: text, FontSize: '12'",
                "Type: text, Bold: 'true'",
                "Type: text, LabelPosition: [123, 3]",
                "Type: text, FontSize: 25",
                "Type: toggle, Bind: particles, FontSize: 16",
                "Type: button, Actions: [close], Position: [0, 3]",
                "Type: dropdown, Bind: theme, Position: [330, 25], Options: {dark: 深色, light: 浅色}",
            )) {
            assertThrows(Exception::class.java, { parse(prefix + "{" + fields + "}") }, fields)
        }
    }
}
