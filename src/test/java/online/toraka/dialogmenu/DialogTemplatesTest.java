package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DialogTemplatesTest {

    @TempDir Path root;

    private static String source(String id) {
        return MenuRepository.resource("templates/" + id + ".yml");
    }

    private static TemplateElement single(DialogTemplate template, String id) {
        return Kt.single(template.elements().stream().filter(it -> it.id().equals(id)).toList());
    }

    @Test
    void titleSizeUsesMeasuredLargerGlyphsAndReservesVerticalSpace() {
        DialogTemplate template = TemplateParser.parse("boss-intro", source("boss-intro"));
        TemplateElement title = single(template, "title");
        assertTrue(title.bold());
        assertEquals(54, DialogCanvas.textWidth("夜巡者", 16, true));
        assertEquals(16, title.textSize());
        assertEquals(3, title.rows());
        assertEquals(51, DialogCanvas.textWidth("夜巡者", 16));
        assertEquals(List.of("夜", "巡", "者"), TemplateRenderer.wrap("夜巡者", 33, 16));
        assertTrue(title.row() + title.rows() <= single(template, "subtitle").row());
        Component component =
                TemplateRenderer.render(
                                template,
                                template.values(Collections.emptyMap()),
                                it -> it,
                                it -> DialogClicks.custom(Key.key("test", it)))
                        .build();
        assertTrue(
                NativeMenuFontTest.leaves(component).stream()
                        .anyMatch(it -> Objects.equals(it.font(), NativeMenuFont.FONT)));
        assertTrue(
                NativeMenuFontTest.leaves(component).stream()
                        .anyMatch(
                                it ->
                                        NativeMenuFont.POSITION.equals(it.font())
                                                && it.content().contains("\uF028")));
        for (String invalid :
                List.of(
                        source("boss-intro").replace("FontSize: 16", "FontSize: 25"),
                        source("boss-intro")
                                .replace("FontSize: 16", "Rows: 2\n    FontSize: 16"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateParser.parse("boss-intro", invalid));
        }
    }

    @Test
    void customFontSizesAndBoldPreserveTextFlowAndRejectInvalidConfiguration() {
        for (int size = 6; size <= 24; size++) {
            int textSize = size;
            DialogTemplate template =
                    TemplateParser.parse(
                            "boss-intro",
                            source("boss-intro").replace("FontSize: 16", "FontSize: " + size));
            TemplateElement title = single(template, "title");
            assertEquals(TitleFont.lineRows(size), title.rows());
            assertEquals(size, title.textSize());
            assertTrue(
                    TemplateRenderer.wrap("夜巡者 ABC 123", 51, size, true).stream()
                            .allMatch(it -> DialogCanvas.textWidth(it, textSize, true) <= 51));
            TemplateRenderer.render(
                            template,
                            template.values(Collections.emptyMap()),
                            it -> it,
                            it -> DialogClicks.custom(Key.key("test", it)))
                    .build();
        }
        DialogTemplate legacy =
                TemplateParser.parse(
                        "boss-intro", source("boss-intro").replace("FontSize: 16", "TextSize: 16"));
        assertEquals(16, single(legacy, "title").textSize());
        for (String invalid :
                List.of(
                        source("boss-intro").replace("Bold: true", "Bold: 'true'"),
                        source("boss-intro").replace("FontSize: 16", "FontSize: 5"),
                        source("boss-intro").replace("FontSize: 16", "FontSize: 12.5"),
                        source("boss-intro")
                                .replace("FontSize: 16", "TextSize: 12\n    FontSize: 16"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateParser.parse("boss-intro", invalid));
        }
    }

    @Test
    void bundledPagesLinkAndAllDifficultyChoicesMatchTheirHitRegions() {
        Map<String, DialogTemplate> templates = new LinkedHashMap<>();
        for (String id : TemplateRepository.defaults()) {
            templates.put(id, TemplateParser.parse(id, source(id)));
        }
        TemplateParser.validateLinks(templates);
        for (DialogTemplate template : templates.values()) {
            for (String difficulty : List.of("normal", "hard", "overload")) {
                Map<String, String> values = template.values(Map.of("difficulty", difficulty));
                Set<String> actions = new LinkedHashSet<>();
                DialogCanvas canvas =
                        TemplateRenderer.render(
                                template,
                                values,
                                it -> it,
                                it -> {
                                    actions.add(it);
                                    return DialogClicks.custom(Key.key("test", it));
                                });
                Component component = canvas.build();
                assertFalse(component.children().isEmpty());
                assertTrue(
                        canvas.hits().stream()
                                .allMatch(
                                        it ->
                                                it.x() >= 0
                                                        && it.x() + it.width() <= template.width()
                                                        && it.row() + it.rows()
                                                                <= template.rows()));
                assertEquals(
                        template.elements().stream()
                                .filter(
                                        it ->
                                                it.type().equals("button")
                                                        && TemplateRenderer.visible(it, values))
                                .map(TemplateElement::id)
                                .collect(Collectors.toCollection(LinkedHashSet::new)),
                        actions);
                if (template.id().equals("boss-confirm")) {
                    assertTrue(
                            TemplateRenderer.visible(
                                    template.elements().stream()
                                            .filter(
                                                    it ->
                                                            it.id()
                                                                    .equals(
                                                                            difficulty
                                                                                    + "-description"))
                                            .findFirst()
                                            .orElseThrow(),
                                    values));
                    assertEquals(
                            1,
                            template.elements().stream()
                                    .filter(
                                            it ->
                                                    it.id().endsWith("-description")
                                                            && TemplateRenderer.visible(it, values))
                                    .count());
                }
            }
        }
    }

    @Test
    void invalidCoordinatesLinksCommandsAndOverlappingButtonsAreRejected() {
        String original = source("boss-confirm");
        for (String invalid :
                List.of(
                        original.replace("Position: [144, 9]", "Position: [999, 9]"),
                        original.replace("Position: [144, 11]", "Position: [144, 9]"),
                        original.replace("set: difficulty=hard", "set: difficulty=injected"),
                        original.replace("message: 已选择难度", "console: cmd {unknown} 已选择难度"),
                        original.replace("Rows: 20", "Rows: 21"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TemplateParser.parse("boss-confirm", invalid));
        }
        DialogTemplate template = TemplateParser.parse("boss-confirm", original);
        assertThrows(
                IllegalArgumentException.class,
                () -> TemplateParser.validateLinks(Map.of(template.id(), template)));
    }

    @Test
    void reloadDoesNotOverwriteFilesAndBadSnapshotDoesNotReplaceCurrent() throws IOException {
        TemplateRepository repository = new TemplateRepository(root.toFile());
        repository.initialize();
        Map<String, DialogTemplate> before = repository.current();
        Path file = root.resolve("templates/boss-confirm.yml");
        String invalid =
                Files.readString(file).replace("template: boss-intro", "template: nonexistent");
        Files.writeString(file, invalid);
        assertThrows(IllegalArgumentException.class, () -> repository.read());
        assertSame(before, repository.current());
        assertEquals(invalid, Files.readString(file));
        Files.writeString(file, source("boss-confirm").replace("开启挑战", "新的挑战"));
        Map<String, DialogTemplate> next = repository.read();
        assertEquals("开启挑战", repository.current().get("boss-confirm").title());
        repository.install(next);
        assertEquals("新的挑战", repository.current().get("boss-confirm").title());
        TemplateRepository restarted = new TemplateRepository(root.toFile());
        restarted.initialize();
        assertEquals("新的挑战", restarted.current().get("boss-confirm").title());
    }

    @Test
    void variablesAreEnumeratedPerPlayerAndSubstitutionsNeverRecurse() {
        DialogTemplate template = TemplateParser.parse("boss-confirm", source("boss-confirm"));
        assertEquals(
                "normal", template.values(Map.of("difficulty", "not-valid")).get("difficulty"));
        assertEquals("hard", template.values(Map.of("difficulty", "hard")).get("difficulty"));
        assertEquals("normal", template.values(Collections.emptyMap()).get("difficulty"));
        assertEquals(
                "Player hard",
                TemplateRenderer.expand(
                        "{player} {difficulty}", Map.of("difficulty", "hard"), "Player", "id"));
        assertEquals(
                "{difficulty}",
                TemplateRenderer.expand(
                        "{player}", Map.of("difficulty", "hard"), "{difficulty}", "id"));
        assertTrue(
                TemplateRenderer.wrap("中文换行测试 text", 27).stream()
                        .allMatch(it -> DialogCanvas.textWidth(it) <= 27));
    }
}
