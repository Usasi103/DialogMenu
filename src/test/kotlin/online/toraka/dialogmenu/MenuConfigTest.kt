package online.toraka.dialogmenu

import java.nio.file.Path
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MenuConfigTest {
    @TempDir lateinit var directory: Path

    private fun resource(name: String) =
        javaClass.getResourceAsStream("/$name")!!.bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }

    private fun languages() =
        MenuLanguage.entries.associateWith { resource("languages/${it.id}.yml") }

    private fun config() = YamlConfiguration().apply { loadFromString(resource("menu.yml")) }

    @Test
    fun `failed reload preserves complete snapshot and user files while successful reload replaces all files`() {
        directory.resolve("menu.yml").toFile().writeText(resource("menu.yml"))
        val repository = MenuRepository(directory.toFile())
        repository.initialize()
        val menuFile = directory.resolve("menu.yml").toFile()
        val langFile = directory.resolve("languages/zh_cn.yml").toFile()
        val original = repository.current
        val source = menuFile.readText()
        assertTrue(source.startsWith("# DialogMenu"))
        langFile.writeText(langFile.readText().replace("玩家设置", "我的菜单"))
        menuFile.writeText("pages: [\n")
        assertThrows(Exception::class.java) { repository.reload() }
        assertSame(original, repository.current)
        assertEquals("pages: [\n", menuFile.readText())
        menuFile.writeText(
            source.replace("default-page: \"profile\"", "default-page: \"particles\"")
        )
        repository.reload()
        assertEquals("particles", repository.current.defaultPage)
        assertEquals("我的菜单", repository.current.text(MenuLanguage.CHINESE, "$" + "menu.title"))
        val next = MenuRepository(directory.toFile())
        next.initialize()
        assertEquals("particles", next.current.defaultPage)
        assertEquals(
            menuFile.readText(),
            source.replace("default-page: \"profile\"", "default-page: \"particles\""),
        )
    }

    @Test
    fun `invalid actions coordinates missing text and overlapping click targets are rejected`() {
        val edits: List<(YamlConfiguration) -> Unit> =
            listOf(
                { it.set("default-page", "missing") },
                { it.set("actions.particles.type", "unknown") },
                { it.set("actions.particles.command", "say %untrusted_papi%") },
                { it.set("actions.particles.command", "say {query}") },
                { it.set("actions.particles.command", "say first\nstop") },
                { it.set("pages.particles.label", "$" + "missing_text") },
                { it.set("states.particles", "unknown-state") },
                { it.set("navigation.row", 28) },
                { it.set("navigation.rou", 3) },
                { it.set("hide-focus-outline", "typo") },
                { it.set("actions.particles.close", "typo") },
                {
                    it.set(
                        "pages.particles.widgets",
                        listOf(
                            mapOf(
                                "type" to "button",
                                "row" to 28,
                                "text" to "Bad",
                                "action" to "close",
                            )
                        ),
                    )
                },
                {
                    it.set(
                        "pages.particles.widgets",
                        listOf(
                            mapOf(
                                "type" to "button",
                                "x" to 0,
                                "row" to 3,
                                "text" to "Overlap",
                                "action" to "close",
                            )
                        ),
                    )
                },
            )
        edits.forEachIndexed { index, edit ->
            val menu = config()
            edit(menu)
            assertThrows(
                Exception::class.java,
                { MenuConfigParser.parse(menu.saveToString(), languages()) },
                "case $index",
            )
        }
    }

    @Test
    fun `duplicate YAML keys are rejected and command replacements do not expand recursively`() {
        assertThrows(Exception::class.java) {
            MenuConfigParser.parse(resource("menu.yml") + "\nversion: 1\n", languages())
        }
        assertEquals(
            "say {uuid} abc",
            CommandTemplate.render("say {player} {uuid}", "{uuid}", "abc"),
        )
    }

    @Test
    fun `custom page button console permission and three step slider can be configured without source changes`() {
        val menu = config()
        menu.set(
            "actions.reward",
            mapOf(
                "type" to "console-command",
                "command" to "give {player} stone 1",
                "permission" to "example.reward",
                "close" to true,
            ),
        )
        menu.set(
            "pages.custom",
            mapOf(
                "label" to "自定义",
                "icon" to "profile-icon",
                "keywords" to listOf("奖励"),
                "widgets" to
                    listOf(
                        mapOf(
                            "type" to "button",
                            "row" to 4,
                            "text" to "领取奖励",
                            "action" to "reward",
                        ),
                        mapOf(
                            "type" to "slider",
                            "row" to 14,
                            "state" to "density",
                            "options" to
                                listOf(
                                    mapOf(
                                        "value" to "low",
                                        "label" to "低",
                                        "action" to "density_low",
                                    ),
                                    mapOf(
                                        "value" to "medium",
                                        "label" to "中",
                                        "action" to "density_medium",
                                    ),
                                    mapOf(
                                        "value" to "high",
                                        "label" to "高",
                                        "action" to "density_high",
                                    ),
                                ),
                        ),
                    ),
            ),
        )
        val parsed = MenuConfigParser.parse(menu.saveToString(), languages())
        assertEquals("custom", parsed.search("领取奖励"))
        assertEquals("example.reward", parsed.actions.getValue("reward").permission)
        assertEquals(
            "give Alice stone 1",
            CommandTemplate.render(parsed.actions.getValue("reward").command, "Alice", "id"),
        )
        assertEquals(3, parsed.pages.getValue("custom").widgets.last().options.size)
        val canvas =
            MenuRenderer.render(
                parsed,
                "custom",
                MenuLanguage.CHINESE,
                MenuTheme.DARK,
                { "medium" },
                { it },
                { DialogClicks.custom(Key.key("test", it)) },
            )
        assertEquals(
            "action/density_low",
            canvas.hits.single { it.x == 280 && it.row == 14 }.action,
        )
        assertEquals(
            "action/density_high",
            canvas.hits.single { it.x == 426 && it.row == 14 }.action,
        )
    }

    @Test
    fun `all default pages render within the fixed client canvas in both languages and themes`() {
        val menu = MenuRepository.bundled()
        for (language in MenuLanguage.entries) for (theme in MenuTheme.entries) for (page in
            menu.pages.keys) {
            val canvas =
                MenuRenderer.render(
                    menu,
                    page,
                    language,
                    theme,
                    { if (it == "density") "medium" else "true" },
                    { it },
                    { DialogClicks.custom(Key.key("test", it)) },
                )
            checkLines(canvas, "$page $language $theme")
        }
    }

    @Test
    fun `two through eight slider steps align and expose every rail pixel with bounded arrows`() {
        for (count in 2..8) for (selected in -1 until count) {
            val actions = (0 until count).map { "step_$it" }
            val canvas = DialogCanvas { DialogClicks.custom(Key.key("test", it)) }
            canvas.slider(280, 19, selected, "测试", actions)
            for (row in 19..20) for (x in 302 until 422) {
                assertEquals(
                    1,
                    canvas.hits.count {
                        row in it.row until it.row + it.rows && x in it.x until it.x + it.width
                    },
                )
            }
            assertEquals(
                actions.getOrNull(selected - 1),
                canvas.hits.singleOrNull { it.x == 280 }?.action,
            )
            assertEquals(
                if (selected >= 0) actions.getOrNull(selected + 1) else null,
                canvas.hits.singleOrNull { it.x == 426 }?.action,
            )
            checkLines(canvas, "slider $count selected=$selected")
        }
    }

    @Test
    fun `dropdown exposes options only when expanded and preserves canvas in both languages and themes`() {
        val menu = MenuRepository.bundled()
        val widgets = menu.common + menu.pages.getValue("appearance").widgets
        val indices = widgets.indices.filter { widgets[it].kind == WidgetKind.DROPDOWN }
        assertEquals(2, indices.size)
        for (index in indices) {
            val widget = widgets[index]
            for (language in MenuLanguage.entries) for (theme in MenuTheme.entries) for (opened in
                listOf(false, true)) {
                val canvas =
                    MenuRenderer.render(
                        menu,
                        "appearance",
                        language,
                        theme,
                        { if (it == "language") language.id else theme.id },
                        { it },
                        { DialogClicks.custom(Key.key("test", it)) },
                        if (opened) index else -1,
                    )
                assertTrue(canvas.hits.any { it.action == "dropdown/$index" })
                assertEquals(
                    if (opened) 2 else 0,
                    canvas.hits.count {
                        it.action.startsWith("action/language_") ||
                            it.action.startsWith("action/theme_")
                    },
                )
                if (opened)
                    widget.options.forEachIndexed { n, option ->
                        val row = widget.row + 2 + n * 2
                        for (y in row..row + 1) for (x in widget.x until widget.x + 114) {
                            assertEquals(
                                "action/${option.action}",
                                canvas.hits
                                    .last {
                                        y in it.row until it.row + it.rows &&
                                            x in it.x until it.x + it.width
                                    }
                                    .action,
                            )
                        }
                    }
                checkLines(canvas, "dropdown $language $theme $opened")
            }
            val view = MenuDialog.View("appearance")
            assertEquals(index, view.toggleDropdown(index).dropdown)
            assertEquals(-1, view.toggleDropdown(index).toggleDropdown(index).dropdown)
            assertEquals(view, view.toggleDropdown(index).collapsed())
            assertEquals(
                indices.last(),
                view.toggleDropdown(indices.first()).toggleDropdown(indices.last()).dropdown,
            )
        }
    }

    @Test
    fun `dropdown rejects overflow duplicate values and invalid option actions`() {
        for (case in 0..2) {
            val menu = config()
            menu.set(
                "pages.appearance.widgets",
                listOf(
                    mapOf(
                        "type" to "dropdown",
                        "row" to if (case == 0) 25 else 3,
                        "state" to "language",
                        "options" to
                            listOf(
                                mapOf(
                                    "value" to "zh_cn",
                                    "label" to "中文",
                                    "action" to "language_zh_cn",
                                ),
                                mapOf(
                                    "value" to if (case == 1) "ZH_CN" else "en_us",
                                    "label" to "English",
                                    "action" to if (case == 2) "missing" else "language_en_us",
                                ),
                            ),
                    )
                ),
            )
            assertThrows(Exception::class.java) {
                MenuConfigParser.parse(menu.saveToString(), languages())
            }
        }
    }

    private fun checkLines(canvas: DialogCanvas, context: String) {
        var width = 0
        var rows = 1
        for (child in canvas.build().children()) {
            val part = child as TextComponent
            if (part.content() == "\n") {
                assertEquals(DialogCanvas.LINE_WIDTH, width, context)
                width = 0
                rows++
            } else if (part.font() == DialogCanvas.FONT) {
                val code = part.content().single().code
                width +=
                    if (code in 0xE800..0xEC00) code - 0xEA00 else DialogCanvas.glyphWidth(code)
            } else width += DialogCanvas.textWidth(part.content())
            assertTrue(width in 0..DialogCanvas.LINE_WIDTH, "$context x=$width")
        }
        assertEquals(DialogCanvas.LINE_WIDTH, width, context)
        assertEquals(29, rows, context)
    }
}
