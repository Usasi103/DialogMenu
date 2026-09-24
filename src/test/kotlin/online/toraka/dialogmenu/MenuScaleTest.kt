package online.toraka.dialogmenu

import java.nio.file.Files
import java.nio.file.Path
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MenuScaleTest {
    @Test
    fun `boss pages cap the rendered width without changing player preferences`() {
        val catalog =
            MenuCatalogParser.parse(
                MenuRepository.resource("catalog/config.yml"),
                CatalogRepository.defaults.associateWith {
                    MenuRepository.resource("catalog/menus/$it.yml")
                },
            )
        for (template in catalog.menus.getValue("demo-boss").canvas.values) {
            val canvas =
                TemplateRenderer.render(template, template.values(emptyMap()), { it }) {
                    ClickEvent.custom(Key.key("test", it))
                }
            for (preference in MenuScale.entries) {
                val scale = canvas.supportedScale(template.displayScale(preference))
                assertTrue(scale.percent <= 75)
                assertTrue(template.width * scale.factor <= 414f)
                assertEquals(
                    if (preference == MenuScale.SMALL) preference else MenuScale.MEDIUM,
                    scale,
                )
                canvas.build(scale)
            }
        }
        val source = MenuRepository.resource("templates/boss-intro.yml")
        assertThrows(Exception::class.java) {
            TemplateParser.parse("bad", source.replace("MaxScale: 75", "MaxScale: 80"))
        }
    }

    @Test
    fun `text actions remain clickable and external fonts fall back without changing preference`() {
        val actions = mutableSetOf<String>()
        val canvas = DialogCanvas {
            actions += it
            ClickEvent.custom(Key.key("test", it))
        }
        canvas.text(10, 2, "Click me", action = "text")
        canvas.build(MenuScale.SMALL)
        assertEquals(setOf("text"), actions)
        assertEquals(MenuScale.SMALL, canvas.supportedScale(MenuScale.SMALL))
        canvas.sprite(0, 0, DialogCanvas.Skin(0xE000, 10, 1, Key.key("custom:external")))
        assertEquals(MenuScale.NORMAL, canvas.supportedScale(MenuScale.SMALL))
        assertEquals(29, MenuScale.NORMAL.rows(29))
    }

    @Test
    fun `scale binding keeps old menus valid and rejects unknown choices`() {
        val config = MenuRepository.resource("simple/config.yml")
        val appearance = MenuRepository.resource("simple/menus/appearance.yml")
        fun parse(source: String) =
            SimpleMenuParser.parse(config) {
                if (it == "appearance") source else MenuRepository.resource("simple/menus/$it.yml")
            }
        val menu = parse(appearance)
        val widget = menu.pages.getValue("appearance").widgets.single { it.state == "menu-scale" }
        assertEquals(listOf("50", "75", "100"), widget.options.map { it.value })
        assertEquals(
            listOf("menu-scale:50", "menu-scale:75", "menu-scale:100"),
            widget.options.map { menu.actions.getValue(it.action).value },
        )
        assertThrows(Exception::class.java) {
            parse(appearance.replace("\"75\": \"75%\"", "\"42\": \"42%\""))
        }
        assertEquals(MenuScale.NORMAL, MenuScale.parse(null))
        assertEquals(MenuScale.NORMAL, MenuScale.parse("unknown"))
    }

    @Test
    fun `all canvas menus preserve action identities and export real client scale fixtures`() {
        val catalog =
            MenuCatalogParser.parse(
                MenuRepository.resource("catalog/config.yml"),
                CatalogRepository.defaults.associateWith {
                    MenuRepository.resource("catalog/menus/$it.yml")
                },
            )
        val output =
            Path.of(System.getProperty("user.home"), ".gradle-builds", "DialogMenu", "scale-probes")
        Files.createDirectories(output)
        val index = mutableListOf<String>()
        fun export(
            name: String,
            canvas: DialogCanvas,
            width: Int,
            rows: Int,
            hide: Boolean,
            recorded: MutableSet<String>,
        ) {
            val original = canvas.build()
            val expected = recorded.toSet()
            assertEquals(original, canvas.build(MenuScale.NORMAL))
            for (scale in listOf(MenuScale.SMALL, MenuScale.MEDIUM)) {
                recorded.clear()
                val component = canvas.build(scale)
                assertEquals(expected, recorded, "$name ${scale.id} action identities")
                val id = "$name-${scale.id}"
                Files.writeString(
                    output.resolve("$id.json"),
                    GsonComponentSerializer.gson().serialize(component),
                )
                Files.writeString(
                    output.resolve("$id.hits"),
                    canvas.hits.joinToString("\n") {
                        "${scale.pixel(it.x.toFloat())},${scale.y(it.row)},${scale.pixel((it.x + it.width).toFloat())},${scale.y(it.row + it.rows)},${it.action}"
                    },
                )
                index += "$id\t${scale.bodyWidth(width, hide)}\t${scale.rows(rows)}"
            }
        }
        val menu = catalog.menus.getValue("demo-settings").settings!!
        for (theme in MenuTheme.entries) for (language in MenuLanguage.entries) {
            for (page in menu.pages.values) {
                val actions = linkedSetOf<String>()
                val click: (String) -> ClickEvent<*> = {
                    actions += it
                    ClickEvent.custom(Key.key("test", it))
                }
                val canvas =
                    MenuRenderer.render(
                        menu,
                        page.id,
                        language,
                        theme,
                        { if (it == "menu-scale") "75" else "true" },
                        { it.replace("%vault_eco_balance_formatted%", "3,030⛂") },
                        click,
                    )
                export(
                    "${page.id}-${theme.name}-${language.id}",
                    canvas,
                    DialogCanvas.WIDTH,
                    DialogCanvas.ROWS,
                    menu.hideFocus,
                    actions,
                )
                (menu.common + page.widgets).forEachIndexed { dropdown, widget ->
                    if (widget.kind == WidgetKind.DROPDOWN) {
                        actions.clear()
                        val expanded =
                            MenuRenderer.render(
                                menu,
                                page.id,
                                language,
                                theme,
                                { if (it == "menu-scale") "75" else "true" },
                                { it },
                                click,
                                dropdown,
                            )
                        export(
                            "${page.id}-dropdown$dropdown-${theme.name}-${language.id}",
                            expanded,
                            DialogCanvas.WIDTH,
                            DialogCanvas.ROWS,
                            menu.hideFocus,
                            actions,
                        )
                    }
                }
            }
        }
        for ((id, template) in catalog.templates) {
            val actions = linkedSetOf<String>()
            val canvas =
                TemplateRenderer.render(
                    template,
                    template.values(emptyMap()),
                    { TemplateRenderer.expand(it, template.values(emptyMap()), "测试玩家", "uuid") },
                ) {
                    actions += it
                    ClickEvent.custom(Key.key("test", it))
                }
            export(
                id.replace('/', '_'),
                canvas,
                template.width,
                template.rows,
                template.hideFocus,
                actions,
            )
        }
        Files.writeString(output.resolve("index.tsv"), index.joinToString("\n"))
        assertTrue(index.size >= 60)
    }
}
