import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import java.util.zip.ZipFile;

/** Compile UI sprites into whole bitmap glyphs; no downloaded source sheet is copied. */
public class BuildTemplateSkin {
    static Path output;
    static List<String> providers = new ArrayList<>();
    static StringBuilder metrics = new StringBuilder();
    static int glyph = 0xE000;

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        BufferedImage source = args.length > 1 ? ImageIO.read(Path.of(args[1]).toFile()) : null;
        output = project.resolve("resourcepack/assets/dialogmenu_dialogue");
        Files.createDirectories(output.resolve("textures/ui"));
        Files.createDirectories(output.resolve("font"));
        for (String theme : List.of("amethyst", "parchment")) {
            boolean parchment = theme.equals("parchment");
            BufferedImage panel = panel(552, 180, parchment, false);
            if (parchment && source != null)
                panel = nineSlice(source.getSubimage(178, 0, 78, 68), 552, 180, 7);
            if (parchment && source == null) clearLastColumn(panel);
            register(theme + ".panel", panel, 3);
            register(theme + ".button", button(source, 108, parchment, false), 1);
            register(theme + ".selected", button(source, 108, parchment, true), 1);
            register(theme + ".wide-button", button(source, 144, parchment, false), 1);
            register(theme + ".close", panel(18, 18, parchment, false), 1);
            BufferedImage divider = new BufferedImage(348, 9, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = divider.createGraphics();
            g.setColor(new Color(parchment ? 0x9b8065 : 0x74548f));
            g.drawLine(4, 4, 343, 4);
            for (int x : new int[] {2, 345})
                g.fillPolygon(new int[] {x, x + 2, x + 4, x + 2}, new int[] {4, 2, 4, 6}, 4);
            g.dispose();
            register(theme + ".divider", divider, 2);
            BufferedImage emblem = new BufferedImage(108, 108, BufferedImage.TYPE_INT_ARGB);
            g = emblem.createGraphics();
            g.setColor(new Color(parchment ? 0x765d49 : 0x594071));
            g.drawPolygon(
                    new int[] {54, 96, 96, 54, 12, 12}, new int[] {4, 28, 80, 104, 80, 28}, 6);
            g.setColor(new Color(parchment ? 0xc4ab90 : 0xb293d1));
            g.drawPolygon(
                    new int[] {54, 88, 88, 54, 20, 20}, new int[] {14, 34, 74, 94, 74, 34}, 6);
            if (parchment && source != null) {
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(source.getSubimage(54, 26, 20, 22), 34, 32, 40, 44, null);
            } else {
                // A replaceable abstract crest, not a supplied character portrait.
                g.fillPolygon(new int[] {54, 72, 54, 36}, new int[] {29, 53, 79, 53}, 4);
                g.setColor(new Color(0x24192f));
                g.fillPolygon(new int[] {54, 64, 54, 44}, new int[] {39, 53, 69, 53}, 4);
            }
            g.dispose();
            register(theme + ".emblem", emblem, 1);
            register(theme + ".reward", panel(27, 27, parchment, false), 1);
        }
        Files.writeString(
                output.resolve("font/ui.json"),
                "{\"providers\":[" + String.join(",", providers) + "]}\n");
        Files.writeString(
                project.resolve("src/main/resources/template-skins.properties"),
                metrics,
                StandardCharsets.UTF_8);
        compileSymbols(project);
        System.out.println(
                "Compiled template skin glyphs; imported local sheet: " + (source != null));
    }

    static BufferedImage button(
            BufferedImage source, int width, boolean parchment, boolean selected) {
        if (parchment && source != null)
            return nineSlice(source.getSubimage(3, selected ? 99 : 73, 109, 25), width, 18, 5);
        BufferedImage image = panel(width, 18, parchment, selected);
        if (parchment) clearLastColumn(image);
        return image;
    }

