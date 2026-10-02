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
        Path root = Path.of(args[0]).resolve("resourcepack/assets/dialogmenu_settings");
        List<String> providers = new ArrayList<>();
        for (int theme = 0; theme < 2; theme++) {
            for (int state = 0; state < 3; state++) {
                boolean light = theme == 1;
                String name = "switch_" + new String[] {"on", "off", "unavailable"}[state]
                        + (light ? "_light" : "");
                BufferedImage image = ImageIO.read(Path.of(args[0]).resolve("design/original-ui/settings/" + name + ".png").toFile());
                if (image.getWidth() != 36 || image.getHeight() != 18)
                    throw new IllegalArgumentException("Switch must be 36 x 18: " + name);
                ImageIO.write(image, "png", root.resolve("textures/ui/" + name + ".png").toFile());
                providers.add("{\"type\":\"bitmap\",\"file\":\"dialogmenu_settings:ui/" + name
                        + ".png\",\"height\":18,\"ascent\":7,\"chars\":[\""
                        + String.format("\\u%04x", 0xE700 + theme * 3 + state) + "\"]}");
            }
        }
        Files.writeString(root.resolve("font/switches.json"),
                "{\"providers\":[" + String.join(",", providers) + "]}\n", StandardCharsets.UTF_8);
        System.out.println("Compiled six switch glyphs: 36 x 18, advance 37.");
    }

}
