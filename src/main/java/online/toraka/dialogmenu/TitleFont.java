package online.toraka.dialogmenu;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.kyori.adventure.key.Key;

/** Authored layout advances: small Latin bitmaps and client CJK restored by NativeMenuFont. */
public final class TitleFont {

    private record Glyph(int inkWidth, int cellHeight) {}

    private static final Map<Integer, Glyph> METRICS = load();

    private TitleFont() {}

    private static Map<Integer, Glyph> load() {
        Properties properties = new Properties();
        try (InputStream input =
                Kt.requireNotNull(
                        TitleFont.class.getResourceAsStream("/title-metrics.properties"))) {
            properties.load(input);
        } catch (IOException error) {
            throw Kt.sneaky(error);
        }
        Map<Integer, Glyph> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            List<String> parts = Kt.split(entry.getValue().toString(), ',');
            result.put(
                    Integer.parseInt(entry.getKey().toString()),
                    new Glyph(Integer.parseInt(parts.get(0)), Integer.parseInt(parts.get(1))));
        }
        return Collections.unmodifiableMap(result);
    }

    public static Key font(int size) {
        return font(size, false);
    }

    public static Key font(int size, boolean raised) {
        return Key.key(
                "dialogmenu_dialogue:text_" + size + (raised && size <= 12 ? "_button" : ""));
    }

    public static int lineRows(int size) {
        return size == 8 ? 1 : ((size * 3 + 1) / 2 + 8) / 9;
    }

    private static int advance(char character, int size) {
        if (character == ' ') {
            return (size + 1) / 2;
        }
        if (NativeMenuFont.contains(character)) {
            return (int) Math.floor(((size * 3 + 1) / 2) * (2.0 / 3.0) + 0.5) + 1;
        }
        Glyph glyph = METRICS.get((int) character);
        if (glyph == null) {
            glyph = Kt.getValue(METRICS, 63);
        }
        return (int) Math.floor((double) glyph.inkWidth() * size / glyph.cellHeight() + 0.5) + 1;
    }

    public static String normalize(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            result.append(
                    METRICS.containsKey((int) character) || NativeMenuFont.contains(character)
                            ? character
                            : '?');
        }
        return result.toString();
    }

    public static int width(String text, int size) {
        int sum = 0;
        for (int i = 0; i < text.length(); i++) {
            sum += advance(text.charAt(i), size);
        }
        return sum;
    }
}
