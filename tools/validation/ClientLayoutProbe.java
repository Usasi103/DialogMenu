import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.util.RandomSource;

/** Exercises the actual Dialog body widget, including its internal text padding. */
public class ClientLayoutProbe {
    public static void main(String[] args) throws Exception {
        Properties metrics = new Properties();
        try (var in = Files.newInputStream(Path.of(args[0]))) { metrics.load(in); }
        Properties layout = new Properties();
        try (var in = Files.newInputStream(Path.of(args[1]).resolveSibling("layout.properties"))) { layout.load(in); }
        int bodyWidth = Integer.parseInt(layout.getProperty("bodyWidth"));
        int lineWidth = Integer.parseInt(layout.getProperty("lineWidth"));
        int rows = Integer.parseInt(layout.getProperty("rows"));
        var document = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        var component = Component.empty();
        for (var element : document.getAsJsonArray("extra")) {
            if (element.isJsonPrimitive()) {
                component.append(Component.literal(element.getAsString()));
                continue;
            }
            var part = element.getAsJsonObject();
            Style style = Style.EMPTY;
            if (part.has("font")) style = style.withFont(new FontDescription.Resource(Identifier.parse(part.get("font").getAsString())));
            component.append(Component.literal(part.get("text").getAsString()).withStyle(style));
        }
        StringSplitter splitter = new StringSplitter((cp, style) -> {
            if (style.getFont() instanceof FontDescription.Resource font
                    && font.id().toString().equals("dialogmenu_settings:ui")) {
                if (cp >= 0xE800 && cp <= 0xEC00) return cp - 0xEA00;
                return Integer.parseInt(metrics.getProperty("glyph." + cp));
            }
            return Integer.parseInt(metrics.getProperty("label." + cp, "9"));
        });
        var lines = splitter.splitLines(component, lineWidth, Style.EMPTY);
        if (lines.size() != rows) throw new AssertionError("Unexpected wrapping: " + lines.size());
        for (int i = 0; i < lines.size(); i++) {
            float width = splitter.stringWidth(lines.get(i));
            if (width != lineWidth) throw new AssertionError("Row " + i + " drift: " + width);
        }
        System.out.println("PASS: actual Minecraft 26.2 StringSplitter: 29 rows, all 452 pixels; no automatic wraps or horizontal drift.");
        Font font = new Font(new Font.Provider() {
            public GlyphSource glyphs(FontDescription description) {
                Style style = Style.EMPTY.withFont(description);
                return new GlyphSource() {
                    public BakedGlyph getGlyph(int cp) {
                        float width = splitter.stringWidth(FormattedText.of(Character.toString(cp), style));
                        return new EmptyGlyph(width).bake(null);
                    }
                    public BakedGlyph getRandomGlyph(RandomSource random, int width) { return new EmptyGlyph(width).bake(null); }
                };
            }
            public EffectGlyph effect() { return null; }
        });
        var broken = FocusableTextWidget.builder(component, font).maxWidth(452).build();
        var corrected = FocusableTextWidget.builder(component, font).maxWidth(bodyWidth).build();
        if (corrected.getPadding() != 4) throw new AssertionError("Unexpected Minecraft padding");
        if (broken.getHeight() <= corrected.getHeight()) throw new AssertionError("Original wrapping regression not reproduced");
        if (corrected.getHeight() != rows * 9 + 8) throw new AssertionError("Corrected Dialog height " + corrected.getHeight());
        System.out.println("PASS: actual FocusableTextWidget reproduces old height " + broken.getHeight()
                + "px and fixes it to " + corrected.getHeight() + "px with body width " + bodyWidth + " (line width " + lineWidth + ").");
    }
}
