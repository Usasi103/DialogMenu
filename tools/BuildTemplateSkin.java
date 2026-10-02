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
    static Path source;
    static List<String> providers = new ArrayList<>();
    static StringBuilder metrics = new StringBuilder();
    static Map<String, BufferedImage> images = new HashMap<>();
    static int glyph = 0xE000;

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        if (args.length != 1) throw new IllegalArgumentException("Expected only the project directory");
        source = project.resolve("design/original-ui/templates");
        output = project.resolve("resourcepack/assets/dialogmenu_dialogue");
        Files.createDirectories(output.resolve("textures/ui"));
        Files.createDirectories(output.resolve("font"));
        for (String theme : List.of("amethyst", "parchment")) {
            for (String name : List.of("panel", "button", "selected", "wide-button", "close", "divider", "emblem", "reward")) {
                register(theme + "." + name, source(theme, name), name.equals("panel") ? 3 : name.equals("divider") ? 2 : 1);
            }
        }
        // Appended after the whole sprites so their code points never move.
        for (String theme : List.of("amethyst", "parchment"))
            slices(theme, List.of("button", "selected", "wide-button"));
        Files.writeString(
                output.resolve("font/ui.json"),
                "{\"providers\":[" + String.join(",", providers) + "]}\n");
        Files.writeString(
                project.resolve("src/main/resources/template-skins.properties"),
                metrics,
                StandardCharsets.UTF_8);
        compileSymbols(project);
        System.out.println(
                "Compiled original Blockbench template skin glyphs.");
    }

    static BufferedImage source(String theme, String name) throws Exception {
        BufferedImage image = ImageIO.read(source.resolve(theme + "_" + name + ".png").toFile());
        if (image == null) throw new IllegalArgumentException("Invalid source texture: " + name);
        return image;
    }

    /**
     * Buttons of any width: each look's two edges plus power-of-two runs of its middle column.
     * One texture per piece width, one row per look, so no glyph cell is wider than 256 px.
     */
    static void slices(String theme, List<String> names) throws Exception {
        int height = 18, cap = 0;
        List<int[]> edges = new ArrayList<>();
        for (String name : names) {
            int[] edge = edges(images.get(theme + "." + name));
            edges.add(edge);
            cap = Math.max(cap, Math.max(edge[0], edge[1]));
        }
        BufferedImage caps =
                new BufferedImage(cap * 2, height * names.size(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < names.size(); row++) {
            BufferedImage button = images.get(theme + "." + names.get(row));
            int[] edge = edges.get(row);
            copy(button, 0, edge[0], caps, 0, row * height);
            copy(button, button.getWidth() - edge[1], edge[1], caps, cap, row * height);
        }
        List<int[]> edgeCells = grid(theme + "_button-edges", caps, 2, names.size());
        Map<Integer, List<int[]>> fills = new LinkedHashMap<>();
        for (int fill = 256; fill >= 1; fill /= 2) {
            BufferedImage strip =
                    new BufferedImage(fill, height * names.size(), BufferedImage.TYPE_INT_ARGB);
            for (int row = 0; row < names.size(); row++) {
                BufferedImage button = images.get(theme + "." + names.get(row));
                for (int x = 0; x < fill; x++)
                    copy(button, button.getWidth() / 2, 1, strip, x, row * height);
            }
            fills.put(fill, grid(theme + "_button-fill-" + fill, strip, 1, names.size()));
        }
        // slice.<theme>.<name>=glyph,width,advance per piece: left edge, right edge, then fills.
        for (int row = 0; row < names.size(); row++) {
            List<String> parts = new ArrayList<>();
            for (int side = 0; side < 2; side++)
                piece(parts, edgeCells.get(row * 2 + side), edges.get(row)[side]);
            for (Map.Entry<Integer, List<int[]>> fill : fills.entrySet())
                piece(parts, fill.getValue().get(row), fill.getKey());
            metrics.append("slice.")
                    .append(theme)
                    .append('.')
                    .append(names.get(row))
                    .append('=')
                    .append(String.join(",", parts))
                    .append('\n');
        }
    }

    static void piece(List<String> parts, int[] cell, int width) {
        parts.add(Integer.toString(cell[0]));
        parts.add(Integer.toString(width));
        parts.add(Integer.toString(cell[1]));
    }

    /** Widths of the left and right edges around the run of columns equal to the middle one. */
    static int[] edges(BufferedImage image) {
        int middle = image.getWidth() / 2, left = middle, right = middle;
        while (left > 0 && sameColumn(image, left - 1, middle)) left--;
        while (right < image.getWidth() - 1 && sameColumn(image, right + 1, middle)) right++;
        int[] edge = {left, image.getWidth() - 1 - right};
        if (edge[0] > 8 || edge[1] > 8)
            throw new IllegalStateException(
                    "Button art needs edges of at most 8 px around one repeated middle column: "
                            + Arrays.toString(edge));
        return edge;
    }

    static boolean sameColumn(BufferedImage image, int a, int b) {
        for (int y = 0; y < image.getHeight(); y++)
            if (image.getRGB(a, y) != image.getRGB(b, y)) return false;
        return true;
    }

    static void copy(BufferedImage from, int x, int width, BufferedImage to, int toX, int toY) {
        for (int dx = 0; dx < width; dx++)
            for (int y = 0; y < from.getHeight(); y++)
                to.setRGB(toX + dx, toY + y, from.getRGB(x + dx, y));
    }

    /** Writes a glyph grid texture; returns {code point, measured advance} per cell, row by row. */
    static List<int[]> grid(String name, BufferedImage image, int columns, int rows)
            throws Exception {
        ImageIO.write(image, "png", output.resolve("textures/ui/" + name + ".png").toFile());
        int cellWidth = image.getWidth() / columns, cellHeight = image.getHeight() / rows;
        List<int[]> cells = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            StringBuilder chars = new StringBuilder();
            for (int col = 0; col < columns; col++) {
                chars.append(String.format("\\u%04x", glyph));
                cells.add(
                        new int[] {
                            glyph++,
                            advance(image, col * cellWidth, row * cellHeight, cellWidth, cellHeight)
                        });
            }
            lines.add("\"" + chars + "\"");
        }
        providers.add(
                "{\"type\":\"bitmap\",\"file\":\"dialogmenu_dialogue:ui/"
                        + name
                        + ".png\",\"height\":"
                        + cellHeight
                        + ",\"ascent\":7,\"chars\":["
                        + String.join(",", lines)
                        + "]}");
        return cells;
    }

    /** The client's bitmap advance: one past the last column with any opaque pixel, plus one. */
    static int advance(BufferedImage image, int x0, int y0, int width, int height) {
        for (int x = width - 1; x >= 0; x--)
            for (int y = 0; y < height; y++)
                if ((image.getRGB(x0 + x, y0 + y) >>> 24) != 0) return x + 2;
        return 1;
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

    static void register(String id, BufferedImage image, int columns) throws Exception {
        String name = id.replace('.', '_');
        images.put(id, image);
        ImageIO.write(image, "png", output.resolve("textures/ui/" + name + ".png").toFile());
        int first = glyph;
        List<String> advances = new ArrayList<>();
        StringBuilder chars = new StringBuilder();
        int cell = image.getWidth() / columns;
        for (int col = 0; col < columns; col++) {
            chars.append(String.format("\\u%04x", glyph++));
            advances.add(Integer.toString(advance(image, col * cell, 0, cell, image.getHeight())));
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
