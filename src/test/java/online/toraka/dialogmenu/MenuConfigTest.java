package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MenuConfigTest {
    @TempDir Path directory;

    private static final Function<String, ClickEvent> CLICK =
            action -> DialogClicks.custom(Key.key("test", action));

    private String resource(String name) {
        try (InputStream input =
                Objects.requireNonNull(getClass().getResourceAsStream("/" + name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private Map<MenuLanguage, String> languages() {
        Map<MenuLanguage, String> languages = new LinkedHashMap<>();
        for (MenuLanguage language : MenuLanguage.values()) {
            languages.put(language, resource("languages/" + language.id() + ".yml"));
        }
        return languages;
    }

    private YamlConfiguration config() throws Exception {
        YamlConfiguration menu = new YamlConfiguration();
        menu.loadFromString(resource("menu.yml"));
        return menu;
    }

    @Test
    @DisplayName(
            "failed reload preserves complete snapshot and user files while successful reload replaces all files")
    void failedReloadPreservesCompleteSnapshotAndUserFilesWhileSuccessfulReloadReplacesAllFiles()
            throws Exception {
        Files.writeString(directory.resolve("menu.yml"), resource("menu.yml"));
        MenuRepository repository = new MenuRepository(directory.toFile());
        repository.initialize();
        File menuFile = directory.resolve("menu.yml").toFile();
        File langFile = directory.resolve("languages/zh_cn.yml").toFile();
        MenuDefinition original = repository.current();
        String source = Files.readString(menuFile.toPath());
        assertTrue(source.startsWith("# DialogMenu"));
        Files.writeString(
                langFile.toPath(), Files.readString(langFile.toPath()).replace("玩家设置", "我的菜单"));
        Files.writeString(menuFile.toPath(), "pages: [\n");
        assertThrows(Exception.class, () -> repository.reload());
        assertSame(original, repository.current());
        assertEquals("pages: [\n", Files.readString(menuFile.toPath()));
        Files.writeString(
                menuFile.toPath(),
                source.replace("default-page: \"profile\"", "default-page: \"particles\""));
        repository.reload();
        assertEquals("particles", repository.current().defaultPage());
        assertEquals("我的菜单", repository.current().text(MenuLanguage.CHINESE, "$" + "menu.title"));
        MenuRepository next = new MenuRepository(directory.toFile());
        next.initialize();
        assertEquals("particles", next.current().defaultPage());
        assertEquals(
                Files.readString(menuFile.toPath()),
                source.replace("default-page: \"profile\"", "default-page: \"particles\""));
    }

    @Test
    @DisplayName(
            "invalid actions coordinates missing text and overlapping click targets are rejected")
    void invalidActionsCoordinatesMissingTextAndOverlappingClickTargetsAreRejected()
            throws Exception {
        List<Consumer<YamlConfiguration>> edits =
                Kt.listOf(
                        menu -> menu.set("default-page", "missing"),
                        menu -> menu.set("actions.particles.type", "unknown"),
                        menu -> menu.set("actions.particles.command", "say %untrusted_papi%"),
                        menu -> menu.set("actions.particles.command", "say {query}"),
                        menu -> menu.set("actions.particles.command", "say first\nstop"),
                        menu -> menu.set("pages.particles.label", "$" + "missing_text"),
                        menu -> menu.set("states.particles", "unknown-state"),
                        menu -> menu.set("navigation.row", 28),
                        menu -> menu.set("navigation.rou", 3),
                        menu -> menu.set("hide-focus-outline", "typo"),
                        menu -> menu.set("actions.particles.close", "typo"),
                        menu ->
                                menu.set(
                                        "pages.particles.widgets",
                                        Kt.listOf(
                                                Kt.mapOf(
                                                        "type", "button", "row", 28, "text", "Bad",
                                                        "action", "close"))),
                        menu ->
                                menu.set(
                                        "pages.particles.widgets",
                                        Kt.listOf(
                                                Kt.mapOf(
                                                        "type", "button", "x", 0, "row", 3, "text",
                                                        "Overlap", "action", "close"))));
        for (int index = 0; index < edits.size(); index++) {
            YamlConfiguration menu = config();
            edits.get(index).accept(menu);
            assertThrows(
                    Exception.class,
                    () -> MenuConfigParser.parse(menu.saveToString(), languages()),
                    "case " + index);
        }
    }

    @Test
    @DisplayName(
            "duplicate YAML keys are rejected and command replacements do not expand recursively")
    void duplicateYamlKeysAreRejectedAndCommandReplacementsDoNotExpandRecursively() {
        assertThrows(
                Exception.class,
                () -> MenuConfigParser.parse(resource("menu.yml") + "\nversion: 1\n", languages()));
        assertEquals(
                "say {uuid} abc", CommandTemplate.render("say {player} {uuid}", "{uuid}", "abc"));
    }

    @Test
    @DisplayName(
            "custom page button console permission and three step slider can be configured without source changes")
    void customPageButtonConsolePermissionAndThreeStepSliderCanBeConfiguredWithoutSourceChanges()
            throws Exception {
        YamlConfiguration menu = config();
        menu.set(
                "actions.reward",
                Kt.mapOf(
                        "type",
                        "console-command",
                        "command",
                        "give {player} stone 1",
                        "permission",
                        "example.reward",
                        "close",
                        true));
        menu.set(
                "pages.custom",
                Kt.mapOf(
                        "label",
                        "自定义",
                        "icon",
                        "profile-icon",
                        "keywords",
                        Kt.listOf("奖励"),
                        "widgets",
                        Kt.listOf(
                                Kt.mapOf(
                                        "type", "button", "row", 4, "text", "领取奖励", "action",
                                        "reward"),
                                Kt.mapOf(
                                        "type",
                                        "slider",
                                        "row",
                                        14,
                                        "state",
                                        "density",
                                        "options",
                                        Kt.listOf(
                                                Kt.mapOf(
                                                        "value",
                                                        "low",
                                                        "label",
                                                        "低",
                                                        "action",
                                                        "density_low"),
                                                Kt.mapOf(
                                                        "value",
                                                        "medium",
                                                        "label",
                                                        "中",
                                                        "action",
                                                        "density_medium"),
                                                Kt.mapOf(
                                                        "value",
                                                        "high",
                                                        "label",
                                                        "高",
                                                        "action",
                                                        "density_high"))))));
        MenuDefinition parsed = MenuConfigParser.parse(menu.saveToString(), languages());
        assertEquals("custom", parsed.search("领取奖励"));
        assertEquals("example.reward", Kt.getValue(parsed.actions(), "reward").permission());
        assertEquals(
                "give Alice stone 1",
                CommandTemplate.render(
                        Kt.getValue(parsed.actions(), "reward").command(), "Alice", "id"));
        assertEquals(3, Kt.last(Kt.getValue(parsed.pages(), "custom").widgets()).options().size());
        DialogCanvas canvas =
                MenuRenderer.render(
                        parsed,
                        "custom",
                        MenuLanguage.CHINESE,
                        MenuTheme.DARK,
                        state -> "medium",
                        text -> text,
                        CLICK);
        assertEquals(
                "action/density_low",
                single(canvas.hits(), hit -> hit.x() == 280 && hit.row() == 14).action());
        assertEquals(
                "action/density_high",
                single(canvas.hits(), hit -> hit.x() == 426 && hit.row() == 14).action());
    }

    @Test
    @DisplayName(
            "all default pages render within the fixed client canvas in both languages and themes")
    void allDefaultPagesRenderWithinTheFixedClientCanvasInBothLanguagesAndThemes() {
        MenuDefinition menu = MenuRepository.bundled();
        for (MenuLanguage language : MenuLanguage.values()) {
            for (MenuTheme theme : MenuTheme.values()) {
                for (String page : menu.pages().keySet()) {
                    DialogCanvas canvas =
                            MenuRenderer.render(
                                    menu,
                                    page,
                                    language,
                                    theme,
                                    state -> "density".equals(state) ? "medium" : "true",
                                    text -> text,
                                    CLICK);
                    checkLines(canvas, page + " " + language + " " + theme);
                }
            }
        }
    }

    @Test
    @DisplayName(
            "two through eight slider steps align and expose every rail pixel with bounded arrows")
    void twoThroughEightSliderStepsAlignAndExposeEveryRailPixelWithBoundedArrows() {
        for (int count = 2; count <= 8; count++) {
            for (int selected = -1; selected < count; selected++) {
                List<String> actions = new ArrayList<>();
                for (int step = 0; step < count; step++) {
                    actions.add("step_" + step);
                }
                DialogCanvas canvas = new DialogCanvas(CLICK);
                canvas.slider(280, 19, selected, "测试", actions);
                for (int row = 19; row <= 20; row++) {
                    for (int x = 302; x < 422; x++) {
                        int y = row;
                        int column = x;
                        assertEquals(1, count(canvas.hits(), hit -> covers(hit, y, column)));
                    }
                }
                DialogCanvas.Hit left = singleOrNull(canvas.hits(), hit -> hit.x() == 280);
                assertEquals(
                        Kt.getOrNull(actions, selected - 1), left == null ? null : left.action());
                DialogCanvas.Hit right = singleOrNull(canvas.hits(), hit -> hit.x() == 426);
                assertEquals(
                        selected >= 0 ? Kt.getOrNull(actions, selected + 1) : null,
                        right == null ? null : right.action());
                checkLines(canvas, "slider " + count + " selected=" + selected);
            }
        }
    }

    @Test
    @DisplayName(
            "dropdown exposes options only when expanded and preserves canvas in both languages and themes")
    void dropdownExposesOptionsOnlyWhenExpandedAndPreservesCanvasInBothLanguagesAndThemes() {
        MenuDefinition menu = MenuRepository.bundled();
        List<MenuWidget> widgets =
                Kt.plus(menu.common(), Kt.getValue(menu.pages(), "appearance").widgets());
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < widgets.size(); index++) {
            if (widgets.get(index).kind() == WidgetKind.DROPDOWN) {
                indices.add(index);
            }
        }
        assertEquals(2, indices.size());
        for (int index : indices) {
            MenuWidget widget = widgets.get(index);
            for (MenuLanguage language : MenuLanguage.values()) {
                for (MenuTheme theme : MenuTheme.values()) {
                    for (boolean opened : new boolean[] {false, true}) {
                        DialogCanvas canvas =
                                MenuRenderer.render(
                                        menu,
                                        "appearance",
                                        language,
                                        theme,
                                        state ->
                                                "language".equals(state)
                                                        ? language.id()
                                                        : theme.id(),
                                        text -> text,
                                        CLICK,
                                        opened ? index : -1);
                        assertTrue(
                                canvas.hits().stream()
                                        .anyMatch(hit -> hit.action().equals("dropdown/" + index)));
                        assertEquals(
                                opened ? 2 : 0,
                                count(
                                        canvas.hits(),
                                        hit ->
                                                hit.action().startsWith("action/language_")
                                                        || hit.action()
                                                                .startsWith("action/theme_")));
                        if (opened) {
                            for (int n = 0; n < widget.options().size(); n++) {
                                MenuOption option = widget.options().get(n);
                                int row = widget.row() + 2 + n * 2;
                                for (int y = row; y <= row + 1; y++) {
                                    for (int x = widget.x(); x < widget.x() + 114; x++) {
                                        int line = y;
                                        int column = x;
                                        assertEquals(
                                                "action/" + option.action(),
                                                last(
                                                                canvas.hits(),
                                                                hit -> covers(hit, line, column))
                                                        .action());
                                    }
                                }
                            }
                        }
                        checkLines(canvas, "dropdown " + language + " " + theme + " " + opened);
                    }
                }
            }
            MenuDialog.View view = new MenuDialog.View("appearance");
            assertEquals(index, view.toggleDropdown(index).dropdown());
            assertEquals(-1, view.toggleDropdown(index).toggleDropdown(index).dropdown());
            assertEquals(view, view.toggleDropdown(index).collapsed());
            assertEquals(
                    (int) Kt.last(indices),
                    view.toggleDropdown(Kt.first(indices))
                            .toggleDropdown(Kt.last(indices))
                            .dropdown());
        }
    }

    @Test
    @DisplayName("dropdown rejects overflow duplicate values and invalid option actions")
    void dropdownRejectsOverflowDuplicateValuesAndInvalidOptionActions() throws Exception {
        for (int variant = 0; variant <= 2; variant++) {
            YamlConfiguration menu = config();
            menu.set(
                    "pages.appearance.widgets",
                    Kt.listOf(
                            Kt.mapOf(
                                    "type",
                                    "dropdown",
                                    "row",
                                    variant == 0 ? 25 : 3,
                                    "state",
                                    "language",
                                    "options",
                                    Kt.listOf(
                                            Kt.mapOf(
                                                    "value",
                                                    "zh_cn",
                                                    "label",
                                                    "中文",
                                                    "action",
                                                    "language_zh_cn"),
                                            Kt.mapOf(
                                                    "value",
                                                    variant == 1 ? "ZH_CN" : "en_us",
                                                    "label",
                                                    "English",
                                                    "action",
                                                    variant == 2
                                                            ? "missing"
                                                            : "language_en_us")))));
            assertThrows(
                    Exception.class,
                    () -> MenuConfigParser.parse(menu.saveToString(), languages()));
        }
    }

    private static void checkLines(DialogCanvas canvas, String context) {
        int width = 0;
        int rows = 1;
        for (Component child : canvas.build().children()) {
            TextComponent part = (TextComponent) child;
            if (part.content().equals("\n")) {
                assertEquals(DialogCanvas.LINE_WIDTH, width, context);
                width = 0;
                rows++;
            } else if (DialogCanvas.FONT.equals(part.font())) {
                int code = singleCode(part.content());
                width +=
                        code >= 0xE800 && code <= 0xEC00
                                ? code - 0xEA00
                                : DialogCanvas.glyphWidth(code);
            } else {
                width += DialogCanvas.textWidth(part.content());
            }
            assertTrue(width >= 0 && width <= DialogCanvas.LINE_WIDTH, context + " x=" + width);
        }
        assertEquals(DialogCanvas.LINE_WIDTH, width, context);
        assertEquals(29, rows, context);
    }

    /** {@code row in hit.row until hit.row + hit.rows && x in hit.x until hit.x + hit.width}. */
    private static boolean covers(DialogCanvas.Hit hit, int row, int x) {
        return hit.row() <= row
                && row < hit.row() + hit.rows()
                && hit.x() <= x
                && x < hit.x() + hit.width();
    }

    /** Kotlin {@code String.single().code}. */
    private static int singleCode(String text) {
        if (text.isEmpty()) {
            throw new NoSuchElementException("Char sequence is empty.");
        }
        if (text.length() > 1) {
            throw new IllegalArgumentException("Char sequence has more than one element.");
        }
        return text.charAt(0);
    }

    /** Kotlin {@code single { predicate }}. */
    private static <T> T single(List<T> values, Predicate<T> predicate) {
        return Kt.single(values.stream().filter(predicate).toList());
    }

    /** Kotlin {@code singleOrNull { predicate }}. */
    private static <T> T singleOrNull(List<T> values, Predicate<T> predicate) {
        List<T> matches = values.stream().filter(predicate).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** Kotlin {@code last { predicate }}. */
    private static <T> T last(List<T> values, Predicate<T> predicate) {
        return Kt.last(values.stream().filter(predicate).toList());
    }

    /** Kotlin {@code count { predicate }}. */
    private static <T> int count(List<T> values, Predicate<T> predicate) {
        int count = 0;
        for (T value : values) {
            if (predicate.test(value)) {
                count++;
            }
        }
        return count;
    }
}
