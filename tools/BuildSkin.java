import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Compile Hallow reference primitives into whole UI glyphs and measured advances. */
public class BuildSkin {
    private static final List<String> providers = new ArrayList<>();
    private static final StringBuilder metrics = new StringBuilder();
    private static Path root;
    private static BufferedImage tiles;
    private static BufferedImage frame;

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        root = project.resolve("resourcepack/assets/toraka_settings");
        Path source = project.resolve("design/hallow-source");
        tiles = ImageIO.read(source.resolve("tiles_16x9.png").toFile());
        frame = ImageIO.read(source.resolve("settings_toggle_enabled.png").toFile());
        Files.createDirectories(root.resolve("textures/ui"));
        Files.createDirectories(root.resolve("font"));
        skin("panel_top", 336, 81, false, true, 0xE000);
        skin("panel_bottom", 336, 108, false, true, 0xE020);
        skin("nav", 102, 18, false, false, 0xE040);
        skin("nav_selected", 102, 18, true, false, 0xE050);
        skin("control", 114, 18, false, false, 0xE060);
        skin("control_selected", 114, 18, true, false, 0xE070);
        skin("search", 102, 18, false, false, 0xE080);
        skin("panel_top_light", 336, 81, false, true, 0xE100);
        skin("panel_bottom_light", 336, 108, false, true, 0xE120);
        skin("nav_light", 102, 18, false, false, 0xE140);
        skin("nav_selected_light", 102, 18, true, false, 0xE150);
        skin("control_light", 114, 18, false, false, 0xE160);
        skin("control_selected_light", 114, 18, true, false, 0xE170);
        skin("search_light", 102, 18, false, false, 0xE180);
        BufferedImage icons = ImageIO.read(source.resolve("dialog_icons.png").toFile());
        int[] cells = {8, 26, 3, 9, 6, 11, 2, 8, 17, 16};
        for (int i = 0; i < cells.length; i++) {
            BufferedImage icon = i >= 8
                    ? ImageIO.read(source.resolve(i == 8 ? "menu_down.png" : "menu_up.png").toFile())
                    : icons.getSubimage(cells[i] % 8 * 18, cells[i] / 8 * 18, 18, 18);
            if (i == 1) icon = ImageIO.read(source.resolve("settings_entry_voice_chat.png").toFile()).getSubimage(0, 0, 18, 18);
            if (i == 6 || i == 7) icon = ImageIO.read(source.resolve(i == 6 ? "menu_filter.png" : "menu_back.png").toFile());
            String name = "hallow_icon_" + i;
            ImageIO.write(icon, "png", root.resolve("textures/ui/" + name + ".png").toFile());
            register(name, icon, 9, 0xE090 + i, 1);
        }
        StringBuilder spaces = new StringBuilder("{\"type\":\"space\",\"advances\":{");
        for (int value = -512; value <= 512; value++) {
            if (value != -512) spaces.append(',');
            spaces.append('"').append(escape(0xEA00 + value)).append("\":").append(value);
        }
        providers.add(spaces.append("}}").toString());
        Files.writeString(root.resolve("font/ui.json"),
                "{\"providers\":[" + String.join(",", providers) + "]}\n", StandardCharsets.UTF_8);
        BufferedImage ascii = ImageIO.read(root.resolve("textures/ui/ascii.png").toFile());
        List<String> asciiChars = Files.readAllLines(source.resolve("ascii-chars.txt"), StandardCharsets.UTF_8);
        int cell = ascii.getWidth() / 16;
        for (int row = 0; row < asciiChars.size(); row++) {
            for (int col = 0; col < 16; col++) {
                int cp = asciiChars.get(row).charAt(col);
                if (cp != 0) metrics.append("label.").append(cp).append('=')
                        .append(advance(ascii.getSubimage(col * cell, row * cell, cell, cell), 8)).append('\n');
            }
        }
        metrics.append("label.32=4\n");
        // Four transparent pixels beneath each ASCII cell allow ascent 11 while
        // retaining scale 1 and satisfying Minecraft's ascent <= height rule.
        BufferedImage raised = new BufferedImage(ascii.getWidth(), asciiChars.size() * 12, BufferedImage.TYPE_INT_ARGB);
        Graphics2D raisedGraphics = raised.createGraphics();
        for (int row = 0; row < asciiChars.size(); row++) {
            for (int col = 0; col < 16; col++) {
                raisedGraphics.drawImage(ascii.getSubimage(col * cell, row * cell, cell, cell), col * cell, row * 12, null);
            }
        }
        raisedGraphics.dispose();
        ImageIO.write(raised, "png", root.resolve("textures/ui/ascii_raised.png").toFile());
        String labelFont = Files.readString(root.resolve("font/labels.json"));
        writeCjkFont(project.resolve("design/minecraft-font"));
        Files.writeString(root.resolve("font/button_labels.json"),
                labelFont.replace("ui/ascii.png", "ui/ascii_raised.png")
                        .replace("\"ascent\": 7", "\"ascent\": 11, \"height\": 12")
                        .replace("{\"type\": \"reference\", \"id\": \"minecraft:include/unifont\"}",
                                "{\"type\":\"reference\",\"id\":\"toraka_settings:button_cjk\"},"
                                + "{\"type\":\"reference\",\"id\":\"minecraft:include/unifont\"}"));
        Files.writeString(project.resolve("src/main/resources/ui-metrics.properties"), metrics, StandardCharsets.UTF_8);
        System.out.println("Compiled whole glyphs and measured advances: " + root);
    }

    private record HexGlyph(int codepoint, String bits) {}

    /** Match the vanilla full-width CJK ranges, with the same -4px button offset as ASCII. */
    private static void writeCjkFont(Path source) throws Exception {
        List<HexGlyph> glyphs = new ArrayList<>();
        try (ZipFile zip = new ZipFile(source.resolve("unifont.zip").toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".hex")) continue;
                for (String line : new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    int colon = line.indexOf(':');
                    if (colon < 0) continue;
                    int cp = Integer.parseInt(line.substring(0, colon), 16);
                    if ((cp >= 0x3001 && cp <= 0x9FFF) || (cp >= 0xF900 && cp <= 0xFAFF)) {
                        glyphs.add(new HexGlyph(cp, line.substring(colon + 1)));
                    }
                }
            }
        }
        glyphs.sort(Comparator.comparingInt(HexGlyph::codepoint));
        List<String> cjkProviders = new ArrayList<>();
        for (int start = 0, page = 0; start < glyphs.size(); start += 1024, page++) {
            int count = Math.min(1024, glyphs.size() - start);
            int rows = (count + 31) / 32;
            byte[] white = {(byte) 255, (byte) 255, (byte) 255};
            IndexColorModel palette = new IndexColorModel(2, 3, white, white, white, new byte[] {0, 1, (byte) 255});
            BufferedImage atlas = new BufferedImage(32 * 16, rows * 24, BufferedImage.TYPE_BYTE_BINARY, palette);
            List<String> chars = new ArrayList<>();
            for (int row = 0; row < rows; row++) {
                StringBuilder codes = new StringBuilder("\"");
                for (int col = 0; col < 32; col++) {
                    int index = row * 32 + col;
                    if (index >= count) { codes.append(escape(0)); continue; }
                    HexGlyph glyph = glyphs.get(start + index);
                    codes.append(escape(glyph.codepoint()));
                    int digits = glyph.bits().length() / 16;
                    int sourceWidth = digits * 4;
                    for (int y = 0; y < 16; y++) {
                        long bits = Long.parseUnsignedLong(glyph.bits().substring(y * digits, (y + 1) * digits), 16);
                        for (int x = 0; x < Math.min(16, sourceWidth); x++) {
                            if ((bits & (1L << (sourceWidth - x - 1))) != 0) atlas.setRGB(col * 16 + x, row * 24 + y, 0xFFFFFFFF);
                        }
                    }
                    // Unihex reserves 16 source pixels for these ranges. Bitmap
                    // providers trim transparent columns, so pin that advance
                    // with an alpha-1 pixel below the ink, invisible to the shader.
                    atlas.setRGB(col * 16 + 15, row * 24 + 23, 0x01FFFFFF);
                }
                chars.add(codes.append('"').toString());
            }
            String filename = "button_cjk_" + page + ".png";
            ImageIO.write(atlas, "png", root.resolve("textures/ui/" + filename).toFile());
            cjkProviders.add("{\"type\":\"bitmap\",\"file\":\"toraka_settings:ui/" + filename
                    + "\",\"height\":12,\"ascent\":11,\"chars\":[" + String.join(",", chars) + "]}");
        }
        Files.writeString(root.resolve("font/button_cjk.json"), "{\"providers\":[" + String.join(",", cjkProviders) + "]}\n", StandardCharsets.UTF_8);
        Files.copy(source.resolve("LICENSE.txt"), root.resolve("font/LICENSE-Unifont.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Aligned " + glyphs.size() + " full-width CJK glyphs with the button baseline.");
    }

    private static void skin(String name, int width, int height, boolean selected, boolean card, int glyph)
            throws Exception {
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        // Nine-slice the actual Hallow widget texture. Repeat its plain interior,
        // excluding the baked checkbox, while preserving all border pixels.
        int start = selected ? frame.getWidth() / 2 : 0;
        int templateWidth = frame.getWidth() / 2;
        int templateHeight = frame.getHeight() / 2;
        for (int y = 0; y < height; y++) {
            int sy = y < 3 ? y : y >= height - 3 ? templateHeight - (height - y) : 8;
            for (int x = 0; x < width; x++) {
                int sx = x < 3 ? x : x >= width - 3 ? templateWidth - (width - x) : 4;
                int pixel = frame.getRGB(start + sx, sy);
                // Palette variants belong to this compiled layout: preserve alpha,
                // borders and glyph advances so changing themes never moves a hit area.
                if (glyph >= 0xE100 && pixel != 0xFF7F7F00) pixel = lightPalette(pixel);
                // This source uses a shader metadata marker in its corner.
                im.setRGB(x, y, pixel == 0xFF7F7F00 ? 0 : pixel);
            }
        }
        if (card) for (int x = 5; x < width - 5; x++) {
            int pixel = tiles.getRGB(2, 4);
            im.setRGB(x, 26, glyph >= 0xE100 ? lightPalette(pixel) : pixel);
        }
        ImageIO.write(im, "png", root.resolve("textures/ui/" + name + ".png").toFile());
        // Glyph atlases are 256px wide: large panels use two columns, never rows.
        register(name, im, height, glyph, width > 256 ? 2 : 1);
    }

    private static void register(String name, BufferedImage image, int height, int glyph, int columns) {
        StringBuilder chars = new StringBuilder();
        for (int col = 0; col < columns; col++) {
            chars.append(escape(glyph + col));
            int width = image.getWidth() / columns;
            metrics.append("glyph.").append(glyph + col).append('=')
                    .append(advance(image.getSubimage(col * width, 0, width, image.getHeight()), height)).append('\n');
        }
        providers.add("{\"type\":\"bitmap\",\"file\":\"toraka_settings:ui/" + name
                + ".png\",\"height\":" + height + ",\"ascent\":" + ((glyph & 0xFF) >= 0x90 ? 2 : 7)
                + ",\"chars\":[\"" + chars + "\"]}");
    }

    private static int advance(BufferedImage cell, int height) {
        int actual = 0;
        for (int x = cell.getWidth() - 1; x >= 0 && actual == 0; x--) {
            for (int y = 0; y < cell.getHeight(); y++) {
                if ((cell.getRGB(x, y) >>> 24) != 0) { actual = x + 1; break; }
            }
        }
        return Math.round(actual * (float) height / cell.getHeight()) + 1;
    }

    private static int lightPalette(int pixel) {
        int red = (pixel >>> 16) & 255;
        int green = (pixel >>> 8) & 255;
        int blue = pixel & 255;
        if (red != green || green != blue) return pixel;
        int value = Math.min(255, 176 + red / 2);
        return (pixel & 0xFF000000) | (value << 16) | (value << 8) | value;
    }

    private static String escape(int value) { return String.format("\\u%04x", value); }
}
