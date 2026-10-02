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

/** Compile Blockbench-authored original textures into whole UI glyphs and measured advances. */
public class BuildSkin {
    private static final List<String> providers = new ArrayList<>();
    private static final StringBuilder metrics = new StringBuilder();
    private static Path root;
    private static Path source;

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        root = project.resolve("resourcepack/assets/dialogmenu_settings");
        source = project.resolve("design/original-ui");
        Files.createDirectories(root.resolve("textures/ui"));
        Files.createDirectories(root.resolve("font"));
        skin("panel_top", 336, 81, false, true, 0xE000);
        skin("panel_bottom", 336, 126, false, true, 0xE020);
        skin("nav", 102, 18, false, false, 0xE040);
        skin("nav_selected", 102, 18, true, false, 0xE050);
        skin("control", 114, 18, false, false, 0xE060);
        skin("control_selected", 114, 18, true, false, 0xE070);
        skin("search", 102, 18, false, false, 0xE080);
        skin("panel_top_light", 336, 81, false, true, 0xE100);
        skin("panel_bottom_light", 336, 126, false, true, 0xE120);
        skin("nav_light", 102, 18, false, false, 0xE140);
        skin("nav_selected_light", 102, 18, true, false, 0xE150);
        skin("control_light", 114, 18, false, false, 0xE160);
        skin("control_selected_light", 114, 18, true, false, 0xE170);
        skin("search_light", 102, 18, false, false, 0xE180);
        for (boolean light : new boolean[] {false, true}) {
            for (int selected = 0; selected <= 4; selected++) {
                slider(selected, light);
            }
            for (int direction = 0; direction < 2; direction++) {
                sliderArrow(direction, false, light);
                sliderArrow(direction, true, light);
            }
        }
        int configurableGlyph = 0xE400;
        for (int count = 2; count <= 8; count++) {
            for (int selected = 0; selected <= count; selected++) {
                configurableGlyph = configurableSlider(count, selected, configurableGlyph);
            }
        }
        String[] icons = {"profile", "sound", "particles", "notices", "loot", "appearance", "search", "return", "down", "up"};
        for (int i = 0; i < icons.length; i++) {
            String name = "icon_" + icons[i];
            BufferedImage icon = read("icons/" + name, 16, 16);
            ImageIO.write(icon, "png", root.resolve("textures/ui/" + name + ".png").toFile());
            register(name, icon, 16, 0xE090 + i, 1);
        }
        StringBuilder spaces = new StringBuilder("{\"type\":\"space\",\"advances\":{");
        for (int value = -512; value <= 512; value++) {
            if (value != -512) spaces.append(',');
            spaces.append('"').append(escape(0xEA00 + value)).append("\":").append(value);
        }
        spaces.append(",\"\\ue7f0\":0.5,\"\\ue7f1\":-0.5");
        providers.add(spaces.append("}}").toString());
        Files.writeString(root.resolve("font/ui.json"),
                "{\"providers\":[" + String.join(",", providers) + "]}\n", StandardCharsets.UTF_8);
        BufferedImage ascii = ImageIO.read(root.resolve("textures/ui/ascii.png").toFile());
        List<String> asciiChars = Files.readAllLines(project.resolve("design/minecraft-font/ascii-chars.txt"), StandardCharsets.UTF_8);
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
                        .replace("{\"type\": \"reference\", \"id\": \"dialogmenu_settings:unifont\"}",
                                "{\"type\":\"reference\",\"id\":\"dialogmenu_settings:button_cjk\"},"
                                + "{\"type\":\"reference\",\"id\":\"dialogmenu_settings:unifont\"}"));
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
                    if ((cp >= 0x3001 && cp <= 0x9FFF) || (cp >= 0xF900 && cp <= 0xFAFF)
                            || (cp >= 0xFF01 && cp <= 0xFF5E) || (cp >= 0x20A0 && cp <= 0x20CF) || cp == 0x26C2) {
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
            cjkProviders.add("{\"type\":\"bitmap\",\"file\":\"dialogmenu_settings:ui/" + filename
                    + "\",\"height\":12,\"ascent\":11,\"chars\":[" + String.join(",", chars) + "]}");
        }
        Files.writeString(root.resolve("font/button_cjk.json"), "{\"providers\":[" + String.join(",", cjkProviders) + "]}\n", StandardCharsets.UTF_8);
        Files.copy(source.resolve("LICENSE.txt"), root.resolve("font/LICENSE-Unifont.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Aligned " + glyphs.size() + " full-width CJK glyphs with the button baseline.");
    }

    private static void skin(String name, int width, int height, boolean selected, boolean card, int glyph)
            throws Exception {
        BufferedImage im = read("settings/" + name, width, height);
        ImageIO.write(im, "png", root.resolve("textures/ui/" + name + ".png").toFile());
        // Glyph atlases are 256px wide: large panels use two columns, never rows.
        register(name, im, height, glyph, width > 256 ? 2 : 1);
    }

    /** Use the authored rail; only the glyph columns are cut during compilation. */
    private static void slider(int selected, boolean light) throws Exception {
        String name = "density_slider_" + selected + (light ? "_light" : "");
        BufferedImage image = read("settings/" + name, 120, 18);
        ImageIO.write(image, "png", root.resolve("textures/ui/" + name + ".png").toFile());
        register(name, image, 18, (light ? 0xE300 : 0xE200) + selected * 4, 4);
    }

    private static int configurableSlider(int count, int selected, int glyph) throws Exception {
        BufferedImage rail = read("settings/slider_" + count + "_" + selected, 120, 18);
        for (int column = 0; column < count; column++) {
            int start = 120 * column / count;
            int end = 120 * (column + 1) / count;
            BufferedImage cell = rail.getSubimage(start, 0, end - start, 18);
            String name = "slider_" + count + "_" + selected + "_" + column;
            ImageIO.write(cell, "png", root.resolve("textures/ui/" + name + ".png").toFile());
            register(name, cell, 18, glyph++, 1);
        }
        return glyph;
    }

    private static void sliderArrow(int direction, boolean disabled, boolean light) throws Exception {
        String name = "density_arrow_" + direction + (disabled ? "_disabled" : "") + (light ? "_light" : "");
        BufferedImage image = read("settings/" + name, 18, 18);
        ImageIO.write(image, "png", root.resolve("textures/ui/" + name + ".png").toFile());
        register(name, image, 18, (light ? 0xE320 : 0xE220) + direction + (disabled ? 2 : 0), 1);
    }

    private static BufferedImage read(String name, int width, int height) throws Exception {
        BufferedImage image = ImageIO.read(source.resolve(name + ".png").toFile());
        if (image == null || image.getWidth() != width || image.getHeight() != height)
            throw new IllegalArgumentException("Invalid source dimensions: " + name);
        return image;
    }

    private static void register(String name, BufferedImage image, int height, int glyph, int columns) {
        StringBuilder chars = new StringBuilder();
        for (int col = 0; col < columns; col++) {
            chars.append(escape(glyph + col));
            int width = image.getWidth() / columns;
            metrics.append("glyph.").append(glyph + col).append('=')
                    .append(advance(image.getSubimage(col * width, 0, width, image.getHeight()), height)).append('\n');
        }
        providers.add("{\"type\":\"bitmap\",\"file\":\"dialogmenu_settings:ui/" + name
                + ".png\",\"height\":" + height + ",\"ascent\":" + (glyph >= 0xE090 && glyph <= 0xE099 ? 6 : 7)
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

    private static String escape(int value) { return String.format("\\u%04x", value); }
}
