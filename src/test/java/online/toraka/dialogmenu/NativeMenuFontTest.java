package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class NativeMenuFontTest {
    static java.util.List<TextComponent> leaves(Component component) {
        var result = new java.util.ArrayList<TextComponent>();
        leaves(component, Style.empty(), result);
        return result;
    }

    private static void leaves(
            Component component, Style inherited, java.util.List<TextComponent> result) {
        Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && !text.content().isEmpty())
            result.add(Component.text(text.content()).style(style));
        for (Component child : component.children()) leaves(child, style, result);
    }

    static String visible(Component component) {
        StringBuilder out = new StringBuilder();
        for (TextComponent text : leaves(component)) {
            if (!NativeMenuFont.POSITION.equals(text.font())
                    && !NativeMenuFont.BOUNDS.equals(text.font())
                    && !DialogCanvas.FONT.equals(text.font())) out.append(text.content());
        }
        return out.toString();
    }

    /** Client advances after inherited styles, including the two positioning spaces. */
    static float width(Component component) {
        return width(component, Style.empty());
    }

    private static float width(Component component, Style inherited) {
        Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        boolean bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        Key font = style.font();
        float total = 0;
        if (component instanceof TextComponent text) {
            for (int cp : text.content().codePoints().toArray()) {
                if (NativeMenuFont.POSITION.equals(font)) {
                    int offset = cp - 0xF000;
                    total += (200 + offset / 2) * 16384 * (offset % 2 == 0 ? -1 : 1);
                } else if (DialogCanvas.FONT.equals(font)) {
                    total += cp == 0xE7F0 ? .5f : cp == 0xE7F1 ? -.5f : cp - 0xEA00;
                } else if (NativeMenuFont.BOUNDS.equals(font)) {
                    total += (((cp - 0xF100) / 2 + 6) * 3 + 1) / 2 + 1;
                } else if (NativeMenuFont.FONT.equals(font)) {
                    total += 9 + (bold ? .5f : 0);
                } else if (font != null && font.value().startsWith("text_")) {
                    int size = Integer.parseInt(font.value().split("_")[1]);
                    total +=
                            TitleFont.width(new String(Character.toChars(cp)), size)
                                    + (bold ? 1 : 0);
                } else {
                    total += LabelMetrics.width(new String(Character.toChars(cp)), font, bold);
                }
            }
        }
        for (Component child : component.children()) total += width(child, style);
        return total;
    }

    @Test
    void allSizesAndBaselinesKeepExactAdvancesWhenMixedWithLatinAndBold() {
        DialogCanvas canvas = new DialogCanvas(it -> null);
        for (int size = 6; size <= 24; size++) {
            for (boolean raised : new boolean[] {false, true}) {
                for (boolean bold : new boolean[] {false, true}) {
                    MeasuredText measured = canvas.prepare("菜单 A，Ｂ 价格⛂·", size, bold, raised);
                    assertEquals(
                            measured.advance(),
                            width(measured.component()),
                            .001,
                            "size=" + size + " raised=" + raised + " bold=" + bold);
                    for (MeasuredText line : measured.wrap(40)) {
                        assertEquals(line.advance(), width(line.component()), .001);
                        assertTrue(line.advance() <= 40);
                    }
                    assertEquals(
                            measured.fit(37).advance(), width(measured.fit(37).component()), .001);
                }
            }
        }
    }

    @Test
    void glyphCoverageAndFallbackRemainAvailableWithoutAnAtlas() throws Exception {
        assertTrue(NativeMenuFont.contains('中'));
        assertTrue(NativeMenuFont.contains('⛂'));
        assertFalse(NativeMenuFont.contains('A'));
        assertEquals("中文?", TitleFont.normalize("中文\uE123"));
        assertEquals(34, TitleFont.width("中文", 16));
        try (var entries =
                Files.list(Path.of("resourcepack/assets/dialogmenu_settings/textures/ui"))) {
            assertFalse(
                    entries.anyMatch(p -> p.getFileName().toString().startsWith("button_cjk_")));
        }
        assertTrue(Files.size(Path.of("src/main/resources/title-metrics.properties")) < 4096);
    }

    @Test
    void decorationsKeepLogicalWidthAndInheritedClickWithoutScalingLines() {
        for (int size : new int[] {6, 8, 16, 24}) {
            var style =
                    Style.style()
                            .font(
                                    size == 8
                                            ? DialogCanvas.BUTTON_LABEL_FONT
                                            : TitleFont.font(size, true))
                            .decorate(
                                    TextDecoration.ITALIC,
                                    TextDecoration.UNDERLINED,
                                    TextDecoration.STRIKETHROUGH)
                            .build();
            float advance = size == 8 ? 9 : TitleFont.width("中", size);
            var text =
                    new MeasuredText(
                            java.util.List.of(
                                    new MeasuredGlyph(Component.text("中").style(style), advance)));
            var click = net.kyori.adventure.text.event.ClickEvent.runCommand("/example");
            Component result = text.component().clickEvent(click);
            assertEquals(advance, width(result), .001);
            assertEquals("中", visible(result));
            assertTrue(leaves(result).stream().anyMatch(c -> NativeMenuFont.FONT.equals(c.font())));
            for (var leaf : leaves(result)) {
                assertEquals(click, leaf.clickEvent());
                if (NativeMenuFont.FONT.equals(leaf.font())) {
                    assertEquals(
                            TextDecoration.State.FALSE, leaf.decoration(TextDecoration.ITALIC));
                    assertEquals(
                            TextDecoration.State.FALSE, leaf.decoration(TextDecoration.UNDERLINED));
                    assertEquals(
                            TextDecoration.State.FALSE,
                            leaf.decoration(TextDecoration.STRIKETHROUGH));
                }
            }
        }
    }

    @Test
    void exportActualMixedFontCanvasesForVanillaClientVerification() throws Exception {
        Path output =
                Path.of(System.getProperty("user.home"), ".gradle-builds/DialogMenu/layout-probes");
        Files.createDirectories(output);
        for (int group = 0; group < 3; group++) {
            DialogCanvas canvas = new DialogCanvas(it -> null);
            for (int i = 0; i < 7 && 6 + group * 7 + i <= 24; i++) {
                int size = 6 + group * 7 + i;
                canvas.text(0, i * 4, size + " 菜单 ABC ⛂", 0xFFFFFF, null, false, size, false);
                canvas.text(240, i * 4, "按钮 中文", 0x12ABCD, null, true, size, true);
            }
            Files.writeString(
                    output.resolve("native-font-" + group + ".json"),
                    net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                            .serialize(canvas.build()));
        }
        DialogCanvas decorated = new DialogCanvas(it -> null);
        int row = 0;
        for (int size : new int[] {6, 8, 16, 24}) {
            for (var effect :
                    new TextDecoration[] {
                        TextDecoration.ITALIC,
                        TextDecoration.UNDERLINED,
                        TextDecoration.STRIKETHROUGH
                    }) {
                MeasuredText original = decorated.prepare("菜单 ABC 中文", size, false, true);
                MeasuredText styled =
                        new MeasuredText(
                                original.glyphs().stream()
                                        .map(
                                                g ->
                                                        new MeasuredGlyph(
                                                                g.component()
                                                                        .decoration(effect, true),
                                                                g.advance()))
                                        .toList());
                decorated.text(0, row, styled);
                row += 3;
            }
        }
        Files.writeString(
                output.resolve("native-styles.json"),
                net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                        .serialize(decorated.build()));
    }
}
