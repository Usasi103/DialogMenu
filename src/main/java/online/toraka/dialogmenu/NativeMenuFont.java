package online.toraka.dialogmenu;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

/** Client Unihex plus measured spacing; the GUI shader restores the authored size and baseline. */
final class NativeMenuFont {
    static final Key FONT = Key.key("dialogmenu_settings:button_cjk");
    static final Key POSITION = Key.key("dialogmenu_settings:native_position");
    static final Key BOUNDS = Key.key("dialogmenu_settings:native_bounds");
    private static final int[] RANGES = loadRanges();
    private static final Map<Key, Integer> LAYOUTS = layouts();

    private NativeMenuFont() {}

    private static int[] loadRanges() {
        try (var reader =
                new BufferedReader(
                        new InputStreamReader(
                                Kt.requireNotNull(
                                        NativeMenuFont.class.getResourceAsStream(
                                                "/native-cjk-ranges.txt")),
                                StandardCharsets.UTF_8))) {
            return reader.lines()
                    .flatMapToInt(
                            line ->
                                    java.util.Arrays.stream(line.split(" "))
                                            .mapToInt(Integer::parseInt))
                    .toArray();
        } catch (java.io.IOException error) {
            throw Kt.sneaky(error);
        }
    }

    static boolean contains(int cp) {
        int low = 0, high = RANGES.length / 2 - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (cp < RANGES[mid * 2]) high = mid - 1;
            else if (cp > RANGES[mid * 2 + 1]) low = mid + 1;
            else return true;
        }
        return false;
    }

    private static Map<Key, Integer> layouts() {
        Map<Key, Integer> result = new HashMap<>();
        result.put(DialogCanvas.BUTTON_LABEL_FONT, 5);
        result.put(Key.key("dialogmenu_dialogue:button_labels"), 5);
        result.put(Key.key("dialogmenu_dialogue:title"), 20);
        for (int size = 6; size <= 24; size++) {
            if (size == 8) continue;
            result.put(TitleFont.font(size), (size - 6) * 2);
            if (size <= 12) result.put(TitleFont.font(size, true), (size - 6) * 2 + 1);
        }
        return Map.copyOf(result);
    }

    private static int layout(MeasuredGlyph glyph) {
        Component c = glyph.component();
        if (!(c instanceof TextComponent text)
                || !c.children().isEmpty()
                || text.content().length() != 1
                || !contains(text.content().charAt(0))) return -1;
        int base = c.font() == null ? -1 : LAYOUTS.getOrDefault(c.font(), -1);
        return base < 0
                ? -1
                : base
                        + (c.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE
                                ? 38
                                : 0);
    }

    /** Null means no transformation: ordinary labels/images keep their exact component shape. */
    static Component render(List<MeasuredGlyph> glyphs) {
        if (glyphs.stream().noneMatch(g -> layout(g) >= 0)) return null;
        List<MeasuredGlyph> painted = new ArrayList<>();
        int current = -1;
        for (MeasuredGlyph glyph : glyphs) {
            int next = layout(glyph);
            if (next != current) {
                if (current >= 0) painted.add(position(current, true));
                if (next >= 0) painted.add(position(next, false));
                current = next;
            }
            if (next < 0) {
                painted.add(glyph);
                continue;
            }
            Component original = glyph.component();
            boolean bold = original.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
            float actual = 9f + (bold ? .5f : 0f);
            painted.add(
                    new MeasuredGlyph(
                            original.font(FONT)
                                    .decoration(TextDecoration.ITALIC, false)
                                    .decoration(TextDecoration.UNDERLINED, false)
                                    .decoration(TextDecoration.STRIKETHROUGH, false),
                            actual));
            float correction = glyph.advance() - actual;
            if (correction != 0)
                painted.add(new MeasuredGlyph(DialogCanvas.space(correction), correction));
            if (original.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE
                    || original.decoration(TextDecoration.STRIKETHROUGH)
                            == TextDecoration.State.TRUE) {
                // Vanilla decorations use the logical advance and fixed line baseline. Draw them
                // on screen so they neither inherit Unihex's advance nor the glyph scale shader.
                painted.add(position(current, true));
                current = -1;
                painted.add(
                        new MeasuredGlyph(DialogCanvas.space(-glyph.advance()), -glyph.advance()));
                painted.add(
                        new MeasuredGlyph(
                                DialogCanvas.space(glyph.advance())
                                        .style(original.style())
                                        .font(DialogCanvas.FONT)
                                        .decoration(TextDecoration.BOLD, false)
                                        .decoration(TextDecoration.ITALIC, false)
                                        .decoration(TextDecoration.OBFUSCATED, false),
                                glyph.advance()));
            }
        }
        if (current >= 0) painted.add(position(current, true));
        return new MeasuredText(painted).component();
    }

    private static MeasuredGlyph position(int index, boolean restore) {
        int distance = (200 + index) * 16384 * (restore ? 1 : -1);
        Component result =
                Component.text(String.valueOf((char) (0xF000 + index * 2 + (restore ? 1 : 0))))
                        .font(POSITION)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false);
        if (restore) {
            // CPU GUI sorting cannot see shader movement. Keep the run's bounds on screen,
            // or later button sprites may cover it. Negative advance first prevents wrapping.
            int base = index % 38;
            int height = ((base / 2 + 6) * 3 + 1) / 2;
            result =
                    result.append(DialogCanvas.space(-height - 1))
                            .append(
                                    Component.text(String.valueOf((char) (0xF100 + base)))
                                            .font(BOUNDS));
        }
        return new MeasuredGlyph(result, distance);
    }
}
