package online.toraka.dialogmenu;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import net.kyori.adventure.key.Key;

public final class TemplateSkins {

    private static final Properties questMetrics = load("/quest-skins.properties");
    private static final Properties metrics = load("/template-skins.properties");
    private static final Key font = Key.key("dialogmenu_dialogue:ui");

    private TemplateSkins() {}

    private static Properties load(String resource) {
        Properties properties = new Properties();
        try (InputStream input =
                Kt.requireNotNull(TemplateSkins.class.getResourceAsStream(resource))) {
            properties.load(input);
        } catch (IOException error) {
            throw Kt.sneaky(error);
        }
        return properties;
    }

    public static Key font() {
        return font;
    }

    public static DialogCanvas.Skin get(String theme, String name) {
        boolean quest = name.startsWith("quest-");
        String metric =
                Kt.requireNotNull(
                        (quest ? questMetrics : metrics).getProperty(theme + "." + name),
                        () -> "未知贴图 " + theme + "." + name);
        List<Integer> parts = new ArrayList<>();
        for (String part : Kt.split(metric, ',')) {
            parts.add(Integer.parseInt(part));
        }
        return new DialogCanvas.Skin(
                parts.get(0),
                parts.get(1),
                parts.get(2),
                quest ? Key.key("dialogmenu_dialogue:quest_ui") : font,
                new ArrayList<>(parts.subList(Math.min(4, parts.size()), parts.size())),
                parts.get(3));
    }

    /** Button looks compiled with edge and fill slices, so they can take any width. */
    public static boolean resizable(String theme, String name) {
        return metrics.getProperty("slice." + theme + "." + name) != null;
    }

    /**
     * The sprite at {@code width}: its left edge, the widest fills of its middle column that add
     * up to the rest, then its right edge. Its own width keeps the whole glyph.
     */
    public static DialogCanvas.Skin get(String theme, String name, int width) {
        DialogCanvas.Skin skin = get(theme, name);
        if (width == skin.width()) {
            return skin;
        }
        String metric =
                Kt.requireNotNull(
                        metrics.getProperty("slice." + theme + "." + name),
                        () -> "贴图不能调整宽度 " + theme + "." + name);
        // glyph,width,advance per piece: left edge, right edge, then the fills.
        List<Integer> parts = new ArrayList<>();
        for (String part : Kt.split(metric, ',')) {
            parts.add(Integer.parseInt(part));
        }
        List<DialogCanvas.Slice> pieces = new ArrayList<>();
        for (int index = 0; index + 2 < parts.size(); index += 3) {
            pieces.add(
                    new DialogCanvas.Slice(
                            parts.get(index), parts.get(index + 1), parts.get(index + 2)));
        }
        DialogCanvas.Slice left = pieces.get(0);
        DialogCanvas.Slice right = pieces.get(1);
        List<DialogCanvas.Slice> fills = new ArrayList<>(pieces.subList(2, pieces.size()));
        fills.sort(Comparator.comparingInt(DialogCanvas.Slice::width).reversed());
        List<DialogCanvas.Slice> slices = new ArrayList<>();
        slices.add(left);
        int remaining = width - left.width() - right.width();
        for (DialogCanvas.Slice fill : fills) {
            for (; remaining >= fill.width(); remaining -= fill.width()) {
                slices.add(fill);
            }
        }
        Kt.require(remaining == 0, () -> "贴图 " + theme + "." + name + " 拼不出宽度 " + width);
        slices.add(right);
        return skin.withSlices(width, slices);
    }
}
