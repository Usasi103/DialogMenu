package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RichMenuTextTest {

    private final MenuTranslations translations =
            new MenuTranslations(
                    "zh_cn",
                    Kt.mapOf(
                            "zh_cn",
                            Kt.mapOf(
                                    "title", "菜单",
                                    "hello", "你好 <arg:0>",
                                    "nested", "<l10n:title>"),
                            "en_us",
                            Kt.mapOf("title", "Menu", "hello", "Hello <arg:0>"),
                            "fr",
                            Kt.mapOf("title", "Menu français")));

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static MeasuredText measure(RichMenuText text, String value) {
        return text.measure(value, DialogCanvas.LABEL_FONT, 8, false);
    }

    @Test
    @DisplayName("local i18n and l10n use independent locales and fallback without CE")
    void localI18nAndL10nUseIndependentLocalesAndFallbackWithoutCe() {
        RichMenuText text = new RichMenuText(translations, Locale.US);
        assertEquals("菜单 / Menu", plain(text.component("<i18n:title> / <l10n:title>")));
        assertEquals("Hello Alex", plain(text.component("<l10n:hello:Alex>")));
        assertEquals("Menu", plain(text.component("<i18n:nested>")));
        assertEquals(
                "菜单",
                plain(new RichMenuText(translations, Locale.JAPAN).component("<l10n:title>")));
        assertEquals(
                "Menu français",
                plain(
                        new RichMenuText(translations, Locale.CANADA_FRENCH)
                                .component("<l10n:title>")));
        assertEquals("missing", plain(text.component("<l10n:missing>")));
    }

    @Test
    @DisplayName("images are measured and clipped as whole glyphs before canvas layout")
    void imagesAreMeasuredAndClippedAsWholeGlyphsBeforeCanvasLayout() {
        List<MenuImageRequest> requests = new ArrayList<>();
        Key imageFont = Key.key("demo:icons");
        RichMenuText rich =
                new RichMenuText(
                        new MenuTranslations(),
                        Locale.ENGLISH,
                        request -> {
                            requests.add(request);
                            return new MenuImage(Component.text("\uE001").font(imageFont), 13);
                        },
                        message -> {});
        MeasuredText value = measure(rich, "A<image:IA:demo:star>B");
        assertEquals(DialogCanvas.textWidth("AB") + 13, value.width());
        assertEquals("A", plain(value.fit(18).component()));
        List<String> wrapped = new ArrayList<>();
        for (MeasuredText line : value.wrap(13)) {
            wrapped.add(plain(line.component()));
        }
        assertEquals(List.of("A", "\uE001", "B"), wrapped);
        Set<MenuImageRequest> distinct = new LinkedHashSet<>(requests);
        assertEquals(1, distinct.size());
        assertEquals("IA", distinct.iterator().next().provider());
        MeasuredText big = rich.measure("<image:CE:demo:star>", TitleFont.font(16), 16, true);
        assertEquals(13, big.width());
        assertEquals("\uE001", plain(big.component()));
        assertEquals(imageFont, big.component().font());
        assertEquals(
                new MenuImageRequest("CE", "demo:star", 1, 2),
                RichMenuText.imageRequest(List.of("demo", "star", "1", "2")));
    }

    @Test
    @DisplayName("canvas retains exact cursor width and click event after translated image")
    void canvasRetainsExactCursorWidthAndClickEventAfterTranslatedImage() {
        RichMenuText rich =
                new RichMenuText(
                        translations,
                        Locale.US,
                        request ->
                                new MenuImage(
                                        Component.text("\uE001").font(Key.key("demo:icons")), 13),
                        message -> {});
        DialogCanvas canvas =
                new DialogCanvas(
                        MenuTheme.DARK,
                        DialogCanvas.WIDTH,
                        1,
                        DialogCanvas.LABEL_FONT,
                        DialogCanvas.BUTTON_LABEL_FONT,
                        rich,
                        it -> ClickEvent.runCommand("/example"));
        canvas.text(10, 0, "<image:demo:star><l10n:title>", null, "go", false, 8, false);
        Component root = canvas.build();
        Cursor cursor = new Cursor();
        cursor.visit(root, null, null);
        assertTrue(cursor.foundImage);
        assertEquals((float) DialogCanvas.LINE_WIDTH, cursor.width);
    }

    private static final class Cursor {
        float width = 0f;
        boolean foundImage = false;

        void visit(Component component, Key font, ClickEvent click) {
            Key currentFont = component.font() != null ? component.font() : font;
            ClickEvent currentClick =
                    component.clickEvent() != null ? component.clickEvent() : click;
            if (component instanceof TextComponent text) {
                for (int cp : text.content().codePoints().toArray()) {
                    if (DialogCanvas.FONT.equals(currentFont)) {
                        width += (float) (cp - 0xEA00);
                    } else if (Key.key("demo:icons").equals(currentFont)) {
                        foundImage = true;
                        assertNotNull(currentClick);
                        width += 13f;
                    } else {
                        width +=
                                LabelMetrics.width(
                                        new String(Character.toChars(cp)),
                                        DialogCanvas.LABEL_FONT,
                                        false);
                    }
                }
            }
            for (Component child : component.children()) {
                visit(child, currentFont, currentClick);
            }
        }
    }

    @Test
    @DisplayName("old text stays literal while translation formatting is retained")
    void oldTextStaysLiteralWhileTranslationFormattingIsRetained() {
        RichMenuText rich =
                new RichMenuText(
                        new MenuTranslations(
                                "en_us", Kt.mapOf("en_us", Kt.mapOf("color", "<red>Red</red>"))));
        assertEquals("<red>literal</red>", plain(rich.component("<red>literal</red>")));
        MeasuredText value = measure(rich, "<i18n:color>");
        assertEquals("Red", plain(value.component()));
        assertEquals(NamedTextColor.RED, value.glyphs().get(0).component().color());
    }

    @Test
    @DisplayName(
            "cycles and missing plugins give bounded visible fallback without raw network tags")
    void cyclesAndMissingPluginsGiveBoundedVisibleFallbackWithoutRawNetworkTags() {
        List<String> warnings = new ArrayList<>();
        RichMenuText rich =
                new RichMenuText(
                        new MenuTranslations(
                                "en_us", Kt.mapOf("en_us", Kt.mapOf("loop", "<i18n:loop>"))),
                        Locale.ENGLISH,
                        request -> null,
                        warnings::add);
        assertFalse(plain(rich.component("<i18n:loop>")).contains("<i18n:"));
        assertEquals("[image:demo:star]", plain(rich.component("<image:IA:demo:star>")));
        assertFalse(warnings.isEmpty());
        assertFalse(plain(rich.component("<image:demo:star:-1>")).contains("<image:"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RichMenuText.imageRequest(List.of("IA", "demo", "star", "0")));
    }

    @Test
    @DisplayName("documented menu parses and renders translated buttons without raw tags")
    void documentedMenuParsesAndRendersTranslatedButtonsWithoutRawTags(@TempDir Path directory)
            throws Exception {
        MenuTranslations.initialize(directory.toFile());
        String source = Files.readString(Path.of("docs/wiki/examples/text-tags.yml"));
        MenuCatalog catalog =
                MenuCatalogParser.parse(
                        "Version: 3\nDefaultMenu: text-tags\n", Kt.mapOf("text-tags", source));
        DialogTemplate template = catalog.templates().get("text-tags/main");
        RichMenuText rich = new RichMenuText(MenuTranslations.read(directory.toFile()), Locale.US);
        Map<String, String> none = Collections.emptyMap();
        DialogCanvas canvas =
                TemplateRenderer.render(
                        template,
                        none,
                        it -> TemplateRenderer.expand(it, none, "Alex", "uuid"),
                        rich,
                        it -> ClickEvent.runCommand("/close"));
        String output = plain(canvas.build());
        assertTrue(output.contains("Hello, Alex!"));
        assertTrue(output.contains("Close"));
        assertFalse(output.contains("<l10n:"));
        assertEquals(1, canvas.hits().size());
    }

    @Test
    @DisplayName("bitmap metrics trim transparency and select the requested cell")
    void bitmapMetricsTrimTransparencyAndSelectTheRequestedCell() {
        BufferedImage png = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        png.setRGB(7, 4, -1);
        png.setRGB(31, 4, -1);
        assertEquals(9, MenuImages.bitmapAdvance(png, 16, 1, 2, 0, 0));
        assertEquals(17, MenuImages.bitmapAdvance(png, 16, 1, 2, 0, 1));
        assertEquals(5, MenuImages.bitmapAdvance(png, 8, 1, 2, 0, 0));
    }

    @Test
    @DisplayName("translation files load nested keys and reject invalid reload input")
    void translationFilesLoadNestedKeysAndRejectInvalidReloadInput(@TempDir Path directory)
            throws Exception {
        MenuTranslations.initialize(directory.toFile());
        File file = directory.resolve("translations/zh_cn.yml").toFile();
        assertEquals("关闭", MenuTranslations.read(directory.toFile()).get("demo.close", null));
        Files.writeString(file.toPath(), "demo:\n  close: 123\n");
        assertThrows(
                IllegalArgumentException.class, () -> MenuTranslations.read(directory.toFile()));
    }
}
