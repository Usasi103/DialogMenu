package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MenuPreferencesTest {
    @Test
    void readsPreferencesSavedByPlayerSettings() {
        PersistentDataContainer data = container();
        data.set(
                new NamespacedKey("playersettings", "menu_language"),
                PersistentDataType.STRING,
                "en_us");
        data.set(
                new NamespacedKey("playersettings", "menu_theme"),
                PersistentDataType.STRING,
                "light");
        assertEquals(
                new MenuPreferences(MenuLanguage.ENGLISH, MenuTheme.LIGHT),
                MenuPreferences.read(data));
        new MenuPreferences(MenuLanguage.CHINESE, MenuTheme.DARK).save(data);
        assertEquals(
                "zh_cn",
                data.get(
                        new NamespacedKey("playersettings", "menu_language"),
                        PersistentDataType.STRING));
        assertEquals(
                "dark",
                data.get(
                        new NamespacedKey("playersettings", "menu_theme"),
                        PersistentDataType.STRING));
    }

    private PersistentDataContainer container() {
        Map<NamespacedKey, Object> values = new LinkedHashMap<>();
        return (PersistentDataContainer)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {PersistentDataContainer.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "get" -> {
                                    return values.get(args[0]);
                                }
                                case "set" -> {
                                    values.put((NamespacedKey) args[0], args[2]);
                                    return null;
                                }
                                default ->
                                        throw new IllegalStateException(
                                                "Unexpected PDC operation " + method.getName());
                            }
                        });
    }

    @Test
    @DisplayName(
            "preferences survive reads without sharing state or overwriting unrelated settings")
    void preferencesSurviveReadsWithoutSharingStateOrOverwritingUnrelatedSettings() {
        PersistentDataContainer first = container();
        PersistentDataContainer second = container();
        NamespacedKey pickupKey = new NamespacedKey("pickupnotifier", "disabled");
        first.set(pickupKey, PersistentDataType.BYTE, (byte) 1);
        assertEquals(new MenuPreferences(), MenuPreferences.read(first));
        new MenuPreferences(MenuLanguage.ENGLISH, MenuTheme.LIGHT).save(first);
        assertEquals(
                new MenuPreferences(MenuLanguage.ENGLISH, MenuTheme.LIGHT),
                MenuPreferences.read(first));
        assertEquals(new MenuPreferences(), MenuPreferences.read(second));
        MenuPreferences.read(first).withLanguage(MenuLanguage.CHINESE).save(first);
        assertEquals(
                new MenuPreferences(MenuLanguage.CHINESE, MenuTheme.LIGHT),
                MenuPreferences.read(first));
        assertEquals(Byte.valueOf((byte) 1), first.get(pickupKey, PersistentDataType.BYTE));
        first.set(
                new NamespacedKey("playersettings", "menu_theme"),
                PersistentDataType.STRING,
                "retired");
        assertEquals(MenuTheme.DARK, MenuPreferences.read(first).theme());
    }

    @Test
    @DisplayName("menu translations are complete and selections retain readable labels")
    void menuTranslationsAreCompleteAndSelectionsRetainReadableLabels() {
        assertEquals(MenuText.keys(MenuLanguage.CHINESE), MenuText.keys(MenuLanguage.ENGLISH));
        for (MenuLanguage language : MenuLanguage.values()) {
            assertTrue(DialogCanvas.textWidth(MenuText.get(language, "search.button")) <= 76);
            for (MenuPage tab : MenuRuntime.current().pages().values()) {
                String label = MenuRuntime.current().text(language, tab.label());
                assertTrue(Kt.isNotBlank(label));
                assertTrue(DialogCanvas.textWidth(label) <= 76, label);
            }
        }
        assertEquals("On", MenuDialog.toggleLabel("开", MenuLanguage.ENGLISH));
        assertEquals("Medium", MenuDialog.densityLabel("中", MenuLanguage.ENGLISH));
        assertEquals(
                "Level: %playerlevel_level%  Ping: {ping} ms",
                MenuText.get(MenuLanguage.ENGLISH, "profile.level", "{1}", 30));
        assertEquals("appearance", MenuDialog.findTab("切换语言"));
        assertEquals("appearance", MenuDialog.findTab("Light theme"));
    }

    @Test
    @DisplayName("both themes have identical hit regions line widths and glyph advances")
    void bothThemesHaveIdenticalHitRegionsLineWidthsAndGlyphAdvances() {
        List<DialogCanvas.Hit> expectedHits = null;
        for (MenuTheme theme : MenuTheme.values()) {
            DialogCanvas canvas =
                    new DialogCanvas(theme, action -> DialogClicks.custom(Key.key("test", action)));
            canvas.sprite(114, 0, DialogCanvas.PANEL_TOP);
            canvas.sprite(114, 10, DialogCanvas.PANEL_BOTTOM);
            int index = 0;
            for (MenuPage tab : MenuRuntime.current().pages().values()) {
                canvas.button(
                        0,
                        3 + index * 2,
                        DialogCanvas.NAV,
                        MenuRuntime.current().text(MenuLanguage.ENGLISH, tab.label()),
                        "tab_" + tab.id());
                index++;
            }
            canvas.button(330, 3, DialogCanvas.CONTROL, "简体中文", "language_zh_cn");
            canvas.button(330, 5, DialogCanvas.SELECTED_CONTROL, "English", "language_en_us");
            canvas.button(330, 13, DialogCanvas.CONTROL, "Dark", "theme_dark");
            canvas.button(330, 15, DialogCanvas.SELECTED_CONTROL, "Light", "theme_light");
            if (expectedHits == null) {
                expectedHits = new ArrayList<>(canvas.hits());
            } else {
                assertEquals(expectedHits, canvas.hits());
            }
            int width = 0;
            int lines = 1;
            for (Component component : canvas.build().children()) {
                TextComponent part = (TextComponent) component;
                if (part.content().equals("\n")) {
                    assertEquals(DialogCanvas.LINE_WIDTH, width);
                    width = 0;
                    lines++;
                } else if (DialogCanvas.FONT.equals(part.font())) {
                    int code = singleCode(part.content());
                    width +=
                            code >= 0xE800 && code <= 0xEC00
                                    ? code - 0xEA00
                                    : DialogCanvas.glyphWidth(code);
                } else {
                    width += NativeMenuFontTest.width(part);
                }
                assertTrue(
                        width >= 0 && width <= DialogCanvas.LINE_WIDTH,
                        "theme=" + theme + " line=" + lines + " x=" + width);
            }
            assertEquals(DialogCanvas.LINE_WIDTH, width);
            assertEquals(29, lines);
        }
    }

    /** Kotlin {@code String.single().code}. */
    private static int singleCode(String text) {
        if (text.isEmpty()) {
            throw new NoSuchElementException("Char sequence is empty.");
        }
        if (text.length() > 1) {
            throw new IllegalArgumentException("Char sequence has more than one element.");
        }
        return text.charAt(0);
    }
}
