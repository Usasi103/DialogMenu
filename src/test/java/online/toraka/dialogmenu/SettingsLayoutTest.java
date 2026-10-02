package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingsLayoutTest {
    private MenuDefinition parse(String page) {
        return parse(page, "");
    }

    private MenuDefinition parse(String page, String navigation) {
        return SimpleMenuParser.parse(
                "Version: 2\nPages: [test]\n" + navigation, id -> trimIndent(page));
    }

    @Test
    @DisplayName("settings positions and typography reach rendered text controls and click regions")
    void settingsPositionsAndTypographyReachRenderedTextControlsAndClickRegions() {
        MenuDefinition menu =
                parse(
                        """
                        Title: 测试
                        TitleStyle: {Position: [130, 1], FontSize: 12, Bold: true}
                        Layout: [文字, 标题, 开关, 主题]
                        Icons:
                          文字: {Type: text, Name: 文字预览, Position: [140, 4], Width: 240, FontSize: 16, Bold: true, Color: '#12ABCD'}
                          标题: {Type: heading, Name: 控件, Position: [130, 11], FontSize: 12, Bold: true}
                          开关: {Type: toggle, Style: switch, Name: 测试开关, Bind: particles, Position: [322, 14], LabelPosition: [132, 14], FontSize: 10, Bold: true}
                          主题: {Type: dropdown, Name: 主题, Bind: theme, Position: [324, 18], FontSize: 10, Bold: true, Options: {dark: 深色, light: 浅色}}
                        """,
                        "Navigation: {Position: [4, 3], Step: 3, FontSize: 10, Bold: true}");
        assertEquals(4, menu.navX());
        assertEquals(10, menu.navTextSize());
        assertTrue(menu.navBold());
        List<MenuWidget> widgets = Kt.getValue(menu.pages(), "test").widgets();
        MenuWidget text = single(widgets, widget -> widget.text().equals("文字预览"));
        assertEquals(
                List.of(140, 4, 240, 16),
                List.of(text.x(), text.row(), text.width(), text.textSize()));
        MenuWidget toggle = single(widgets, widget -> widget.kind() == WidgetKind.TOGGLE);
        assertEquals(
                List.of(322, 14, 132, 14),
                List.of(toggle.x(), toggle.row(), toggle.labelX(), toggle.labelRow()));
        DialogCanvas canvas =
                MenuRenderer.render(
                        menu,
                        "test",
                        MenuLanguage.CHINESE,
                        MenuTheme.DARK,
                        state -> "theme".equals(state) ? "dark" : "true",
                        value -> value,
                        action -> DialogClicks.custom(Key.key("test", action)));
        List<TextComponent> parts = new ArrayList<>();
        for (Component child : canvas.build().children()) {
            if (child instanceof TextComponent part) {
                parts.add(part);
            }
        }
        TextComponent renderedText = single(parts, part -> part.content().equals("文字预览"));
        assertEquals(TitleFont.font(16), renderedText.font());
        assertEquals(TextDecoration.State.TRUE, renderedText.decoration(TextDecoration.BOLD));
        assertEquals(0x12ABCD, Objects.requireNonNull(renderedText.color()).value());
        assertTrue(
                parts.stream()
                        .anyMatch(
                                part ->
                                        part.content().equals("开启")
                                                && Objects.equals(
                                                        part.font(), TitleFont.font(10, true))
                                                && part.clickEvent() != null));
        assertTrue(
                canvas.hits().stream()
                        .anyMatch(
                                hit ->
                                        hit.x() == 400
                                                && hit.row() == 14
                                                && hit.action()
                                                        .equals("action/" + toggle.action())));
        assertTrue(
                canvas.hits().stream()
                        .anyMatch(
                                hit ->
                                        hit.x() == 324
                                                && hit.row() == 18
                                                && hit.action().startsWith("dropdown/")));
    }

    @Test
    @DisplayName("automatic text layout reserves rows for larger fonts")
    void automaticTextLayoutReservesRowsForLargerFonts() {
        MenuDefinition menu =
                parse(
                        """
                        Title: 测试
                        Layout: [标题, 下行]
                        Icons:
                          标题: {Type: text, Name: 大字, FontSize: 16}
                          下行: {Type: text, Name: 下一行}
                        """);
        List<MenuWidget> widgets = Kt.getValue(menu.pages(), "test").widgets();
        assertEquals(3, single(widgets, widget -> widget.text().equals("大字")).row());
        assertEquals(6, single(widgets, widget -> widget.text().equals("下一行")).row());
    }

    @Test
    @DisplayName("invalid coordinates fonts types and overlapping click targets are rejected")
    void invalidCoordinatesFontsTypesAndOverlappingClickTargetsAreRejected() {
        String prefix = "Title: 测试\nLayout: [控件]\nIcons:\n  控件: ";
        for (String fields :
                List.of(
                        "Type: text, Position: [1.5, 3]",
                        "Type: text, Position: [123, 29]",
                        "Type: text, FontSize: '12'",
                        "Type: text, Bold: 'true'",
                        "Type: text, LabelPosition: [123, 3]",
                        "Type: text, FontSize: 25",
                        "Type: toggle, Bind: particles, FontSize: 16",
                        "Type: button, Actions: [close], Position: [0, 3]",
                        "Type: dropdown, Bind: theme, Position: [330, 25], Options: {dark: 深色, light: 浅色}")) {
            assertThrows(Exception.class, () -> parse(prefix + "{" + fields + "}"), fields);
        }
    }

    /** Kotlin {@code single { predicate }}. */
    private static <T> T single(List<T> values, Predicate<T> predicate) {
        return Kt.single(values.stream().filter(predicate).toList());
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
