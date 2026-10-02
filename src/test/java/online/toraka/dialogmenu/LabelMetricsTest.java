package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LabelMetricsTest {

    @Test
    @DisplayName("existing custom menu sprite fonts resolve to the renamed pack")
    void existingCustomMenuSpriteFontsResolveToTheRenamedPack() {
        String yaml =
                MenuRepository.resource("catalog/menus/demo-boss.yml")
                        .replace("dialogmenu_dialogue:rewards", "toraka_dialogue:rewards");
        Map<String, String> sources = new LinkedHashMap<>();
        for (String id : CatalogRepository.defaults()) {
            sources.put(
                    id,
                    id.equals("demo-boss")
                            ? yaml
                            : MenuRepository.resource("catalog/menus/" + id + ".yml"));
        }
        MenuCatalog catalog =
                MenuCatalogParser.parse(MenuRepository.resource("catalog/config.yml"), sources);
        List<DialogCanvas.Skin> rewards = new ArrayList<>();
        for (TemplateElement element : catalog.templates().get("demo-boss/intro").elements()) {
            if (element.sprite() != null && element.sprite().font().value().equals("rewards")) {
                rewards.add(element.sprite());
            }
        }
        assertEquals(3, rewards.size());
        assertTrue(
                rewards.stream()
                        .allMatch(skin -> skin.font().namespace().equals("dialogmenu_dialogue")));
        assertEquals(Key.key("custom:icon"), ResourceFont.resourceFont("custom:icon"));
    }

    @Test
    @DisplayName("currency symbols and mixed bold fonts use the actual client advances")
    void currencySymbolsAndMixedBoldFontsUseTheActualClientAdvances() {
        // U+26C2 has 14 ink pixels in the 16px Unihex source: 14 / 2 + 1 = 8.
        assertEquals(8f, LabelMetrics.width("⛂", DialogCanvas.LABEL_FONT, false));
        assertEquals(5f, LabelMetrics.width(" ", DialogCanvas.LABEL_FONT, true));
        assertEquals(9.5f, LabelMetrics.width("\u3000", DialogCanvas.LABEL_FONT, false));
        assertEquals("中", DialogCanvas.fit("中文", 19, 8, true, DialogCanvas.BUTTON_LABEL_FONT));
        assertEquals(34, DialogCanvas.textWidth("3,030⛂"));
        assertEquals(8.5f, LabelMetrics.width("⛂", DialogCanvas.LABEL_FONT, true));
        assertEquals(9.5f, LabelMetrics.width("中", DialogCanvas.LABEL_FONT, true));
        assertEquals(10f, LabelMetrics.width("中", DialogCanvas.BUTTON_LABEL_FONT, true));
        assertEquals("⛂", DialogCanvas.fit("⛂⛂", 8));
    }

    @Test
    @DisplayName("coin row returns to the same origin before drawing the appearance category")
    void coinRowReturnsToTheSameOriginBeforeDrawingTheAppearanceCategory() {
        DialogCanvas canvas = new DialogCanvas(it -> DialogClicks.custom(Key.key("test", it)));
        // Same row as the profile balance and the appearance navigation label.
        canvas.text(123, 14, "金币：3,030⛂");
        canvas.button(0, 13, DialogCanvas.NAV, "界面与语言", "appearance");
        canvas.text(200, 16, "⛂", null, null, false, 8, true);
        canvas.text(20, 16, "中", null, null, false, 8, true);
        Walker walker = new Walker();
        walker.visit(canvas.build());
        assertEquals((float) DialogCanvas.LINE_WIDTH, walker.cursor);
        assertEquals(DialogCanvas.ROWS - 1, walker.row);
    }

    private static final class Walker {
        int row = 0;
        float cursor = 0f;

        void visit(Component component) {
            if (component instanceof TextComponent part) {
                String text = part.content();
                if (text.equals("\n")) {
                    assertEquals((float) DialogCanvas.LINE_WIDTH, cursor, "row " + row);
                    cursor = 0f;
                    row++;
                } else if (DialogCanvas.FONT.equals(component.font())) {
                    for (int cp : text.codePoints().toArray()) {
                        if (cp == 0xE7F0) {
                            cursor += 0.5f;
                        } else if (cp == 0xE7F1) {
                            cursor += -0.5f;
                        } else if (cp >= 0xE800 && cp <= 0xEC00) {
                            cursor += (float) (cp - 0xEA00);
                        } else {
                            cursor += (float) DialogCanvas.glyphWidth(cp);
                        }
                    }
                } else if (!text.isEmpty()) {
                    if (text.equals("界面与语言")) {
                        assertEquals(20f, cursor);
                    }
                    // Independent expected widths for this fixture, not the implementation's
                    // estimator.
                    cursor +=
                            switch (text) {
                                case "金币：3,030⛂" -> 61f;
                                case "界面与语言" -> 45f;
                                case "⛂" -> 8.5f;
                                case "中" -> 9.5f;
                                default -> throw new IllegalStateException(text);
                            };
                }
            }
            for (Component child : component.children()) {
                visit(child);
            }
        }
    }
}
