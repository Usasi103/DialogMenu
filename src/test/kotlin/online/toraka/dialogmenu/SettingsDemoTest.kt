package online.toraka.dialogmenu

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SettingsDemoTest {
    private fun demo(): MenuDefinition =
        MenuCatalogParser.parse(
                MenuRepository.resource("catalog/config.yml"),
                CatalogRepository.defaults.associateWith {
                    MenuRepository.resource("catalog/menus/$it.yml")
                },
            )
            .menus
            .getValue("demo-settings")
            .settings!!

    @Test
    fun `all demo controls have working independent states without Bukkit or plugins`() {
        val menu = demo()
        assertTrue(menu.demo)
        val first = SettingsDemoSession(menu, MenuPreferences())
        val otherPlayer = SettingsDemoSession(menu, MenuPreferences())
        val controls = menu.pages.values.flatMap { it.widgets }.filter { it.state.isNotEmpty() }
        assertEquals(11, controls.size)
        controls.forEach { widget ->
            val initial = first.state(widget.state)
            assertNotNull(initial)
            if (widget.kind == WidgetKind.TOGGLE) {
                assertTrue(first.apply(widget.action))
                assertEquals("false", first.state(widget.state))
                assertEquals(initial, otherPlayer.state(widget.state))
                assertTrue(first.apply(widget.action))
                assertEquals(initial, first.state(widget.state))
            } else {
                widget.options.forEach { option ->
                    assertTrue(first.apply(option.action))
                    assertEquals(option.value, first.state(widget.state))
                    assertEquals(initial, otherPlayer.state(widget.state))
                }
            }
        }
        assertEquals(MenuLanguage.ENGLISH, first.preferences.language)
        assertEquals(MenuTheme.LIGHT, first.preferences.theme)
        assertEquals(MenuPreferences(), otherPlayer.preferences)
        assertEquals(MenuPreferences(), SettingsDemoSession(menu, MenuPreferences()).preferences)
        assertFalse(first.apply("unknown-action"))
    }

    @Test
    fun `demo navigation retains choices but reopening and other menus do not share them`() {
        val menu = demo()
        val session = SettingsDemoSession(menu, MenuPreferences())
        val view = MenuDialog.View("sound", menu = "demo-settings", demo = session)
        val toggle = menu.pages.getValue("sound").widgets.first { it.kind == WidgetKind.TOGGLE }
        session.apply(toggle.action)
        val next = view.copy(page = "particles").toggleDropdown(3).collapsed()
        assertSame(session, next.demo)
        assertEquals("false", next.demo!!.state(toggle.state))
        assertEquals("true", SettingsDemoSession(menu, MenuPreferences()).state(toggle.state))
        assertNull(MenuDialog.View("sound").demo)
    }

    @Test
    fun `demo does not expose unavailable data or allow unbound business commands`() {
        val sources =
            CatalogRepository.defaults.associateWith {
                MenuRepository.resource("catalog/menus/$it.yml")
            }
        val demo = sources.getValue("demo-settings")
        assertFalse(Regex("%[a-zA-Z0-9_:.\\-]+%").containsMatchIn(demo))
        assertThrows(IllegalArgumentException::class.java) {
            MenuCatalogParser.parse(
                MenuRepository.resource("catalog/config.yml"),
                sources +
                    ("demo-settings" to
                        demo.replace(
                            "MainMenu: [\"close\"]",
                            "MainMenu: [\"console: say unintended\"]",
                        )),
            )
        }
    }
}
