package online.toraka.playersettings

import java.nio.file.Path
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SimpleMenuTest {
    @TempDir lateinit var directory: Path

    private val config = "Version: 2\nPages: [example]\n"

    private fun parse(page: String) = SimpleMenuParser.parse(config) { page.trimIndent() }

    private fun bundled() =
        SimpleMenuParser.parse(MenuRepository.resource("simple/config.yml")) {
            MenuRepository.resource("simple/menus/$it.yml")
        }

    @Test
    fun `fresh install exports per page files and failed multi file reload preserves prior snapshot`() {
        val repository = MenuRepository(directory.toFile())
        repository.initialize()
        assertTrue(directory.resolve("config.yml").toFile().isFile)
        assertFalse(directory.resolve("menu.yml").toFile().exists())
        assertFalse(directory.resolve("languages").toFile().exists())
        val appearance = directory.resolve("menus/appearance.yml").toFile()
        val particles = directory.resolve("menus/particles.yml").toFile()
        val original = repository.current
        val particlesSource = particles.readText()
        appearance.writeText(appearance.readText().replace("界面与语言", "我的界面"))
        particles.writeText("Layout: [\n")
        assertThrows(Exception::class.java) { repository.reload() }
        assertSame(original, repository.current)
        assertEquals("Layout: [\n", particles.readText())
        particles.writeText(particlesSource)
        repository.reload()
        assertEquals(
            "我的界面",
            repository.current.text(
                MenuLanguage.CHINESE,
                repository.current.pages.getValue("appearance").label,
            ),
        )
        val restarted = MenuRepository(directory.toFile())
        restarted.initialize()
        assertEquals(repository.current, restarted.current)
        appearance.delete()
        val valid = repository.current
        assertTrue(
            assertThrows(Exception::class.java) { repository.reload() }
                .message!!
                .contains("menus/appearance.yml")
        )
        assertSame(valid, repository.current)
    }

    @Test
    fun `proposed language theme and close command sample compiles with automatic placement`() {
        val menu =
            parse(
                """
            Title: "界面与语言"
            Layout: [语言, 主题, 回城]
            Icons:
              语言:
                Type: dropdown
                Name: "菜单语言"
                Description: "选择这个菜单使用的语言"
                Bind: language
                Options: {zh_cn: "简体中文", en_us: "English"}
              主题:
                Type: dropdown
                Name: {zh_cn: "界面主题", en_us: "Menu theme"}
                Bind: theme
                Options: {dark: "暗色", light: "亮色"}
              回城:
                Name: "返回主城"
                Permission: "example.travel"
                Actions: ["close", "command: spawn"]
        """
            )
        val widgets = menu.pages.getValue("example").widgets
        val dropdowns = widgets.filter { it.kind == WidgetKind.DROPDOWN }
        assertEquals(listOf(4, 14), dropdowns.map { it.row })
        val language = dropdowns.first().options.first()
        assertEquals("language:zh_cn", menu.actions.getValue(language.action).value)
        assertEquals("Menu theme", menu.text(MenuLanguage.ENGLISH, dropdowns.last().label))
        val button = widgets.single { it.kind == WidgetKind.BUTTON }
        assertEquals(16, button.row)
        val action = menu.actions.getValue(button.action)
        assertTrue(action.close)
        assertEquals("example.travel", action.permission)
        assertEquals("spawn", action.steps.single().command)
        assertEquals("player-command", action.steps.single().type)
    }

    @Test
    fun `custom PAPI dropdown and ordered player console actions retain identity and values`() {
        val menu =
            parse(
                """
            Title: "自定义"
            Layout: [选项]
            Icons:
              选项:
                Type: dropdown
                Name: "模式"
                State: "%example_mode%"
                Permission: example.mode
                Options:
                  easy:
                    Name: "简单"
                    Actions: ["command: mode easy", "console: give {player} stone 1", "refresh"]
                  hard:
                    Name: "困难"
                    Actions: ["command: mode hard"]
        """
            )
        val widget =
            menu.pages.getValue("example").widgets.single { it.kind == WidgetKind.DROPDOWN }
        assertEquals("%example_mode%", menu.states.getValue(widget.state))
        val action = menu.actions.getValue(widget.options.first().action)
        assertEquals("example.mode", action.permission)
        assertEquals(
            listOf("player-command", "console-command", "builtin"),
            action.steps.map { it.type },
        )
        assertEquals("give {player} stone 1", action.steps[1].command)
    }

    @Test
    fun `bindings reject unknown choices and incompatible types instead of ignoring fields`() {
        val valid =
            "Title: 测试\nLayout: [主题]\nIcons:\n  主题:\n    Type: dropdown\n    Bind: theme\n    Options: {dark: 暗色, light: 亮色}\n"
        for (source in
            listOf(
                valid.replace("Bind: theme", "Bind: typo"),
                valid.replace("light: 亮色", "auto: 自动"),
                valid.replace("Type: dropdown", "Type: toggle"),
                valid.replace("Bind: theme", "Bind: theme\n    State: '%custom_state%'"),
                valid.replace("Bind: theme", "Bind: theme\n    Permission: true"),
                valid.replace("Bind: theme", "Bind: theme\n    Actions: ['close']"),
                valid.replace("Type: dropdown", "Typo: dropdown"),
                valid.replace("Layout: [主题]", "Layout: [主题, 主题]"),
                valid + "Title: duplicate\n",
            )) assertThrows(Exception::class.java) { parse(source) }
        assertThrows(Exception::class.java) {
            SimpleMenuParser.parse("Version: 2\nPages: ['../secrets']") { error("must not read") }
        }
    }

    @Test
    fun `invalid commands action order and overflowing automatic layout fail validation`() {
        for (action in
            listOf(
                "command: /spawn",
                "console: say %untrusted%",
                "console: say {query}",
                "unknown: x",
            )) {
            assertThrows(Exception::class.java) {
                parse("Title: 测试\nLayout: [按钮]\nIcons:\n  按钮:\n    Actions: ['$action']")
            }
        }
        assertThrows(Exception::class.java) {
            parse(
                "Title: 测试\nLayout: [按钮]\nIcons:\n  按钮:\n    Actions: ['refresh', 'command: spawn']"
            )
        }
        val names = (0..10).map { "item$it" }
        assertThrows(Exception::class.java) {
            parse(
                "Title: 测试\nLayout: [${names.joinToString()}]\nIcons:\n" +
                    names.joinToString("\n") {
                        "  $it: {Type: dropdown, Bind: theme, Options: {dark: 暗色, light: 亮色}}"
                    }
            )
        }
    }

    @Test
    fun `all simple pages and expanded dropdowns keep the fixed measured canvas`() {
        val menu = bundled()
        for (page in menu.pages.values) for (language in MenuLanguage.entries) for (theme in
            MenuTheme.entries) {
            val widgets = menu.common + page.widgets
            val opened =
                listOf(-1) + widgets.indices.filter { widgets[it].kind == WidgetKind.DROPDOWN }
            for (dropdown in opened) {
                val canvas =
                    MenuRenderer.render(
                        menu,
                        page.id,
                        language,
                        theme,
                        {
                            when (it) {
                                "language" -> language.id
                                "theme" -> theme.id
                                "density" -> "medium"
                                else -> "true"
                            }
                        },
                        { it },
                        { ClickEvent.custom(Key.key("test", it)) },
                        dropdown,
                    )
                var width = 0
                var lines = 1
                canvas.build().children().forEach { child ->
                    val part = child as TextComponent
                    if (part.content() == "\n") {
                        assertEquals(452, width)
                        width = 0
                        lines++
                    } else if (part.font() == DialogCanvas.FONT) {
                        val code = part.content().single().code
                        width +=
                            if (code in 0xE800..0xEC00) code - 0xEA00
                            else DialogCanvas.glyphWidth(code)
                    } else width += DialogCanvas.textWidth(part.content())
                    assertTrue(
                        width in 0..452,
                        "${page.id} $language $theme $dropdown width=$width",
                    )
                }
                assertEquals(29, lines)
                assertEquals(452, width)
                if (dropdown >= 0) {
                    val widget = widgets[dropdown]
                    widget.options.forEachIndexed { index, option ->
                        val row = widget.row + 2 + index * 2
                        assertEquals(
                            "action/${option.action}",
                            canvas.hits
                                .last {
                                    row in it.row until it.row + it.rows &&
                                        widget.x in it.x until it.x + it.width
                                }
                                .action,
                        )
                    }
                }
            }
        }
    }
}
