package online.toraka.dialogmenu

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MenuCatalogTest {
    @TempDir lateinit var directory: Path
    private val config = MenuRepository.resource("catalog/config.yml")

    private fun defaults() =
        CatalogRepository.defaults.associateWith {
            MenuRepository.resource("catalog/menus/$it.yml")
        }

    @Test
    fun `settings consolidation preserves every existing page action and translation`() {
        val catalog = MenuCatalogParser.parse(config, defaults())
        val old =
            SimpleMenuParser.parse(MenuRepository.resource("simple/config.yml")) {
                MenuRepository.resource("simple/menus/$it.yml")
            }
        val current = catalog.menus.getValue("settings").settings!!
        val switches =
            current.pages.values.flatMap { it.widgets }.filter { it.kind == WidgetKind.TOGGLE }
        assertEquals(8, switches.size)
        assertTrue(switches.all { it.toggleStyle == ToggleStyle.SWITCH })
        assertEquals(
            old,
            current.copy(
                pages =
                    current.pages.mapValues { (_, page) ->
                        page.copy(
                            widgets = page.widgets.map { it.copy(toggleStyle = ToggleStyle.BUTTON) }
                        )
                    }
            ),
        )
        assertEquals(
            setOf("settings", "demo-dialogue", "demo-boss", "demo-quests"),
            catalog.menus.keys,
        )
        assertEquals(setOf("intro", "confirm"), catalog.menus.getValue("demo-boss").pages)
        assertEquals("settings", catalog.defaultMenu)
    }

    @Test
    fun `settings footer defaults to hidden and rejects non boolean switches`() {
        val sources = defaults()
        val settings = sources.getValue("settings")
        fun parse(value: String) =
            MenuCatalogParser.parse(config, sources + ("settings" to value))
                .menus
                .getValue("settings")
                .settings!!
        assertFalse(parse(settings).showFooter)
        assertFalse(parse(settings.replace("ShowFooter: false\n", "")).showFooter)
        assertTrue(parse(settings.replace("ShowFooter: false", "ShowFooter: true")).showFooter)
        assertThrows(Exception::class.java) {
            parse(settings.replace("ShowFooter: false", "ShowFooter: 'false'"))
        }
    }

    @Test
    fun `canvas controls retain layout and values while links target menu local pages`() {
        val catalog = MenuCatalogParser.parse(config, defaults())
        val intro = catalog.templates.getValue("demo-boss/intro")
        val confirm = catalog.templates.getValue("demo-boss/confirm")
        assertEquals(
            "template: demo-boss/confirm",
            intro.elements.single { it.id == "start" }.actions.last(),
        )
        assertEquals(
            "template: demo-boss/intro",
            confirm.elements.single { it.id == "cancel" }.actions.last(),
        )
        assertEquals(
            "hard",
            intro.values(confirm.values(mapOf("difficulty" to "hard")))["difficulty"],
        )
        val old =
            TemplateParser.parse("boss-intro", MenuRepository.resource("templates/boss-intro.yml"))
        assertEquals(
            old.elements.map { it.copy(actions = emptyList()) },
            intro.elements.map { it.copy(actions = emptyList()) },
        )
        assertEquals(old.background, intro.background)
        assertEquals(
            "template: demo-boss/intro",
            catalog.templates
                .getValue("demo-dialogue/main")
                .elements
                .single { it.id == "continue" }
                .actions
                .last(),
        )
    }

    @Test
    fun `copied menus isolate same page IDs and local navigation`() {
        val sources = defaults().toMutableMap()
        sources["other-boss"] = sources.getValue("demo-boss").replace("夜巡者", "新首领")
        val catalog = MenuCatalogParser.parse(config, sources)
        val other = catalog.templates.getValue("other-boss/intro")
        assertEquals(
            "template: other-boss/confirm",
            other.elements.single { it.id == "start" }.actions.last(),
        )
        assertEquals(
            "template: demo-boss/confirm",
            catalog.templates
                .getValue("demo-boss/intro")
                .elements
                .single { it.id == "start" }
                .actions
                .last(),
        )
        val view = MenuDialog.View("intro", menu = "other-boss")
        assertEquals("other-boss", view.toggleDropdown(2).collapsed().menu)
    }

    @Test
    fun `new installation creates four complete menus and preserves edits on restart`() {
        CatalogRepository.exportIfNew(directory.toFile())
        assertTrue(CatalogRepository.selected(directory.toFile()))
        assertEquals(4, directory.resolve("menus").toFile().listFiles()!!.size)
        assertFalse(directory.resolve("templates").toFile().exists())
        val repository = CatalogRepository(directory.toFile())
        repository.install(repository.read())
        val file = directory.resolve("menus/demo-dialogue.yml").toFile()
        file.writeText(file.readText().replace("守门人", "我的 NPC"))
        CatalogRepository.exportIfNew(directory.toFile())
        assertTrue(file.readText().contains("我的 NPC"))
        val restarted = CatalogRepository(directory.toFile())
        restarted.install(restarted.read())
        assertTrue(
            restarted.current!!.templates.getValue("demo-dialogue/main").title.contains("我的 NPC")
        )
    }

    @Test
    fun `invalid file or link never replaces installed catalog`() {
        CatalogRepository.exportIfNew(directory.toFile())
        val repository = CatalogRepository(directory.toFile())
        repository.install(repository.read())
        val old = repository.current
        val file = directory.resolve("menus/demo-boss.yml").toFile()
        val source = file.readText()
        file.writeText(source.replace("page: confirm", "page: missing"))
        assertThrows(Exception::class.java) { repository.install(repository.read()) }
        assertSame(old, repository.current)
        file.writeText(source + "Unexpected: true\n")
        assertThrows(Exception::class.java) { repository.install(repository.read()) }
        assertSame(old, repository.current)
        file.writeText(source)
        repository.install(repository.read())
        assertEquals(old, repository.current)
    }

    @Test
    fun `legacy installation stays byte identical and invalid default menu is rejected`() {
        val old = directory.resolve("config.yml").toFile()
        old.writeText(MenuRepository.resource("simple/config.yml"))
        val bytes = old.readBytes()
        CatalogRepository.exportIfNew(directory.toFile())
        assertArrayEquals(bytes, old.readBytes())
        assertFalse(CatalogRepository.selected(directory.toFile()))
        assertThrows(Exception::class.java) {
            MenuCatalogParser.parse(
                config.replace("DefaultMenu: settings", "DefaultMenu: missing"),
                defaults(),
            )
        }
        assertThrows(Exception::class.java) {
            MenuCatalogParser.parse(
                config,
                defaults() + ("../bad" to defaults().getValue("demo-boss")),
            )
        }
    }
}
