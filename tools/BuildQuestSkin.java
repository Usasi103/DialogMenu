import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/** Extra native UI geometry; existing template glyphs are not regenerated or renumbered. */
public class BuildQuestSkin {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        BuildTemplateSkin.output = project.resolve("resourcepack/assets/dialogmenu_dialogue");
        Files.createDirectories(BuildTemplateSkin.output.resolve("textures/ui"));
        Files.createDirectories(BuildTemplateSkin.output.resolve("font"));
        for (String theme : List.of("amethyst", "parchment")) {
            boolean wood = theme.equals("parchment");
            for (boolean selected : new boolean[] {false, true}) {
                String suffix = selected ? "-selected" : "";
                BuildTemplateSkin.register(theme + ".quest-row" + suffix,
                        BuildTemplateSkin.button(null, 180, wood, selected), 1);
                BuildTemplateSkin.register(theme + ".quest-tab" + suffix,
                        BuildTemplateSkin.button(null, 54, wood, selected), 1);
            }
            BufferedImage divider = new BufferedImage(3, 108, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = divider.createGraphics();
            g.setColor(new Color(wood ? 0x9b8065 : 0x594071));
            g.drawLine(1, 0, 1, 107);
            g.dispose();
            BuildTemplateSkin.register(theme + ".quest-divider", divider, 1);
            for (int step = 0; step <= 20; step++) {
                BufferedImage bar = new BufferedImage(312, 9, BufferedImage.TYPE_INT_ARGB);
                g = bar.createGraphics();
                g.setColor(new Color(wood ? 0x624633 : 0x21142c));
                g.fillRect(0, 2, 312, 5);
                g.setColor(new Color(wood ? 0x9b8065 : 0x594071));
                g.drawRect(0, 2, 311, 4);
                g.setColor(new Color(wood ? 0xe1bf8f : 0xaa86c5));
                g.fillRect(1, 3, 310 * step / 20, 3);
                g.dispose();
                BuildTemplateSkin.register(theme + ".quest-progress-" + step, bar, 2);
            }
        }
        Files.writeString(BuildTemplateSkin.output.resolve("font/quest_ui.json"),
                "{\"providers\":[" + String.join(",", BuildTemplateSkin.providers) + "]}\n");
        Files.writeString(project.resolve("src/main/resources/quest-skins.properties"),
                BuildTemplateSkin.metrics, StandardCharsets.UTF_8);
        System.out.println("Compiled independent quest UI font.");
    }
}
