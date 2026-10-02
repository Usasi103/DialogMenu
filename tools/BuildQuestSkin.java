import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/** Extra native UI geometry; existing template glyphs are not regenerated or renumbered. */
public class BuildQuestSkin {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        BuildTemplateSkin.source = project.resolve("design/original-ui/templates");
        BuildTemplateSkin.output = project.resolve("resourcepack/assets/dialogmenu_dialogue");
        Files.createDirectories(BuildTemplateSkin.output.resolve("textures/ui"));
        Files.createDirectories(BuildTemplateSkin.output.resolve("font"));
        for (String theme : List.of("amethyst", "parchment")) {
            for (boolean selected : new boolean[] {false, true}) {
                String suffix = selected ? "-selected" : "";
                for (String name : List.of("quest-row", "quest-tab"))
                    BuildTemplateSkin.register(theme + "." + name + suffix,
                            BuildTemplateSkin.source(theme, name + suffix), 1);
            }
            BuildTemplateSkin.register(theme + ".quest-divider", BuildTemplateSkin.source(theme, "quest-divider"), 1);
            for (int step = 0; step <= 20; step++) {
                String name = "quest-progress-" + step;
                BuildTemplateSkin.register(theme + "." + name, BuildTemplateSkin.source(theme, name), 2);
            }
        }
        Files.writeString(BuildTemplateSkin.output.resolve("font/quest_ui.json"),
                "{\"providers\":[" + String.join(",", BuildTemplateSkin.providers) + "]}\n");
        Files.writeString(project.resolve("src/main/resources/quest-skins.properties"),
                BuildTemplateSkin.metrics, StandardCharsets.UTF_8);
        System.out.println("Compiled independent quest UI font.");
    }
}
