package online.toraka.playersettings

import java.lang.reflect.Proxy
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MenuPreferencesTest {
    private fun container(): PersistentDataContainer {
        val values = mutableMapOf<NamespacedKey, Any>()
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(PersistentDataContainer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "get" -> values[args[0]]
                "set" -> {
                    values[args[0] as NamespacedKey] = args[2]
                    null
                }
                else -> error("Unexpected PDC operation ${method.name}")
            }
        } as PersistentDataContainer
    }

    @Test
    fun `preferences survive reads without sharing state or overwriting unrelated settings`() {
        val first = container()
        val second = container()
        val pickupKey = NamespacedKey("pickupnotifier", "disabled")
        first.set(pickupKey, PersistentDataType.BYTE, 1.toByte())
        assertEquals(MenuPreferences(), MenuPreferences.read(first))
        MenuPreferences(MenuLanguage.ENGLISH, MenuTheme.LIGHT).save(first)
        assertEquals(
            MenuPreferences(MenuLanguage.ENGLISH, MenuTheme.LIGHT),
            MenuPreferences.read(first),
        )
        assertEquals(MenuPreferences(), MenuPreferences.read(second))
        MenuPreferences.read(first).copy(language = MenuLanguage.CHINESE).save(first)
        assertEquals(
            MenuPreferences(MenuLanguage.CHINESE, MenuTheme.LIGHT),
            MenuPreferences.read(first),
        )
        assertEquals(1.toByte(), first.get(pickupKey, PersistentDataType.BYTE))
        first.set(
            NamespacedKey("playersettings", "menu_theme"),
            PersistentDataType.STRING,
            "retired",
        )
        assertEquals(MenuTheme.DARK, MenuPreferences.read(first).theme)
    }

    @Test
    fun `menu translations are complete and selections retain readable labels`() {
        assertEquals(MenuText.keys(MenuLanguage.CHINESE), MenuText.keys(MenuLanguage.ENGLISH))
        for (language in MenuLanguage.entries) {
            assertTrue(DialogCanvas.textWidth(MenuText.get(language, "search.button")) <= 76)
            for (tab in SettingsDialog.Tab.entries) {
                val label = MenuText.get(language, tab.textKey)
                assertTrue(label.isNotBlank())
                assertTrue(DialogCanvas.textWidth(label) <= 76, label)
            }
        }
        assertEquals("On", SettingsDialog.toggleLabel("开", MenuLanguage.ENGLISH))
        assertEquals("Medium", SettingsDialog.densityLabel("中", MenuLanguage.ENGLISH))
        assertEquals(
            "Level: {1}  Ping: 30 ms",
            MenuText.get(MenuLanguage.ENGLISH, "profile.level", "{1}", 30),
        )
        assertEquals(SettingsDialog.Tab.APPEARANCE, SettingsDialog.findTab("切换语言"))
        assertEquals(SettingsDialog.Tab.APPEARANCE, SettingsDialog.findTab("Light theme"))
    }

    @Test
    fun `both themes have identical hit regions line widths and glyph advances`() {
        var expectedHits: List<DialogCanvas.Hit>? = null
        for (theme in MenuTheme.entries) {
            val canvas = DialogCanvas(theme) { ClickEvent.custom(Key.key("test", it)) }
            canvas.sprite(114, 0, DialogCanvas.PANEL_TOP)
            canvas.sprite(114, 10, DialogCanvas.PANEL_BOTTOM)
            SettingsDialog.Tab.entries.forEachIndexed { index, tab ->
                canvas.button(
                    0,
                    3 + index * 2,
                    DialogCanvas.NAV,
                    MenuText.get(MenuLanguage.ENGLISH, tab.textKey),
                    "tab_${tab.name.lowercase()}",
                )
            }
            canvas.button(330, 3, DialogCanvas.CONTROL, "简体中文", "language_zh_cn")
            canvas.button(330, 5, DialogCanvas.SELECTED_CONTROL, "English", "language_en_us")
            canvas.button(330, 13, DialogCanvas.CONTROL, "Dark", "theme_dark")
            canvas.button(330, 15, DialogCanvas.SELECTED_CONTROL, "Light", "theme_light")
            if (expectedHits == null) expectedHits = canvas.hits.toList()
            else assertEquals(expectedHits, canvas.hits)
            var width = 0
            var lines = 1
            for (component in canvas.build().children()) {
                val part = component as TextComponent
                if (part.content() == "\n") {
                    assertEquals(DialogCanvas.LINE_WIDTH, width)
                    width = 0
                    lines++
                } else if (part.font() == DialogCanvas.FONT) {
                    val code = part.content().single().code
                    width +=
                        if (code in 0xE800..0xEC00) code - 0xEA00 else DialogCanvas.glyphWidth(code)
                } else width += DialogCanvas.textWidth(part.content())
                assertTrue(width in 0..DialogCanvas.LINE_WIDTH, "theme=$theme line=$lines x=$width")
            }
            assertEquals(DialogCanvas.LINE_WIDTH, width)
            assertEquals(29, lines)
        }
    }
}
