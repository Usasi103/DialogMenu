package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MenuCatalogTest {

    @TempDir Path directory;

    private final String config = MenuRepository.resource("catalog/config.yml");

    private static Map<String, String> defaults() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String id : Kt.plus(CatalogRepository.defaults(), List.of("settings"))) {
            sources.put(id, MenuRepository.resource("catalog/menus/" + id + ".yml"));
        }
        return sources;
    }

    private static TemplateElement single(DialogTemplate template, String id) {
        return Kt.single(template.elements().stream().filter(it -> it.id().equals(id)).toList());
    }

    private static Map<String, String> plus(Map<String, String> sources, String id, String value) {
        Map<String, String> result = new LinkedHashMap<>(sources);
        result.put(id, value);
        return result;
    }

    @Test
    @DisplayName("settings consolidation preserves every existing page action and translation")
    void settingsConsolidationPreservesEveryExistingPageActionAndTranslation() {
        MenuCatalog catalog = MenuCatalogParser.parse(config, defaults());
        MenuDefinition old =
                SimpleMenuParser.parse(
                        MenuRepository.resource("simple/config.yml"),
                        it -> MenuRepository.resource("simple/menus/" + it + ".yml"));
        MenuDefinition current = Objects.requireNonNull(catalog.menus().get("settings").settings());
        assertEquals("profile", current.defaultPage());
        List<MenuWidget> switches =
                current.pages().values().stream()
                        .flatMap(it -> it.widgets().stream())
                        .filter(it -> it.kind() == WidgetKind.TOGGLE)
                        .toList();
        assertEquals(8, switches.size());
        assertTrue(switches.stream().allMatch(it -> it.toggleStyle() == ToggleStyle.SWITCH));
        Map<String, MenuPage> pages = new LinkedHashMap<>();
        for (Map.Entry<String, MenuPage> entry : current.pages().entrySet()) {
            MenuPage page = entry.getValue();
            pages.put(
                    entry.getKey(),
                    page.withWidgets(
                            page.widgets().stream()
                                    .map(it -> it.withToggleStyle(ToggleStyle.BUTTON))
                                    .toList()));
        }
        assertEquals(old, current.withPages(pages));
        assertEquals(
                Set.of("settings", "demo-settings", "demo-dialogue", "demo-boss", "demo-quests"),
                catalog.menus().keySet());
        assertEquals(Set.of("intro", "confirm"), catalog.menus().get("demo-boss").pages());
        assertFalse(current.demo());
        assertTrue(Objects.requireNonNull(catalog.menus().get("demo-settings").settings()).demo());
        assertEquals("demo-settings", catalog.defaultMenu());
    }

    @Test
    @DisplayName("settings footer defaults to hidden and rejects non boolean switches")
    void settingsFooterDefaultsToHiddenAndRejectsNonBooleanSwitches() {
        Map<String, String> sources = defaults();
        String settings = sources.get("settings");
        assertFalse(parseSettings(sources, settings).showFooter());
        assertFalse(
                parseSettings(sources, settings.replace("ShowFooter: false\n", "")).showFooter());
        assertTrue(
                parseSettings(sources, settings.replace("ShowFooter: false", "ShowFooter: true"))
                        .showFooter());
        assertThrows(
                Exception.class,
                () ->
                        parseSettings(
                                sources,
                                settings.replace("ShowFooter: false", "ShowFooter: 'false'")));
    }

    /** The {@code parse(value)} local function of the footer test. */
    private MenuDefinition parseSettings(Map<String, String> sources, String value) {
        return Objects.requireNonNull(
                MenuCatalogParser.parse(config, plus(sources, "settings", value))
                        .menus()
                        .get("settings")
                        .settings());
    }

    @Test
    @DisplayName("canvas controls retain layout and values while links target menu local pages")
    void canvasControlsRetainLayoutAndValuesWhileLinksTargetMenuLocalPages() {
        MenuCatalog catalog = MenuCatalogParser.parse(config, defaults());
        DialogTemplate intro = catalog.templates().get("demo-boss/intro");
        DialogTemplate confirm = catalog.templates().get("demo-boss/confirm");
        assertEquals("template: demo-boss/confirm", Kt.last(single(intro, "start").actions()));
        assertEquals("template: demo-boss/intro", Kt.last(single(confirm, "cancel").actions()));
        assertEquals(
                "hard",
                intro.values(confirm.values(Map.of("difficulty", "hard"))).get("difficulty"));
        DialogTemplate old =
                TemplateParser.parse(
                        "boss-intro", MenuRepository.resource("templates/boss-intro.yml"));
        assertEquals(
                old.elements().stream().map(it -> it.withActions(Collections.emptyList())).toList(),
                intro.elements().stream()
                        .map(it -> it.withActions(Collections.emptyList()))
                        .toList());
        assertEquals(old.background(), intro.background());
        assertEquals(
                "template: demo-boss/intro",
                Kt.last(
                        single(catalog.templates().get("demo-dialogue/main"), "continue")
                                .actions()));
    }

    @Test
    @DisplayName("copied menus isolate same page IDs and local navigation")
    void copiedMenusIsolateSamePageIDsAndLocalNavigation() {
        Map<String, String> sources = new LinkedHashMap<>(defaults());
        sources.put("other-boss", sources.get("demo-boss").replace("夜巡者", "新首领"));
        MenuCatalog catalog = MenuCatalogParser.parse(config, sources);
        DialogTemplate other = catalog.templates().get("other-boss/intro");
        assertEquals("template: other-boss/confirm", Kt.last(single(other, "start").actions()));
        assertEquals(
                "template: demo-boss/confirm",
                Kt.last(single(catalog.templates().get("demo-boss/intro"), "start").actions()));
        MenuDialog.View view = new MenuDialog.View("intro", -1, "other-boss");
        assertEquals("other-boss", view.toggleDropdown(2).collapsed().menu());
    }

    @Test
    @DisplayName("new installation creates four complete menus and preserves edits on restart")
    void newInstallationCreatesFourCompleteMenusAndPreservesEditsOnRestart() throws IOException {
        CatalogRepository.exportIfNew(directory.toFile());
        assertTrue(CatalogRepository.selected(directory.toFile()));
        assertEquals(
                4, Objects.requireNonNull(directory.resolve("menus").toFile().listFiles()).length);
        assertFalse(directory.resolve("menus/settings.yml").toFile().exists());
        assertTrue(directory.resolve("menus/demo-settings.yml").toFile().exists());
        assertFalse(directory.resolve("templates").toFile().exists());
        CatalogRepository repository = new CatalogRepository(directory.toFile());
        repository.install(repository.read());
        Path file = directory.resolve("menus/demo-dialogue.yml");
        Files.writeString(file, Files.readString(file).replace("守门人", "我的 NPC"));
        CatalogRepository.exportIfNew(directory.toFile());
        assertTrue(Files.readString(file).contains("我的 NPC"));
        CatalogRepository restarted = new CatalogRepository(directory.toFile());
        restarted.install(restarted.read());
        assertTrue(
                Objects.requireNonNull(restarted.current())
                        .templates()
                        .get("demo-dialogue/main")
                        .title()
                        .contains("我的 NPC"));
    }

    @Test
    @DisplayName("invalid file or link never replaces installed catalog")
    void invalidFileOrLinkNeverReplacesInstalledCatalog() throws IOException {
        CatalogRepository.exportIfNew(directory.toFile());
        CatalogRepository repository = new CatalogRepository(directory.toFile());
        repository.install(repository.read());
        MenuCatalog old = repository.current();
        Path file = directory.resolve("menus/demo-boss.yml");
        String source = Files.readString(file);
        Files.writeString(file, source.replace("page: confirm", "page: missing"));
        assertThrows(Exception.class, () -> repository.install(repository.read()));
        assertSame(old, repository.current());
        Files.writeString(file, source + "Unexpected: true\n");
        assertThrows(Exception.class, () -> repository.install(repository.read()));
        assertSame(old, repository.current());
        Files.writeString(file, source);
        repository.install(repository.read());
        assertEquals(old, repository.current());
    }

    @Test
    @DisplayName("legacy installation stays byte identical and invalid default menu is rejected")
    void legacyInstallationStaysByteIdenticalAndInvalidDefaultMenuIsRejected() throws IOException {
        Path old = directory.resolve("config.yml");
        Files.writeString(old, MenuRepository.resource("simple/config.yml"));
        byte[] bytes = Files.readAllBytes(old);
        CatalogRepository.exportIfNew(directory.toFile());
        assertArrayEquals(bytes, Files.readAllBytes(old));
        assertFalse(CatalogRepository.selected(directory.toFile()));
        assertThrows(
                Exception.class,
                () ->
                        MenuCatalogParser.parse(
                                config.replace(
                                        "DefaultMenu: demo-settings", "DefaultMenu: missing"),
                                defaults()));
        assertThrows(
                Exception.class,
                () ->
                        MenuCatalogParser.parse(
                                config, plus(defaults(), "../bad", defaults().get("demo-boss"))));
    }
}
