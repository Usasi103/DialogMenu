package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingsDemoTest {

    private static Map<String, String> sources() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String id : CatalogRepository.defaults()) {
            sources.put(id, MenuRepository.resource("catalog/menus/" + id + ".yml"));
        }
        return sources;
    }

    private static MenuDefinition demo() {
        return Objects.requireNonNull(
                MenuCatalogParser.parse(MenuRepository.resource("catalog/config.yml"), sources())
                        .menus()
                        .get("demo-settings")
                        .settings());
    }

    @Test
    @DisplayName("all demo controls have working independent states without Bukkit or plugins")
    void allDemoControlsHaveWorkingIndependentStatesWithoutBukkitOrPlugins() {
        MenuDefinition menu = demo();
        assertTrue(menu.demo());
        SettingsDemoSession first = new SettingsDemoSession(menu, new MenuPreferences());
        SettingsDemoSession otherPlayer = new SettingsDemoSession(menu, new MenuPreferences());
        List<MenuWidget> controls =
                menu.pages().values().stream()
                        .flatMap(it -> it.widgets().stream())
                        .filter(it -> !it.state().isEmpty())
                        .toList();
        assertEquals(11, controls.size());
        for (MenuWidget widget : controls) {
            String initial = first.state(widget.state());
            assertNotNull(initial);
            if (widget.kind() == WidgetKind.TOGGLE) {
                assertTrue(first.apply(widget.action()));
                assertEquals("false", first.state(widget.state()));
                assertEquals(initial, otherPlayer.state(widget.state()));
                assertTrue(first.apply(widget.action()));
                assertEquals(initial, first.state(widget.state()));
            } else {
                for (MenuOption option : widget.options()) {
                    assertTrue(first.apply(option.action()));
                    assertEquals(option.value(), first.state(widget.state()));
                    assertEquals(initial, otherPlayer.state(widget.state()));
                }
            }
        }
        assertEquals(MenuLanguage.ENGLISH, first.preferences().language());
        assertEquals(MenuTheme.LIGHT, first.preferences().theme());
        assertEquals(new MenuPreferences(), otherPlayer.preferences());
        assertEquals(
                new MenuPreferences(),
                new SettingsDemoSession(menu, new MenuPreferences()).preferences());
        assertFalse(first.apply("unknown-action"));
    }

    @Test
    @DisplayName("demo navigation retains choices but reopening and other menus do not share them")
    void demoNavigationRetainsChoicesButReopeningAndOtherMenusDoNotShareThem() {
        MenuDefinition menu = demo();
        SettingsDemoSession session = new SettingsDemoSession(menu, new MenuPreferences());
        MenuDialog.View view = new MenuDialog.View("sound", -1, "demo-settings", session);
        MenuWidget toggle =
                menu.pages().get("sound").widgets().stream()
                        .filter(it -> it.kind() == WidgetKind.TOGGLE)
                        .findFirst()
                        .orElseThrow();
        session.apply(toggle.action());
        MenuDialog.View next = view.withPage("particles").toggleDropdown(3).collapsed();
        assertSame(session, next.demo());
        assertEquals("false", Objects.requireNonNull(next.demo()).state(toggle.state()));
        assertEquals(
                "true", new SettingsDemoSession(menu, new MenuPreferences()).state(toggle.state()));
        assertNull(new MenuDialog.View("sound").demo());
    }

    @Test
    @DisplayName("demo does not expose unavailable data or allow unbound business commands")
    void demoDoesNotExposeUnavailableDataOrAllowUnboundBusinessCommands() {
        Map<String, String> sources = sources();
        String demo = sources.get("demo-settings");
        assertFalse(Pattern.compile("%[a-zA-Z0-9_:.\\-]+%").matcher(demo).find());
        Map<String, String> changed = new LinkedHashMap<>(sources);
        changed.put(
                "demo-settings",
                demo.replace("MainMenu: [\"close\"]", "MainMenu: [\"console: say unintended\"]"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MenuCatalogParser.parse(
                                MenuRepository.resource("catalog/config.yml"), changed));
    }
}
