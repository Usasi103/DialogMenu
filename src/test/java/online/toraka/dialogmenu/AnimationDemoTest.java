package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

class AnimationDemoTest {
    @Test
    void finitePresetsHaveCorrectEndpointsAndRejectNonFiniteInput() {
        for (AnimationPreset preset : AnimationPreset.values()) {
            boolean entrance = preset.id().endsWith("_in");
            boolean exit = preset.id().endsWith("_out");
            assertEquals(!entrance, preset.frame(0).visible(), preset.id());
            assertEquals(!exit, preset.frame(1).visible(), preset.id());
            assertEquals(preset.frame(0), preset.frame(-10));
            assertEquals(preset.frame(1), preset.frame(10));
            assertThrows(IllegalArgumentException.class, () -> preset.frame(Double.NaN));
            assertThrows(
                    IllegalArgumentException.class, () -> preset.frame(Double.POSITIVE_INFINITY));
            assertEquals(preset, AnimationPreset.parse(preset.id()));
            for (int phase = 0; phase <= 255; phase++) {
                var frame = preset.frame(phase / 255.0);
                assertTrue(frame.opacity() >= 0 && frame.opacity() <= 1);
                assertTrue(frame.scale() >= 0 && frame.scale() <= 1.2);
                assertTrue(Math.abs(frame.x()) <= 16 && Math.abs(frame.y()) <= 8);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> AnimationPreset.parse("wipe"));
        assertEquals(0, AnimationDemo.progress(10, 5));
        assertEquals(1, AnimationDemo.progress(30, 5));
        assertEquals(1, AnimationDemo.progress(1000, 5));
    }

    @Test
    void allFramesKeepLayoutAndTooltipsAndHideInvisibleClickTargets() throws Exception {
        Path output =
                Path.of(
                        System.getProperty("user.home"),
                        ".gradle-builds",
                        "DialogMenu-animation-frames");
        Files.createDirectories(output);
        for (AnimationPreset preset : AnimationPreset.values())
            for (int tick = 0; tick <= 30; tick++) {
                DialogCanvas canvas =
                        AnimationDemo.render(
                                preset,
                                tick,
                                "演示",
                                action -> DialogClicks.custom(Key.key("test", action)));
                for (int icon = 0; icon < 6; icon++) {
                    String action = "icon/" + icon;
                    assertEquals(
                            preset.frame(AnimationDemo.progress(tick, icon)).visible(),
                            canvas.hits().stream().anyMatch(hit -> hit.action().equals(action)));
                }
                for (var a : canvas.hits()) {
                    assertTrue(a.x() >= 0 && a.x() + a.width() <= AnimationDemo.WIDTH);
                    assertTrue(a.row() >= 0 && a.row() + a.rows() <= AnimationDemo.ROWS);
                    for (var b : canvas.hits())
                        if (!a.action().equals(b.action()))
                            assertFalse(
                                    a.x() < b.x() + b.width()
                                            && b.x() < a.x() + a.width()
                                            && a.row() < b.row() + b.rows()
                                            && b.row() < a.row() + a.rows());
                }
                Component frame = canvas.build();
                assertEquals(
                        18,
                        frame.children().stream()
                                .filter(
                                        c ->
                                                c instanceof TextComponent text
                                                        && text.content().equals("\n"))
                                .count());
                for (Component part : frame.children())
                    if (part.clickEvent() != null) assertNotNull(part.hoverEvent());
                if (preset == AnimationPreset.FADE_IN)
                    Files.writeString(
                            output.resolve("frame-" + tick + ".json"),
                            GsonComponentSerializer.gson().serialize(frame));
            }
    }

    @Test
    void oneDefinitionPerExistingTextureAndNoPngCopies() throws Exception {
        Path assets = Path.of("resourcepack/assets/dialogmenu_animation");
        var providers =
                JsonParser.parseString(Files.readString(assets.resolve("font/icons.json")))
                        .getAsJsonObject()
                        .getAsJsonArray("providers");
        assertEquals(7, providers.size());
        var spaces = providers.get(0).getAsJsonObject().getAsJsonObject("advances");
        assertEquals(-3145728, spaces.get("\uE000").getAsInt());
        assertEquals(3145728, spaces.get("\uE001").getAsInt());
        List<String> icons =
                List.of("diamond", "emerald", "book", "ender_pearl", "golden_apple", "nether_star");
        for (int i = 0; i < 6; i++) {
            var provider = providers.get(i + 1).getAsJsonObject();
            assertEquals(
                    "minecraft:item/" + icons.get(i) + ".png", provider.get("file").getAsString());
            assertEquals(36, provider.get("height").getAsInt());
            assertEquals(7, provider.get("ascent").getAsInt());
        }
        try (var files = Files.walk(assets)) {
            assertFalse(files.anyMatch(p -> p.toString().endsWith(".png")));
        }
    }
}
