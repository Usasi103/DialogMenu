import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Standalone compact switches; leaves the existing UI font and its metrics untouched. */
public class BuildSwitchSkin {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).resolve("resourcepack/assets/toraka_settings");
        List<String> providers = new ArrayList<>();
        for (int theme = 0; theme < 2; theme++) {
            for (int state = 0; state < 3; state++) {
                boolean light = theme == 1;
                BufferedImage image = new BufferedImage(36, 18, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                fill(g, 0, 0, 36, 18, 0x101010);
                fill(g, 1, 1, 34, 16, light ? 0x909090 : 0x707070);
                fill(g, 2, 2, 32, 14, light ? 0xC8C8C8 : 0x303030);
                fill(g, 17, 1, 2, 16, 0x101010);
                if (state != 2) {
                    int x = state == 0 ? 1 : 19;
                    fill(g, x, 1, 16, 16, state == 0 ? 0xCEFFA0 : 0xB0B0B0);
                    fill(g, x + 1, 2, 14, 14, state == 0 ? 0x66BD31 : 0x6E6E6E);
                    fill(g, x + 2, 14, 13, 2, state == 0 ? 0x438522 : 0x494949);
                    if (state == 0) {
                        fill(g, x + 8, 5, 2, 9, 0x315C1C);
                        fill(g, x + 7, 4, 2, 9, 0xF1FFDF);
                    } else {
                        fill(g, x + 5, 5, 6, 8, 0xE6E6E6);
                        fill(g, x + 7, 7, 2, 4, 0x6E6E6E);
                    }
                } else {
                    fill(g, 6, 8, 6, 2, light ? 0x888888 : 0x555555);
                    fill(g, 24, 8, 6, 2, light ? 0x888888 : 0x555555);
                }
                g.dispose();
                String name = "switch_" + new String[] {"on", "off", "unavailable"}[state]
                        + (light ? "_light" : "");
                ImageIO.write(image, "png", root.resolve("textures/ui/" + name + ".png").toFile());
                providers.add("{\"type\":\"bitmap\",\"file\":\"toraka_settings:ui/" + name
                        + ".png\",\"height\":18,\"ascent\":7,\"chars\":[\""
                        + String.format("\\u%04x", 0xE700 + theme * 3 + state) + "\"]}");
            }
        }
        Files.writeString(root.resolve("font/switches.json"),
                "{\"providers\":[" + String.join(",", providers) + "]}\n", StandardCharsets.UTF_8);
        System.out.println("Compiled six switch glyphs: 36 x 18, advance 37.");
    }

    private static void fill(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb));
        g.fillRect(x, y, w, h);
    }
}
