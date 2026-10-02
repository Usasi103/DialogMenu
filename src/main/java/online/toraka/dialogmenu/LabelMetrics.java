package online.toraka.dialogmenu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.kyori.adventure.key.Key;

/** Advances compiled from the same bitmap and Unihex providers shipped in the pack. */
public final class LabelMetrics {

    private record Range(int end, float advance, float boldOffset) {}

    private record Font(String parent, TreeMap<Integer, Range> ranges) {}

    private static final Map<String, Font> FONTS = load();

    private LabelMetrics() {}

    private static Map<String, Font> load() {
        Map<String, Font> fonts = new HashMap<>();
        InputStream stream =
                Kt.requireNotNull(LabelMetrics.class.getResourceAsStream("/label-metrics.txt"));
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || Kt.isBlank(line)) {
                    continue;
                }
                List<String> parts = Kt.split(line, ' ');
                if (parts.get(0).equals("@")) {
                    String parent = parts.get(2).equals("-") ? null : parts.get(2);
                    fonts.put(parts.get(1), new Font(parent, new TreeMap<>()));
                } else {
                    Kt.getValue(fonts, parts.get(0))
                            .ranges()
                            .put(
                                    Integer.parseInt(parts.get(1)),
                                    new Range(
                                            Integer.parseInt(parts.get(2)),
                                            Float.parseFloat(parts.get(3)),
                                            Float.parseFloat(parts.get(4))));
                }
            }
        } catch (IOException error) {
            throw Kt.sneaky(error);
        }
        return fonts;
    }

    private static Range glyph(String font, int codepoint) {
        Font data = Kt.getValue(FONTS, font);
        Map.Entry<Integer, Range> floor = data.ranges().floorEntry(codepoint);
        if (floor != null && codepoint <= floor.getValue().end()) {
            return floor.getValue();
        }
        return data.parent() == null ? null : glyph(data.parent(), codepoint);
    }

    public static float width(String text, Key font, boolean bold) {
        float width = 0f;
        int[] codepoints = text.codePoints().toArray();
        for (int codepoint : codepoints) {
            Range glyph = glyph(font.asString(), codepoint);
            float advance = glyph != null ? glyph.advance() : 6f;
            float boldOffset = glyph != null ? glyph.boldOffset() : 1f;
            width += advance + (bold ? boldOffset : 0f);
        }
        return width;
    }
}