    static void clearLastColumn(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) image.setRGB(image.getWidth() - 1, y, 0);
    }

    static void compileSymbols(Path project) throws Exception {
        String symbols = "·×•…—";
        Map<Integer, String> bits = new HashMap<>();
        try (ZipFile zip =
                new ZipFile(project.resolve("design/minecraft-font/unifont.zip").toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".hex")) continue;
                for (String line :
                        new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)
                                .split("\\R")) {
                    int colon = line.indexOf(':');
                    if (colon < 0) continue;
                    int cp = Integer.parseInt(line.substring(0, colon), 16);
                    if (symbols.indexOf(cp) >= 0) bits.put(cp, line.substring(colon + 1));
                }
            }
        }
        for (boolean raised : new boolean[] {false, true}) {
            int cellHeight = raised ? 24 : 16;
            BufferedImage atlas =
                    new BufferedImage(
                            symbols.length() * 16, cellHeight, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < symbols.length(); i++) {
                String glyph = Objects.requireNonNull(bits.get((int) symbols.charAt(i)));
                int digits = glyph.length() / 16, sourceWidth = digits * 4;
                for (int y = 0; y < 16; y++) {
                    long row =
                            Long.parseUnsignedLong(
                                    glyph.substring(y * digits, (y + 1) * digits), 16);
                    for (int x = 0; x < sourceWidth; x++)
                        if ((row & (1L << (sourceWidth - x - 1))) != 0)
                            atlas.setRGB(i * 16 + (16 - sourceWidth) / 2 + x, y, 0xffffffff);
                }
                atlas.setRGB(i * 16 + 15, cellHeight - 1, 0x01ffffff);
            }
            String name = raised ? "button_labels" : "labels";
            ImageIO.write(atlas, "png", output.resolve("textures/ui/" + name + ".png").toFile());
            Files.writeString(
                    output.resolve("font/" + name + ".json"),
                    "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"dialogmenu_dialogue:ui/"
                            + name
                            + ".png\",\"height\":"
                            + (raised ? 12 : 8)
                            + ",\"ascent\":"
                            + (raised ? 11 : 7)
                            + ",\"chars\":[\""
                            + symbols
                            + "\"]},{\"type\":\"reference\",\"id\":\"dialogmenu_settings:"
                            + name
                            + "\"}]}\n");
        }
    }

    static BufferedImage panel(int width, int height, boolean parchment, boolean selected) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(
                new Color(
                        parchment
                                ? (selected ? 0x806951 : 0x382b20)
                                : (selected ? 0x5d406d : 0x100b17)));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(parchment ? 0xb3977b : 0x9271ac));
        g.drawRect(0, 0, width - 1, height - 1);
        g.setColor(new Color(parchment ? 0x67513d : 0x271b32));
        g.drawRect(1, 1, width - 3, height - 3);
        g.dispose();
        return image;
    }

    static BufferedImage nineSlice(BufferedImage source, int width, int height, int border) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int[] sx = {0, border, source.getWidth() - border, source.getWidth()},
                sy = {0, border, source.getHeight() - border, source.getHeight()};
        int[] dx = {0, border, width - border, width}, dy = {0, border, height - border, height};
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++)
                g.drawImage(
                        source, dx[x], dy[y], dx[x + 1], dy[y + 1], sx[x], sy[y], sx[x + 1],
                        sy[y + 1], null);
        g.dispose();
        return image;
    }

    static void register(String id, BufferedImage image, int columns) throws Exception {
        String name = id.replace('.', '_');
        ImageIO.write(image, "png", output.resolve("textures/ui/" + name + ".png").toFile());
        int first = glyph;
        List<String> advances = new ArrayList<>();
        StringBuilder chars = new StringBuilder();
        int cell = image.getWidth() / columns;
        for (int col = 0; col < columns; col++) {
            chars.append(String.format("\\u%04x", glyph++));
            int actual = 0;
            for (int x = cell - 1; x >= 0 && actual == 0; x--)
                for (int y = 0; y < image.getHeight(); y++)
                    if ((image.getRGB(col * cell + x, y) >>> 24) != 0) {
                        actual = x + 1;
                        break;
                    }
            advances.add(Integer.toString(actual + 1));
        }
        metrics.append(id)
                .append('=')
                .append(first)
                .append(',')
                .append(image.getWidth())
                .append(',')
                .append(image.getHeight() / 9)
                .append(',')
                .append(columns)
                .append(',')
                .append(String.join(",", advances))
                .append('\n');
        providers.add(
                "{\"type\":\"bitmap\",\"file\":\"dialogmenu_dialogue:ui/"
                        + name
                        + ".png\",\"height\":"
                        + image.getHeight()
                        + ",\"ascent\":7,\"chars\":[\""
                        + chars
                        + "\"]}");
    }
}
