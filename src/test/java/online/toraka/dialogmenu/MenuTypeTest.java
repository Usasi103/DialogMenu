package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MenuTypeTest {
    private static final String CANVAS =
            """
            MenuType: dialog
            Version: 1
            Type: canvas
            Pages:
              main:
                Elements:
                  greeting:
                    Type: text
                    Position: [24, 3]
                    Width: 200
                    Rows: 2
                    Text: hello
            """;

    private static MenuCatalog catalog(String source) {
        return MenuCatalogParser.parse(
                "Version: 3\nDefaultMenu: sample\n", Map.of("sample", source));
    }

    @Test
    void headerIsSingleRequiredBackendAndFirstConfigKey() {
        for (String source :
                List.of(
                        "Version: 1\n",
                        "MenuType: [dialog, fullscreen]\n",
                        "MenuType: {dialog: true}\n",
                        "MenuType: unknown\n",
                        "MenuType: true\n",
                        "Version: 1\nMenuType: dialog\n",
                        "MenuType: dialog\nMenuType: fullscreen\n")) {
            var error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> MenuType.parse(source, MenuType.DIALOG, "menus/test.yml"));
            assertTrue(error.getMessage().contains("menus/test.yml"));
        }
        assertDoesNotThrow(
                () -> MenuType.parse("# comment\nMenuType: dialog\n", MenuType.DIALOG, "menu.yml"));
        assertDoesNotThrow(
                () -> MenuType.parse("MenuType: fullscreen\n", MenuType.FULLSCREEN, "menu.yml"));
        assertThrows(
                IllegalArgumentException.class,
                () -> MenuType.parse("MenuType: dialog\n", MenuType.FULLSCREEN, "menu.yml"));
    }

    @Test
    void dialogMenusCannotDispatchFullscreenOrOverridePagesAndControls() {
        assertDoesNotThrow(() -> catalog(CANVAS));
        for (String source :
                List.of(
                        CANVAS.replace("MenuType: dialog\n", ""),
                        CANVAS.replace("MenuType: dialog", "MenuType: fullscreen"),
                        CANVAS.replace("  main:\n", "  main:\n    MenuType: fullscreen\n"),
                        CANVAS.replace("Type: text", "MenuType: fullscreen\n        Type: text"),
                        CANVAS.replace("Type: text", "Type: fullscreen-button"),
                        CANVAS + "Cursor: local\n")) {
            assertThrows(IllegalArgumentException.class, () -> catalog(source));
        }
    }

    @Test
    void everyDialogEntryValidatesBackendBeforeInterpretingLayout() {
        String wrong = "MenuType: fullscreen\n";
        assertThrows(IllegalArgumentException.class, () -> TemplateParser.parse("test", wrong));
        assertThrows(IllegalArgumentException.class, () -> SimpleMenuParser.parse(wrong, id -> ""));
        assertThrows(IllegalArgumentException.class, () -> MenuConfigParser.parse(wrong, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QuestDemoCompiler.compile(
                                MenuConfigParser.yaml(wrong, "quest.yml"), "quest.yml"));
    }

    @Test
    void everyBundledMenuDeclaresItsBackendAndBothTypesCanShareACatalog() {
        Map<String, String> menus = new LinkedHashMap<>();
        for (String id : CatalogRepository.defaults()) {
            String source = MenuRepository.resource("catalog/menus/" + id + ".yml");
            assertTrue(
                    source.startsWith(
                            "MenuType: "
                                    + (id.equals("demo-fullscreen") ? "fullscreen" : "dialog")));
            menus.put(id, source);
        }
        assertEquals(
                5,
                MenuCatalogParser.parse(MenuRepository.resource("catalog/config.yml"), menus)
                        .menus()
                        .size());
    }

    @Test
    void fullscreenLayoutRejectsDialogFieldsAndDialogCanLinkToFullscreen() {
        String fullscreen = "MenuType: fullscreen\nPreset: diagnostic\n";
        var menu = catalog(fullscreen).menus().get("sample");
        assertEquals(MenuType.FULLSCREEN, menu.menuType());
        assertEquals(java.util.Set.of("main"), menu.pages());
        for (String invalid :
                List.of(
                        fullscreen + "Pages: {}\n",
                        fullscreen + "Type: canvas\n",
                        fullscreen + "Elements: {}\n",
                        fullscreen.replace("diagnostic", "unknown"))) {
            assertThrows(IllegalArgumentException.class, () -> catalog(invalid));
        }
        String canvas =
                CANVAS.replace("Type: text", "Type: button")
                        .replace(
                                "Text: hello",
                                "Text: hello\n        Actions: ['open: fullscreen']");
        var combined =
                MenuCatalogParser.parse(
                        "Version: 3\nDefaultMenu: sample\n",
                        Map.of("sample", canvas, "fullscreen", fullscreen));
        assertEquals(MenuType.DIALOG, combined.menus().get("sample").menuType());
        assertEquals(MenuType.FULLSCREEN, combined.menus().get("fullscreen").menuType());
    }
}
