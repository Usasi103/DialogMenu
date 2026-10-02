package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ButtonWidthTest {

    private static final List<String> THEMES = List.of("amethyst", "parchment");
    private static final List<String> LOOKS = List.of("button", "selected", "wide-button");
    private static final Function<String, ClickEvent> CLICK =
            it -> DialogClicks.custom(Key.key("test", it));

    /** One glyph of the bundled pack's {@code dialogmenu_dialogue:ui} font, as the client loads it. */
    private record Glyph(BufferedImage cell, int advance) {}

    private static Map<String, byte[]> pack;
    private static Map<Integer, Glyph> font;

    private static synchronized Map<String, byte[]> pack() throws IOException {
        if (pack == null) {
            pack = new HashMap<>();
            try (InputStream input =
                            ButtonWidthTest.class.getResourceAsStream(
                                    "/" + BundledResourcePack.RESOURCE);
                    ZipInputStream zip = new ZipInputStream(assertNotNullStream(input))) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                    pack.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        return pack;
    }

    private static InputStream assertNotNullStream(InputStream input) {
        assertNotNull(input, "bundled resource pack");
        return input;
    }

    private static BufferedImage texture(String name) throws IOException {
        byte[] bytes = pack().get("assets/dialogmenu_dialogue/textures/ui/" + name + ".png");
        assertNotNull(bytes, name + ".png is in the bundled pack");
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    /** Bitmap providers measured like the client: cell size and trimmed advance plus one. */
    private static synchronized Map<Integer, Glyph> font() throws IOException {
        if (font != null) {
            return font;
        }
        font = new HashMap<>();
        JsonObject json =
                JsonParser.parseString(
                                new String(
                                        pack().get("assets/dialogmenu_dialogue/font/ui.json"),
                                        StandardCharsets.UTF_8))
                        .getAsJsonObject();
        for (JsonElement element : json.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            String file = provider.get("file").getAsString();
            BufferedImage image =
                    texture(file.substring(file.indexOf("ui/") + 3, file.length() - 4));
            List<String> rows = new ArrayList<>();
            provider.getAsJsonArray("chars").forEach(it -> rows.add(it.getAsString()));
            int cellWidth = image.getWidth() / rows.get(0).length();
            int cellHeight = image.getHeight() / rows.size();
            // Font atlas pages are 256 px: a wider cell becomes an invisible missing glyph.
            assertTrue(cellWidth <= 256 && cellHeight <= 256, file);
            float scale = provider.get("height").getAsFloat() / cellHeight;
            for (int row = 0; row < rows.size(); row++) {
                for (int column = 0; column < rows.get(row).length(); column++) {
                    BufferedImage cell =
                            image.getSubimage(
                                    column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                    int actual = 0;
                    for (int x = cellWidth - 1; x >= 0 && actual == 0; x--) {
                        for (int y = 0; y < cellHeight; y++) {
                            if ((cell.getRGB(x, y) >>> 24) != 0) {
                                actual = x + 1;
                                break;
                            }
                        }
                    }
                    assertEquals(
                            null,
                            font.put(
                                    (int) rows.get(row).charAt(column),
                                    new Glyph(cell, (int) (0.5 + actual * scale) + 1)));
                }
            }
        }
        return font;
    }

    /** The left two columns, the middle column repeated, then the right two columns. */
    private static BufferedImage stretch(BufferedImage source, int width) {
        BufferedImage image =
                new BufferedImage(width, source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            int from;
            if (x < 2) {
                from = x;
            } else if (x >= width - 2) {
                from = source.getWidth() - (width - x);
            } else {
                from = source.getWidth() / 2;
            }
            for (int y = 0; y < source.getHeight(); y++) {
                image.setRGB(x, y, source.getRGB(from, y));
            }
        }
        return image;
    }

    private record Painted(int glyph, int x) {}

    /** Lays out one canvas row like the client; returns the UI glyphs and the row's net advance. */
    private static List<Painted> layout(Component root, int row, int[] net) throws IOException {
        List<Component> parts = new ArrayList<>();
        flatten(root, parts);
        List<Painted> painted = new ArrayList<>();
        int current = 0;
        int cursor = 0;
        for (Component part : parts) {
            String content = ((TextComponent) part).content();
            if (content.equals("\n")) {
                current++;
                continue;
            }
            if (current != row) {
                continue;
            }
            for (char character : content.toCharArray()) {
                if (DialogCanvas.FONT.equals(part.font())) {
                    assertTrue(character >= 0xE800 && character <= 0xEC00, "whole-pixel space");
                    cursor += character - 0xEA00;
                } else {
                    assertEquals(TemplateSkins.font(), part.font());
                    painted.add(new Painted(character, cursor));
                    cursor += font().get((int) character).advance();
                }
            }
        }
        net[0] = cursor;
        return painted;
    }

    private static void flatten(Component component, List<Component> into) {
        into.add(component);
        for (Component child : component.children()) {
            flatten(child, into);
        }
    }

    private static DialogTemplate parse(String elements) {
        return parse("amethyst", elements);
    }

    private static DialogTemplate parse(String theme, String elements) {
        return TemplateParser.parse(
                "width-test", "Version: 1\nSkin: " + theme + "\nElements:\n" + elements);
    }

    private static String button(String id, int x, String extra) {
        return "  "
                + id
                + ":\n    Type: button\n    Position: ["
                + x
                + ", 2]\n    Text: 确定\n    Actions: [close]\n"
                + extra;
    }

    private static TemplateElement single(DialogTemplate template, String id) {
        return Kt.single(template.elements().stream().filter(it -> it.id().equals(id)).toList());
    }

    private static String message(Runnable parse) {
        return assertThrows(IllegalArgumentException.class, parse::run).getMessage();
    }

    @Test
    @DisplayName("Width stretches button looks in both skins and keeps the default glyph")
    void widthStretchesButtonLooksInBothSkinsAndKeepsTheDefaultGlyph() {
        for (String theme : THEMES) {
            DialogCanvas.Skin native108 = TemplateSkins.get(theme, "button");
            TemplateElement plain = single(parse(theme, button("ok", 0, "")), "ok");
            assertEquals(108, plain.width());
            assertEquals(native108.glyph(), plain.sprite().glyph());
            assertEquals(native108, plain.sprite());
            assertTrue(plain.sprite().slices().isEmpty());
            assertEquals(plain, single(parse(theme, button("ok", 0, "    Width: 108\n")), "ok"));

            TemplateElement wide =
                    single(parse(theme, button("ok", 0, "    Sprite: wide-button\n")), "ok");
            assertEquals(TemplateSkins.get(theme, "wide-button"), wide.sprite());
            assertEquals(144, wide.width());

            for (String look : LOOKS) {
                for (int width : List.of(16, 40, 200, 300, 536)) {
                    TemplateElement element =
                            single(
                                    parse(
                                            theme,
                                            button(
                                                    "ok",
                                                    16,
                                                    "    Sprite: "
                                                            + look
                                                            + "\n    Width: "
                                                            + width
                                                            + "\n")),
                                    "ok");
                    DialogCanvas.Skin sprite = element.sprite();
                    assertEquals(width, element.width());
                    assertEquals(2, element.rows());
                    assertEquals(width, sprite.width());
                    assertEquals(2, sprite.rows());
                    assertEquals(
                            width,
                            sprite.slices().stream().mapToInt(DialogCanvas.Slice::width).sum());
                }
            }
        }
    }

    @Test
    @DisplayName("selected looks take the button width, configured or default")
    void selectedLooksTakeTheButtonWidthConfiguredOrDefault() {
        for (String theme : THEMES) {
            TemplateElement element =
                    single(
                            parse(
                                    theme,
                                    button(
                                            "ok",
                                            0,
                                            "    Width: 200\n    SelectedSprite: selected\n")),
                            "ok");
            assertEquals(TemplateSkins.get(theme, "button", 200), element.sprite());
            assertEquals(TemplateSkins.get(theme, "selected", 200), element.selectedSprite());
            assertFalse(
                    element.sprite()
                            .slices()
                            .get(0)
                            .equals(element.selectedSprite().slices().get(0)));

            TemplateElement standard =
                    single(parse(theme, button("ok", 0, "    SelectedSprite: selected\n")), "ok");
            assertEquals(TemplateSkins.get(theme, "selected"), standard.selectedSprite());

            // wide-button is a 144 px default; its selected look is composed at 144.
            TemplateElement wide =
                    single(
                            parse(
                                    theme,
                                    button(
                                            "ok",
                                            0,
                                            "    Sprite: wide-button\n"
                                                    + "    SelectedSprite: selected\n")),
                            "ok");
            assertEquals(TemplateSkins.get(theme, "selected", 144), wide.selectedSprite());
            assertEquals(144, wide.selectedSprite().width());
        }
        assertTrue(
                message(
                                () ->
                                        parse(
                                                button(
                                                        "ok",
                                                        0,
                                                        "    Width: 200\n"
                                                                + "    SelectedSprite: close\n")))
                        .contains("选中贴图尺寸必须相同"));
    }

    @Test
    @DisplayName("Width is rejected on other sprites, out of range and past the canvas")
    void widthIsRejectedOnOtherSpritesOutOfRangeAndPastTheCanvas() {
        for (String sprite : List.of("close", "emblem", "divider", "reward", "panel")) {
            assertEquals(
                    "templates/width-test.yml.Elements.ok.Width: "
                            + "仅 button / selected / wide-button 贴图的按钮可设置宽度",
                    message(
                            () ->
                                    parse(
                                            button(
                                                    "ok",
                                                    0,
                                                    "    Sprite: "
                                                            + sprite
                                                            + "\n    Width: 40\n"))));
        }
        for (String width : List.of("15", "961", "0", "-40", "120.5", "wide")) {
            assertEquals(
                    "templates/width-test.yml.Elements.ok.Width: 需要 16..960 范围内整数",
                    message(() -> parse(button("ok", 0, "    Width: " + width + "\n"))));
        }
        assertEquals(
                "templates/width-test.yml.Elements.ok: 元素超出画布",
                message(() -> parse(button("ok", 500, "    Width: 60\n"))));
        assertEquals(492, single(parse(button("ok", 492, "    Width: 60\n")), "ok").x());
        // Width on a sprite element keeps its old meaning (ignored for built-in sprites).
        assertEquals(
                108,
                single(
                                parse(
                                        "  mark:\n    Type: sprite\n    Position: [0, 0]\n"
                                                + "    Sprite: emblem\n    Width: 40\n"),
                                "mark")
                        .width());
    }

    @Test
    @DisplayName("button overlap uses the configured width")
    void buttonOverlapUsesTheConfiguredWidth() {
        String second = button("next", 150, "");
        parse(button("ok", 0, "") + second);
        assertTrue(
                message(() -> parse(button("ok", 0, "    Width: 151\n") + second))
                        .contains("按钮点击区域重叠 ok/next"));
        parse(button("ok", 0, "    Width: 150\n") + second);
        assertTrue(
                message(() -> parse(button("ok", 0, "") + button("next", 100, "    Width: 40\n")))
                        .contains("按钮点击区域重叠 ok/next"));
    }

    @Test
    @DisplayName("every width from 16 to 960 tiles exactly with at most 12 glyphs")
    void everyWidthFrom16To960TilesExactlyWithAtMost12Glyphs() throws IOException {
        for (String theme : THEMES) {
            for (String look : LOOKS) {
                assertTrue(TemplateSkins.resizable(theme, look));
                for (int width = 16; width <= 960; width++) {
                    DialogCanvas.Skin skin = TemplateSkins.get(theme, look, width);
                    if (width == TemplateSkins.get(theme, look).width()) {
                        assertTrue(skin.slices().isEmpty());
                        continue;
                    }
                    assertTrue(skin.slices().size() <= 12, theme + look + width);
                    int total = 0;
                    for (DialogCanvas.Slice slice : skin.slices()) {
                        total += slice.width();
                        Glyph glyph = font().get(slice.glyph());
                        assertNotNull(glyph, "glyph " + Integer.toHexString(slice.glyph()));
                        // The compiled advance is the one the client measures from the PNG.
                        assertEquals(glyph.advance(), slice.advance());
                        assertTrue(slice.width() <= glyph.cell().getWidth());
                    }
                    assertEquals(width, total);
                }
            }
            for (String other : List.of("close", "emblem", "divider", "reward", "panel")) {
                assertFalse(TemplateSkins.resizable(theme, other));
            }
        }
    }

    @Test
    @DisplayName("stretched glyph runs paint the 3-slice image and keep one hit region")
    void stretchedGlyphRunsPaintThe3SliceImageAndKeepOneHitRegion() throws IOException {
        for (String theme : THEMES) {
            for (String look : LOOKS) {
                BufferedImage source = texture(theme + "_" + look);
                // The art really is two-pixel edges around one repeated column.
                for (int x = 0; x < source.getWidth(); x++) {
                    for (int y = 0; y < source.getHeight(); y++) {
                        assertEquals(
                                source.getRGB(x, y),
                                stretch(source, source.getWidth()).getRGB(x, y));
                    }
                }
                for (int width : List.of(16, 17, 40, 107, 109, 200, 255, 256, 257, 300, 767, 956)) {
                    DialogCanvas.Skin skin = TemplateSkins.get(theme, look, width);
                    DialogCanvas canvas =
                            new DialogCanvas(
                                    MenuTheme.DARK,
                                    960,
                                    4,
                                    Key.key("dialogmenu_dialogue:labels"),
                                    Key.key("dialogmenu_dialogue:button_labels"),
                                    new RichMenuText(),
                                    CLICK);
                    canvas.sprite(3, 1, skin, "go");
                    assertEquals(
                            List.of(new DialogCanvas.Hit(3, 1, width, 2, "go")), canvas.hits());
                    Component built = canvas.build();
                    int[] net = new int[1];
                    List<Painted> painted = layout(built, 1, net);
                    assertEquals(960 + DialogCanvas.WRAP_SLACK, net[0]);
                    assertEquals(
                            skin.slices().stream().map(DialogCanvas.Slice::glyph).toList(),
                            painted.stream().map(Painted::glyph).toList());
                    BufferedImage image =
                            new BufferedImage(width + 512, 18, BufferedImage.TYPE_INT_ARGB);
                    int expectedX = 3;
                    for (int index = 0; index < painted.size(); index++) {
                        Painted piece = painted.get(index);
                        assertEquals(expectedX, piece.x());
                        expectedX += skin.slices().get(index).width();
                        BufferedImage cell = font().get(piece.glyph()).cell();
                        for (int x = 0; x < cell.getWidth(); x++) {
                            for (int y = 0; y < 18; y++) {
                                int argb = cell.getRGB(x, y);
                                if ((argb >>> 24) != 0) {
                                    image.setRGB(piece.x() - 3 + x, y, argb);
                                }
                            }
                        }
                    }
                    assertEquals(3 + width, expectedX);
                    BufferedImage expected = stretch(source, width);
                    for (int x = 0; x < image.getWidth(); x++) {
                        for (int y = 0; y < 18; y++) {
                            int want = x < width ? expected.getRGB(x, y) : 0;
                            if ((want >>> 24) == 0) {
                                want = 0;
                            }
                            assertEquals(
                                    want,
                                    image.getRGB(x, y),
                                    theme + " " + look + " " + width + " at " + x + "," + y);
                        }
                    }
                    // Glyphs keep the click event as well as the invisible hit grid.
                    List<Component> parts = new ArrayList<>();
                    flatten(built, parts);
                    assertEquals(
                            skin.slices().size(),
                            parts.stream()
                                    .filter(it -> TemplateSkins.font().equals(it.font()))
                                    .filter(it -> it.clickEvent() != null)
                                    .count());
                }
            }
        }
    }

    @Test
    @DisplayName("stretched buttons centre and fit their label to the new width")
    void stretchedButtonsCentreAndFitTheirLabelToTheNewWidth() {
        DialogTemplate template =
                parse(
                        button("short", 0, "    Width: 40\n").replace("确定", "确认领取全部奖励")
                                + button("long", 100, "    Width: 300\n")
                                        .replace("确定", "确认领取全部奖励"));
        DialogCanvas canvas =
                TemplateRenderer.render(
                        template, template.values(Collections.emptyMap()), it -> it, CLICK);
        assertEquals(
                List.of(
                        new DialogCanvas.Hit(0, 2, 40, 2, "short"),
                        new DialogCanvas.Hit(100, 2, 300, 2, "long")),
                canvas.hits().stream().filter(it -> it.row() == 2).toList());
        MeasuredText clipped = canvas.prepare("确认领取全部奖励", 8, false, true).fit(32);
        assertTrue(clipped.width() <= 32);
        String text = PlainTextComponentSerializer.plainText().serialize(canvas.build());
        assertTrue(
                text.contains(
                        PlainTextComponentSerializer.plainText().serialize(clipped.component())));
        assertTrue(text.contains("确认领取全部奖励"));
    }

    @Test
    @DisplayName("buttons without Width render the same JSON as before")
    void buttonsWithoutWidthRenderTheSameJsonAsBefore() {
        for (String theme : THEMES) {
            String elements =
                    button("ok", 0, "    SelectedSprite: selected\n    SelectedWhen: mood=calm\n")
                            + button("wide", 200, "    Sprite: wide-button\n");
            String variables = "Variables:\n  mood: [calm, angry]\n";
            DialogTemplate before =
                    TemplateParser.parse(
                            "width-test",
                            "Version: 1\nSkin: "
                                    + theme
                                    + "\n"
                                    + variables
                                    + "Elements:\n"
                                    + elements);
            DialogTemplate same =
                    TemplateParser.parse(
                            "width-test",
                            "Version: 1\nSkin: "
                                    + theme
                                    + "\n"
                                    + variables
                                    + "Elements:\n"
                                    + elements.replace(
                                            "    Sprite: wide-button\n",
                                            "    Sprite: wide-button\n    Width: 144\n"));
            for (String mood : List.of("calm", "angry")) {
                Map<String, String> values = Map.of("mood", mood);
                String json =
                        GsonComponentSerializer.gson()
                                .serialize(
                                        TemplateRenderer.render(before, values, it -> it, CLICK)
                                                .build());
                assertEquals(
                        json,
                        GsonComponentSerializer.gson()
                                .serialize(
                                        TemplateRenderer.render(same, values, it -> it, CLICK)
                                                .build()));
                // One whole glyph per button, exactly as before slices existed.
                int glyphs = 0;
                for (int codepoint : json.codePoints().toArray()) {
                    if (codepoint >= 0xE016 && codepoint <= 0xE057) {
                        glyphs++;
                    }
                }
                assertEquals(0, glyphs, "no slice glyphs");
                String selected =
                        String.valueOf(
                                (char)
                                        TemplateSkins.get(
                                                        theme,
                                                        mood.equals("calm") ? "selected" : "button")
                                                .glyph());
                assertTrue(json.contains(selected));
            }
        }
    }
}
