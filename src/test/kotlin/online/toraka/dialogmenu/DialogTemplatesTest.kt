package online.toraka.dialogmenu

import java.nio.file.Path
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DialogTemplatesTest {
    @TempDir lateinit var root: Path

    private fun source(id: String) = MenuRepository.resource("templates/$id.yml")

    @Test
    fun titleSizeUsesMeasuredLargerGlyphsAndReservesVerticalSpace() {
        val template = TemplateParser.parse("boss-intro", source("boss-intro"))
        val title = template.elements.single { it.id == "title" }
        assertTrue(title.bold)
        assertEquals(54, DialogCanvas.textWidth("夜巡者", 16, true))
        assertEquals(16, title.textSize)
        assertEquals(3, title.rows)
        assertEquals(51, DialogCanvas.textWidth("夜巡者", 16))
        assertEquals(listOf("夜", "巡", "者"), TemplateRenderer.wrap("夜巡者", 33, 16))
        assertTrue(title.row + title.rows <= template.elements.single { it.id == "subtitle" }.row)
        val component =
            TemplateRenderer.render(template, template.values(emptyMap()), { it }) {
                    DialogClicks.custom(Key.key("test", it))
                }
                .build()
        assertTrue(component.children().any { it.font() == TitleFont.font(16) })
        for (invalid in
            listOf(
                source("boss-intro").replace("FontSize: 16", "FontSize: 25"),
                source("boss-intro").replace("FontSize: 16", "Rows: 2\n    FontSize: 16"),
            )) assertThrows(IllegalArgumentException::class.java) {
            TemplateParser.parse("boss-intro", invalid)
        }
    }

    @Test
    fun customFontSizesAndBoldPreserveTextFlowAndRejectInvalidConfiguration() {
        for (size in 6..24) {
            val template =
                TemplateParser.parse(
                    "boss-intro",
                    source("boss-intro").replace("FontSize: 16", "FontSize: $size"),
                )
            val title = template.elements.single { it.id == "title" }
            assertEquals(TitleFont.lineRows(size), title.rows)
            assertEquals(size, title.textSize)
            assertTrue(
                TemplateRenderer.wrap("夜巡者 ABC 123", 51, size, true).all {
                    DialogCanvas.textWidth(it, size, true) <= 51
                }
            )
            TemplateRenderer.render(template, template.values(emptyMap()), { it }) {
                    DialogClicks.custom(Key.key("test", it))
                }
                .build()
        }
        val legacy =
            TemplateParser.parse(
                "boss-intro",
                source("boss-intro").replace("FontSize: 16", "TextSize: 16"),
            )
        assertEquals(16, legacy.elements.single { it.id == "title" }.textSize)
        for (invalid in
            listOf(
                source("boss-intro").replace("Bold: true", "Bold: 'true'"),
                source("boss-intro").replace("FontSize: 16", "FontSize: 5"),
                source("boss-intro").replace("FontSize: 16", "FontSize: 12.5"),
                source("boss-intro").replace("FontSize: 16", "TextSize: 12\n    FontSize: 16"),
            )) assertThrows(IllegalArgumentException::class.java) {
            TemplateParser.parse("boss-intro", invalid)
        }
    }

    @Test
    fun bundledPagesLinkAndAllDifficultyChoicesMatchTheirHitRegions() {
        val templates =
            TemplateRepository.defaults.associateWith { TemplateParser.parse(it, source(it)) }
        TemplateParser.validateLinks(templates)
        for (template in templates.values) {
            for (difficulty in listOf("normal", "hard", "overload")) {
                val values = template.values(mapOf("difficulty" to difficulty))
                val actions = linkedSetOf<String>()
                val canvas =
                    TemplateRenderer.render(template, values, { it }) {
                        actions += it
                        DialogClicks.custom(Key.key("test", it))
                    }
                val component = canvas.build()
                assertFalse(component.children().isEmpty())
                assertTrue(
                    canvas.hits.all {
                        it.x >= 0 &&
                            it.x + it.width <= template.width &&
                            it.row + it.rows <= template.rows
                    }
                )
                assertEquals(
                    template.elements
                        .filter { it.type == "button" && TemplateRenderer.visible(it, values) }
                        .map { it.id }
                        .toSet(),
                    actions,
                )
                if (template.id == "boss-confirm") {
                    assertTrue(
                        TemplateRenderer.visible(
                            template.elements.first { it.id == "$difficulty-description" },
                            values,
                        )
                    )
                    assertEquals(
                        1,
                        template.elements.count {
                            it.id.endsWith("-description") && TemplateRenderer.visible(it, values)
                        },
                    )
                }
            }
        }
    }

    @Test
    fun invalidCoordinatesLinksCommandsAndOverlappingButtonsAreRejected() {
        val original = source("boss-confirm")
        for (invalid in
            listOf(
                original.replace("Position: [144, 9]", "Position: [999, 9]"),
                original.replace("Position: [144, 11]", "Position: [144, 9]"),
                original.replace("set: difficulty=hard", "set: difficulty=injected"),
                original.replace("message: 已选择难度", "console: cmd {unknown} 已选择难度"),
                original.replace("Rows: 20", "Rows: 21"),
            )) assertThrows(IllegalArgumentException::class.java) {
            TemplateParser.parse("boss-confirm", invalid)
        }
        val template = TemplateParser.parse("boss-confirm", original)
        assertThrows(IllegalArgumentException::class.java) {
            TemplateParser.validateLinks(mapOf(template.id to template))
        }
    }

    @Test
    fun reloadDoesNotOverwriteFilesAndBadSnapshotDoesNotReplaceCurrent() {
        val repository = TemplateRepository(root.toFile())
        repository.initialize()
        val before = repository.current
        val file = root.resolve("templates/boss-confirm.yml").toFile()
        val invalid = file.readText().replace("template: boss-intro", "template: nonexistent")
        file.writeText(invalid)
        assertThrows(IllegalArgumentException::class.java) { repository.read() }
        assertSame(before, repository.current)
        assertEquals(invalid, file.readText())
        file.writeText(source("boss-confirm").replace("开启挑战", "新的挑战"))
        val next = repository.read()
        assertEquals("开启挑战", repository.current.getValue("boss-confirm").title)
        repository.install(next)
        assertEquals("新的挑战", repository.current.getValue("boss-confirm").title)
        val restarted = TemplateRepository(root.toFile())
        restarted.initialize()
        assertEquals("新的挑战", restarted.current.getValue("boss-confirm").title)
    }

    @Test
    fun variablesAreEnumeratedPerPlayerAndSubstitutionsNeverRecurse() {
        val template = TemplateParser.parse("boss-confirm", source("boss-confirm"))
        assertEquals("normal", template.values(mapOf("difficulty" to "not-valid"))["difficulty"])
        assertEquals("hard", template.values(mapOf("difficulty" to "hard"))["difficulty"])
        assertEquals("normal", template.values(emptyMap())["difficulty"])
        assertEquals(
            "Player hard",
            TemplateRenderer.expand(
                "{player} {difficulty}",
                mapOf("difficulty" to "hard"),
                "Player",
                "id",
            ),
        )
        assertEquals(
            "{difficulty}",
            TemplateRenderer.expand(
                "{player}",
                mapOf("difficulty" to "hard"),
                "{difficulty}",
                "id",
            ),
        )
        assertTrue(
            TemplateRenderer.wrap("中文换行测试 text", 27).all { DialogCanvas.textWidth(it) <= 27 }
        )
    }
}
