package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToggleSwitchTest {
    private MenuDefinition parse() {
        return parse("", "Bind: particles");
    }

    private MenuDefinition parse(String style) {
        return parse(style, "Bind: particles");
    }

    private MenuDefinition parse(String style, String binding) {
        return SimpleMenuParser.parse(
                "MenuType: dialog\nVersion: 2\nPages: [test]",
                id ->
                        "Title: 测试\nLayout: [开关]\nIcons:\n  开关:\n    Type: toggle\n    "
                                + binding
                                + "\n    "
                                + style);
    }

    @Test
    @DisplayName("switch is opt in and preserves builtin and custom actions")
    void switchIsOptInAndPreservesBuiltinAndCustomActions() {
        MenuWidget old = Kt.last(Kt.getValue(parse().pages(), "test").widgets());
        MenuDefinition menu = parse("Style: switch");
        MenuWidget widget = Kt.last(Kt.getValue(menu.pages(), "test").widgets());
        assertEquals(ToggleStyle.BUTTON, old.toggleStyle());
        assertEquals(ToggleStyle.SWITCH, widget.toggleStyle());
        assertEquals(old.withToggleStyle(ToggleStyle.SWITCH), widget);
        assertEquals("particles toggle", Kt.getValue(menu.actions(), widget.action()).command());
        MenuDefinition custom =
                parse(
                        "Style: switch",
                        "State: '%custom_toggle%'\n    Actions: ['command: toggle']");
        MenuWidget customWidget = Kt.last(Kt.getValue(custom.pages(), "test").widgets());
        assertEquals("%custom_toggle%", Kt.getValue(custom.states(), customWidget.state()));
        assertEquals(
                List.of("command: toggle"),
                Kt.getValue(custom.actions(), customWidget.action()).reaction().lines());
        for (String style : List.of("Style: typo", "Style: true", "Style: ''")) {
            assertThrows(Exception.class, () -> parse(style));
        }
        assertThrows(
                Exception.class,
                () ->
                        SimpleMenuParser.parse(
                                "MenuType: dialog\nVersion: 2\nPages: [test]",
                                id ->
                                        "Title: 测试\nLayout: [按钮]\nIcons:\n  按钮: {Type: button, Style: switch, Actions: [close]}"));
    }

    @Test
    @DisplayName("switch states themes languages and painted click events keep exact canvas width")
    void switchStatesThemesLanguagesAndPaintedClickEventsKeepExactCanvasWidth() {
        MenuDefinition menu = parse("Style: switch");
        MenuWidget widget = Kt.last(Kt.getValue(menu.pages(), "test").widgets());
        for (MenuTheme theme : MenuTheme.values()) {
            for (MenuLanguage language : MenuLanguage.values()) {
                List<String> states = Arrays.asList("true", "false", null);
                for (int stateIndex = 0; stateIndex < states.size(); stateIndex++) {
                    String state = states.get(stateIndex);
                    DialogCanvas canvas =
                            MenuRenderer.render(
                                    menu,
                                    "test",
                                    language,
                                    theme,
                                    key -> state,
                                    value -> value,
                                    action -> DialogClicks.custom(Key.key("test", action)));
                    List<TextComponent> parts = new ArrayList<>();
                    for (Component child : canvas.build().children()) {
                        parts.add((TextComponent) child);
                    }
                    TextComponent glyph =
                            single(parts, part -> DialogCanvas.SWITCH_FONT.equals(part.font()));
                    assertEquals(
                            0xE700 + stateIndex + (theme == MenuTheme.LIGHT ? 3 : 0),
                            singleCode(glyph.content()));
                    String action = "action/" + widget.action();
                    assertEquals(DialogClicks.custom(Key.key("test", action)), glyph.clickEvent());
                    String status =
                            menu.text(
                                    language,
                                    "$" + List.of("on", "off", "unavailable").get(stateIndex));
                    assertTrue(
                            parts.stream()
                                    .anyMatch(
                                            part ->
                                                    part.content().equals(status)
                                                            && Objects.equals(
                                                                    part.clickEvent(),
                                                                    glyph.clickEvent())));
                    for (int x = widget.x() + 78; x < widget.x() + 114; x++) {
                        for (int row = widget.row(); row <= widget.row() + 1; row++) {
                            int column = x;
                            int line = row;
                            assertEquals(
                                    action,
                                    last(canvas.hits(), hit -> covers(hit, line, column)).action());
                        }
                    }
                    int width = 0;
                    int lines = 1;
                    for (TextComponent part : parts) {
                        if (part.content().equals("\n")) {
                            assertEquals(452, width);
                            width = 0;
                            lines++;
                        } else if (DialogCanvas.SWITCH_FONT.equals(part.font())) {
                            width += 37;
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
                                theme + " " + language + " " + state + " width=" + width);
                    }
                    assertEquals(29, lines);
                    assertEquals(452, width);
                }
            }
        }
    }

    /** {@code x in hit.x until hit.x + hit.width && row in hit.row until hit.row + hit.rows}. */
    private static boolean covers(DialogCanvas.Hit hit, int row, int x) {
        return hit.x() <= x
                && x < hit.x() + hit.width()
                && hit.row() <= row
                && row < hit.row() + hit.rows();
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
}
