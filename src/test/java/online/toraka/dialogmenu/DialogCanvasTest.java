package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DialogCanvasTest {

    /** Kotlin {@code CharSequence.single().code}. */
    private static int single(String text) {
        if (text.isEmpty()) {
            throw new java.util.NoSuchElementException("Char sequence is empty.");
        }
        if (text.length() > 1) {
            throw new IllegalArgumentException("Char sequence has more than one element.");
        }
        return text.charAt(0);
    }

    @Test
    @DisplayName("whole surfaces and measured text keep every row at the same origin")
    void wholeSurfacesAndMeasuredTextKeepEveryRowAtTheSameOrigin() throws Exception {
        DialogCanvas canvas = new DialogCanvas(it -> DialogClicks.custom(Key.key("test", it)));
        canvas.sprite(114, 0, DialogCanvas.PANEL_TOP);
        canvas.sprite(114, 10, DialogCanvas.PANEL_BOTTOM);
        canvas.button(0, 0, DialogCanvas.SEARCH, "搜索设置", "search");
        List<String> tabs = List.of("玩家信息", "环境音效", "环境粒子", "拾取提示", "掉落光柱", "使用帮助");
        for (int i = 0; i < 6; i++) {
            canvas.button(
                    0,
                    3 + i * 2,
                    i == 2 ? DialogCanvas.SELECTED_NAV : DialogCanvas.NAV,
                    tabs.get(i),
                    "tab_" + i);
            canvas.sprite(4, 3 + i * 2, new DialogCanvas.Skin(0xE090 + i, 16, 2));
        }
        canvas.text(123, 1, "环境粒子");
        canvas.text(123, 11, "粒子分类与密度");
        for (int row : List.of(4, 13, 15, 17)) {
            canvas.button(
                    330,
                    row,
                    DialogCanvas.CONTROL,
                    row == 4 ? "开启 / 切换" : "高 / 中",
                    "control_" + row);
        }
        canvas.densitySlider(280, 19, "medium", MenuLanguage.CHINESE);
        canvas.text(123, 5, "显示环境粒子");
        canvas.text(123, 7, "仅影响你自己看到的环境粒子。");
        Component root = canvas.build();
        int width = 0;
        int rows = 0;
        List<Integer> glyphs = new ArrayList<>();
        for (Component child : root.children()) {
            TextComponent part = (TextComponent) child;
            if (part.content().equals("\n")) {
                assertEquals(DialogCanvas.LINE_WIDTH, width, "row " + rows + " width");
                width = 0;
                rows++;
            } else if (DialogCanvas.FONT.equals(part.font())) {
                int cp = single(part.content());
                if (cp >= 0xE800 && cp <= 0xEC00) {
                    width += cp - 0xEA00;
                } else {
                    glyphs.add(cp);
                    width += DialogCanvas.glyphWidth(cp);
                }
            } else {
                width += DialogCanvas.textWidth(part.content());
            }
            assertTrue(
                    width >= 0 && width <= DialogCanvas.WIDTH + 2,
                    "client must not wrap at row " + rows + ", x " + width);
        }
        assertEquals(DialogCanvas.LINE_WIDTH, width);
        assertEquals(DialogCanvas.ROWS - 1, rows);
        assertEquals(1, glyphs.stream().filter(it -> it == 0xE000).count());
        assertEquals(1, glyphs.stream().filter(it -> it == 0xE001).count());
        assertFalse(glyphs.stream().anyMatch(it -> it >= 0xE002 && it <= 0xE011));
        assertTrue(
                canvas.hits().stream().allMatch(it -> it.row() + it.rows() <= DialogCanvas.ROWS));
        Path output =
                Path.of(
                        System.getProperty("user.home"),
                        ".gradle-builds",
                        "DialogMenu",
                        "layout-probes");
        Files.createDirectories(output);
        Files.writeString(
                output.resolve("canvas.json"), GsonComponentSerializer.gson().serialize(root));
        Files.writeString(
                output.resolve("layout.properties"),
                "bodyWidth="
                        + DialogCanvas.FRAMELESS_BODY_WIDTH
                        + "\nlineWidth="
                        + DialogCanvas.LINE_WIDTH
                        + "\nrows="
                        + DialogCanvas.ROWS
                        + "\n");
    }

    @Test
    @DisplayName("Chinese and legacy search terms resolve to the correct settings page")
    void chineseAndLegacySearchTermsResolveToTheCorrectSettingsPage() {
        assertEquals("particles", MenuDialog.findTab("环境粒子"));
        assertEquals("particles", MenuDialog.findTab("萤火虫"));
        assertEquals("sound", MenuDialog.findTab("鸟鸣"));
        assertEquals("notices", MenuDialog.findTab("拾取提示"));
        assertEquals("loot", MenuDialog.findTab("掉落光柱"));
        assertEquals("loot", MenuDialog.findTab("掉落音效"));
        assertEquals("profile", MenuDialog.findTab("金币"));
        assertEquals("help", MenuDialog.findTab("资源包"));
        assertEquals("particles", MenuDialog.findTab("density"));
        assertNull(MenuDialog.findTab(""));
    }

    @Test
    @DisplayName("toggle and density values display Chinese without changing underlying ids")
    void toggleAndDensityValuesDisplayChineseWithoutChangingUnderlyingIds() {
        assertEquals("开启", MenuDialog.toggleLabel("ON"));
        assertEquals("关闭", MenuDialog.toggleLabel("disabled"));
        assertEquals("开启", MenuDialog.toggleLabel("开"));
        assertEquals("未接入", MenuDialog.toggleLabel("N/A"));
        assertEquals("中", MenuDialog.densityLabel("medium"));
        assertEquals("高", MenuDialog.densityLabel("high"));
        assertEquals("关闭", MenuDialog.densityLabel("off"));
    }

    @Test
    @DisplayName("hit spans cover both halves of a button without affecting neighbours")
    void hitSpansCoverBothHalvesOfAButtonWithoutAffectingNeighbours() {
        DialogCanvas canvas = new DialogCanvas(it -> DialogClicks.custom(Key.key("test", it)));
        canvas.button(330, 13, DialogCanvas.CONTROL, "开", "firefly");
        Component root = canvas.build();
        List<List<TextComponent>> rows = new ArrayList<>();
        rows.add(new ArrayList<>());
        for (Component child : root.children()) {
            TextComponent text = (TextComponent) child;
            if (text.content().equals("\n")) {
                rows.add(new ArrayList<>());
            } else {
                rows.get(rows.size() - 1).add(text);
            }
        }
        assertEquals(DialogCanvas.ROWS, rows.size());
        for (int row = 0; row < rows.size(); row++) {
            List<TextComponent> parts = rows.get(row);
            List<TextComponent> clickable =
                    parts.stream().filter(it -> it.clickEvent() != null).toList();
            if (row >= 13 && row <= 14) {
                assertFalse(clickable.isEmpty());
                assertTrue(clickable.stream().anyMatch(it -> single(it.content()) - 0xEA00 == 114));
                int prefix = 0;
                for (TextComponent part : parts) {
                    if (part.clickEvent() != null) {
                        break;
                    }
                    prefix += single(part.content()) - 0xEA00;
                }
                assertEquals(330, prefix);
            } else {
                assertTrue(clickable.isEmpty());
            }
        }
    }

    @Test
    @DisplayName("slider track and bounded arrows select density without gaps or row wrapping")
    void sliderTrackAndBoundedArrowsSelectDensityWithoutGapsOrRowWrapping() {
        List<String> ids = List.of("off", "low", "medium", "high");
        List<String> selections = new ArrayList<>(ids);
        selections.add("unavailable");
        for (MenuTheme theme : MenuTheme.values()) {
            for (MenuLanguage language : MenuLanguage.values()) {
                for (String selected : selections) {
                    DialogCanvas canvas =
                            new DialogCanvas(theme, it -> DialogClicks.custom(Key.key("test", it)));
                    canvas.densitySlider(280, 19, selected, language);
                    for (int row = 19; row <= 20; row++) {
                        for (int x = 302; x < 422; x++) {
                            int r = row;
                            int column = x;
                            List<DialogCanvas.Hit> found =
                                    canvas.hits().stream()
                                            .filter(
                                                    it ->
                                                            r >= it.row()
                                                                    && r < it.row() + it.rows()
                                                                    && column >= it.x()
                                                                    && column < it.x() + it.width())
                                            .toList();
                            assertEquals(1, found.size());
                            assertEquals(
                                    "density_" + ids.get((x - 302) / 30), found.get(0).action());
                        }
                    }
                    DialogCanvas.Hit previous = singleOrNull(canvas.hits(), 280);
                    DialogCanvas.Hit next = singleOrNull(canvas.hits(), 426);
                    int index = ids.indexOf(selected);
                    assertEquals(
                            index >= 1 && index <= 3 ? "density_" + ids.get(index - 1) : null,
                            previous != null ? previous.action() : null);
                    assertEquals(
                            index >= 0 && index <= 2 ? "density_" + ids.get(index + 1) : null,
                            next != null ? next.action() : null);
                    int width = 0;
                    int row = 0;
                    for (Component component : canvas.build().children()) {
                        TextComponent part = (TextComponent) component;
                        if (part.content().equals("\n")) {
                            assertEquals(DialogCanvas.LINE_WIDTH, width);
                            width = 0;
                            row++;
                        } else if (DialogCanvas.FONT.equals(part.font())) {
                            int code = single(part.content());
                            width +=
                                    code >= 0xE800 && code <= 0xEC00
                                            ? code - 0xEA00
                                            : DialogCanvas.glyphWidth(code);
                        } else {
                            width += DialogCanvas.textWidth(part.content());
                        }
                        assertTrue(
                                width >= 0 && width <= DialogCanvas.LINE_WIDTH,
                                theme + " " + language + " " + selected + " row=" + row + " x="
                                        + width);
                    }
                    assertEquals(DialogCanvas.LINE_WIDTH, width);
                    assertEquals(28, row);
                }
            }
        }
    }

    /** Kotlin {@code singleOrNull { it.x == x }}: null for none or for more than one. */
    private static DialogCanvas.Hit singleOrNull(List<DialogCanvas.Hit> hits, int x) {
        List<DialogCanvas.Hit> found = hits.stream().filter(it -> it.x() == x).toList();
        return found.size() == 1 ? found.get(0) : null;
    }

    @Test
    @DisplayName("labels remain within the allotted controls")
    void labelsRemainWithinTheAllottedControls() {
        String fitted = DialogCanvas.fit("自然环境粒子与落叶和萤火虫开关", 102);
        assertTrue(DialogCanvas.textWidth(fitted) <= 102);
        assertFalse(fitted.isEmpty());
    }
}
