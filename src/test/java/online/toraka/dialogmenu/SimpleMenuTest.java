package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimpleMenuTest {
    @TempDir Path directory;

    private static final Function<String, ClickEvent> CLICK =
            action -> DialogClicks.custom(Key.key("test", action));

    private final String config = "Version: 2\nPages: [example]\n";

    private MenuDefinition parse(String page) {
        return SimpleMenuParser.parse(config, id -> trimIndent(page));
    }

    private MenuDefinition bundled() {
        return SimpleMenuParser.parse(
                MenuRepository.resource("simple/config.yml"),
                id -> MenuRepository.resource("simple/menus/" + id + ".yml"));
    }

    @Test
    @DisplayName(
            "fresh install exports per page files and failed multi file reload preserves prior snapshot")
    void freshInstallExportsPerPageFilesAndFailedMultiFileReloadPreservesPriorSnapshot()
            throws Exception {
        MenuRepository repository = new MenuRepository(directory.toFile());
        repository.initialize();
        assertTrue(directory.resolve("config.yml").toFile().isFile());
        assertFalse(directory.resolve("menu.yml").toFile().exists());
        assertFalse(directory.resolve("languages").toFile().exists());
        File appearance = directory.resolve("menus/appearance.yml").toFile();
        File particles = directory.resolve("menus/particles.yml").toFile();
        MenuDefinition original = repository.current();
        String particlesSource = Files.readString(particles.toPath());
        Files.writeString(
                appearance.toPath(),
                Files.readString(appearance.toPath()).replace("界面与语言", "我的界面"));
        Files.writeString(particles.toPath(), "Layout: [\n");
        assertThrows(Exception.class, () -> repository.reload());
        assertSame(original, repository.current());
        assertEquals("Layout: [\n", Files.readString(particles.toPath()));
        Files.writeString(particles.toPath(), particlesSource);
        repository.reload();
        assertEquals(
                "我的界面",
                repository
                        .current()
                        .text(
                                MenuLanguage.CHINESE,
                                Kt.getValue(repository.current().pages(), "appearance").label()));
        MenuRepository restarted = new MenuRepository(directory.toFile());
        restarted.initialize();
        assertEquals(repository.current(), restarted.current());
        appearance.delete();
        MenuDefinition valid = repository.current();
        assertTrue(
                Objects.requireNonNull(
                                assertThrows(Exception.class, () -> repository.reload())
                                        .getMessage())
                        .contains("menus/appearance.yml"));
        assertSame(valid, repository.current());
    }

    @Test
    @DisplayName(
            "proposed language theme and close command sample compiles with automatic placement")
    void proposedLanguageThemeAndCloseCommandSampleCompilesWithAutomaticPlacement() {
        MenuDefinition menu =
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
                        """);
        List<MenuWidget> widgets = Kt.getValue(menu.pages(), "example").widgets();
        List<MenuWidget> dropdowns =
                widgets.stream().filter(widget -> widget.kind() == WidgetKind.DROPDOWN).toList();
        assertEquals(List.of(4, 14), dropdowns.stream().map(MenuWidget::row).toList());
        MenuOption language = Kt.first(Kt.first(dropdowns).options());
        assertEquals("language:zh_cn", Kt.getValue(menu.actions(), language.action()).value());
        assertEquals("Menu theme", menu.text(MenuLanguage.ENGLISH, Kt.last(dropdowns).label()));
        MenuWidget button = single(widgets, widget -> widget.kind() == WidgetKind.BUTTON);
        assertEquals(16, button.row());
        MenuAction action = Kt.getValue(menu.actions(), button.action());
        assertEquals("example.travel", action.permission());
        assertEquals(List.of("close", "command: spawn"), action.reaction().lines());
    }

    @Test
    @DisplayName(
            "custom PAPI dropdown and ordered player console actions retain identity and values")
    void customPapiDropdownAndOrderedPlayerConsoleActionsRetainIdentityAndValues() {
        MenuDefinition menu =
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
                        """);
        MenuWidget widget =
                single(
                        Kt.getValue(menu.pages(), "example").widgets(),
                        candidate -> candidate.kind() == WidgetKind.DROPDOWN);
        assertEquals("%example_mode%", Kt.getValue(menu.states(), widget.state()));
        MenuAction action = Kt.getValue(menu.actions(), Kt.first(widget.options()).action());
        assertEquals("example.mode", action.permission());
        assertEquals(
                List.of("command: mode easy", "console: give {player} stone 1", "refresh"),
                action.reaction().lines());
    }

    @Test
    @DisplayName(
            "bindings reject unknown choices and incompatible types instead of ignoring fields")
    void bindingsRejectUnknownChoicesAndIncompatibleTypesInsteadOfIgnoringFields() {
        String valid =
                "Title: 测试\nLayout: [主题]\nIcons:\n  主题:\n    Type: dropdown\n    Bind: theme\n    Options: {dark: 暗色, light: 亮色}\n";
        for (String source :
                List.of(
                        valid.replace("Bind: theme", "Bind: typo"),
                        valid.replace("light: 亮色", "auto: 自动"),
                        valid.replace("Type: dropdown", "Type: toggle"),
                        valid.replace("Bind: theme", "Bind: theme\n    State: '%custom_state%'"),
                        valid.replace("Bind: theme", "Bind: theme\n    Permission: true"),
                        valid.replace("Bind: theme", "Bind: theme\n    Actions: ['close']"),
                        valid.replace("Type: dropdown", "Typo: dropdown"),
                        valid.replace("Layout: [主题]", "Layout: [主题, 主题]"),
                        valid + "Title: duplicate\n")) {
            assertThrows(Exception.class, () -> parse(source));
        }
        assertThrows(
                Exception.class,
                () ->
                        SimpleMenuParser.parse(
                                "Version: 2\nPages: ['../secrets']",
                                id -> {
                                    throw new IllegalStateException("must not read");
                                }));
    }

    @Test
    @DisplayName("invalid commands action order and overflowing automatic layout fail validation")
    void invalidCommandsActionOrderAndOverflowingAutomaticLayoutFailValidation() {
        for (String action :
                List.of(
                        "command: /spawn",
                        "console: say ok; /op someone",
                        "console: say {query}",
                        "set: mode=easy",
                        "page: missing",
                        "open: other",
                        "sound: NOT A SOUND",
                        "title: a b slow",
                        "unknown: x")) {
            assertThrows(
                    Exception.class,
                    () ->
                            parse(
                                    "Title: 测试\nLayout: [按钮]\nIcons:\n  按钮:\n    Actions: ['"
                                            + action
                                            + "']"));
        }
        // TrMenu order: actions run where they stand, so a refresh may come first.
        parse("Title: 测试\nLayout: [按钮]\nIcons:\n  按钮:\n    Actions: ['refresh', 'command: spawn']");
        List<String> names = new ArrayList<>();
        for (int index = 0; index <= 10; index++) {
            names.add("item" + index);
        }
        List<String> icons = new ArrayList<>();
        for (String name : names) {
            icons.add(
                    "  "
                            + name
                            + ": {Type: dropdown, Bind: theme, Options: {dark: 暗色, light: 亮色}}");
        }
        assertThrows(
                Exception.class,
                () ->
                        parse(
                                "Title: 测试\nLayout: ["
                                        + String.join(", ", names)
                                        + "]\nIcons:\n"
                                        + String.join("\n", icons)));
    }

    @Test
    @DisplayName("all simple pages and expanded dropdowns keep the fixed measured canvas")
    void allSimplePagesAndExpandedDropdownsKeepTheFixedMeasuredCanvas() {
        MenuDefinition menu = bundled();
        for (MenuPage page : menu.pages().values()) {
            for (MenuLanguage language : MenuLanguage.values()) {
                for (MenuTheme theme : MenuTheme.values()) {
                    List<MenuWidget> widgets = Kt.plus(menu.common(), page.widgets());
                    List<Integer> opened = new ArrayList<>();
                    opened.add(-1);
                    for (int index = 0; index < widgets.size(); index++) {
                        if (widgets.get(index).kind() == WidgetKind.DROPDOWN) {
                            opened.add(index);
                        }
                    }
                    for (int dropdown : opened) {
                        DialogCanvas canvas =
                                MenuRenderer.render(
                                        menu,
                                        page.id(),
                                        language,
                                        theme,
                                        state ->
                                                switch (state) {
                                                    case "language" -> language.id();
                                                    case "theme" -> theme.id();
                                                    case "density" -> "medium";
                                                    default -> "true";
                                                },
                                        text -> text,
                                        CLICK,
                                        dropdown);
                        int width = 0;
                        int lines = 1;
                        for (Component child : canvas.build().children()) {
                            TextComponent part = (TextComponent) child;
                            if (part.content().equals("\n")) {
                                assertEquals(452, width);
                                width = 0;
                                lines++;
                            } else if (DialogCanvas.FONT.equals(part.font())) {
                                int code = singleCode(part.content());
                                width +=
                                        code >= 0xE800 && code <= 0xEC00
                                                ? code - 0xEA00
                                                : DialogCanvas.glyphWidth(code);
                            } else {
                                width += DialogCanvas.textWidth(part.content());
                            }
                            assertTrue(
                                    width >= 0 && width <= 452,
                                    page.id() + " " + language + " " + theme + " " + dropdown
                                            + " width=" + width);
                        }
                        assertEquals(29, lines);
                        assertEquals(452, width);
                        if (dropdown >= 0) {
                            MenuWidget widget = widgets.get(dropdown);
                            for (int index = 0; index < widget.options().size(); index++) {
                                MenuOption option = widget.options().get(index);
                                int row = widget.row() + 2 + index * 2;
                                assertEquals(
                                        "action/" + option.action(),
                                        last(canvas.hits(), hit -> covers(hit, row, widget.x()))
                                                .action());
                            }
                        }
                    }
                }
            }
        }
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

    /** Kotlin {@code last { predicate }}. */
    private static <T> T last(List<T> values, Predicate<T> predicate) {
        return Kt.last(values.stream().filter(predicate).toList());
    }

    /** Kotlin {@code String.trimIndent()}: common indent removed, blank first/last line dropped. */
    private static String trimIndent(String text) {
        List<String> lines = Kt.split(text, '\n');
        int indent = -1;
        for (String line : lines) {
            if (Kt.isNotBlank(line)) {
                int width = 0;
                while (width < line.length() && Kt.isWhitespace(line.charAt(width))) {
                    width++;
                }
                indent = indent < 0 ? width : Math.min(indent, width);
            }
        }
        int common = Math.max(indent, 0);
        List<String> result = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if ((index == 0 || index == lines.size() - 1) && Kt.isBlank(line)) {
                continue;
            }
            result.add(Kt.drop(line, common));
        }
        return String.join("\n", result);
    }
}
