package online.toraka.dialogmenu

import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.Locale
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RichMenuTextTest {
    private val translations =
        MenuTranslations(
            "zh_cn",
            mapOf(
                "zh_cn" to
                    mapOf("title" to "菜单", "hello" to "你好 <arg:0>", "nested" to "<l10n:title>"),
                "en_us" to mapOf("title" to "Menu", "hello" to "Hello <arg:0>"),
                "fr" to mapOf("title" to "Menu français"),
            ),
        )

    private fun plain(component: Component) =
        PlainTextComponentSerializer.plainText().serialize(component)

    private fun measure(text: RichMenuText, value: String) =
        text.measure(value, DialogCanvas.LABEL_FONT, 8, false)

    @Test
    fun `local i18n and l10n use independent locales and fallback without CE`() {
        val text = RichMenuText(translations, Locale.US)
        assertEquals("菜单 / Menu", plain(text.component("<i18n:title> / <l10n:title>")))
        assertEquals("Hello Alex", plain(text.component("<l10n:hello:Alex>")))
        assertEquals("Menu", plain(text.component("<i18n:nested>")))
        assertEquals(
            "菜单",
            plain(RichMenuText(translations, Locale.JAPAN).component("<l10n:title>")),
        )
        assertEquals(
            "Menu français",
            plain(RichMenuText(translations, Locale.CANADA_FRENCH).component("<l10n:title>")),
        )
        assertEquals("missing", plain(text.component("<l10n:missing>")))
    }

    @Test
    fun `images are measured and clipped as whole glyphs before canvas layout`() {
        val requests = mutableListOf<MenuImageRequest>()
        val imageFont = Key.key("demo:icons")
        val rich =
            RichMenuText(
                images = {
                    requests += it
                    MenuImage(Component.text("\uE001").font(imageFont), 13)
                }
            )
        val value = measure(rich, "A<image:IA:demo:star>B")
        assertEquals(DialogCanvas.textWidth("AB") + 13, value.width)
        assertEquals("A", plain(value.fit(18).component()))
        assertEquals(listOf("A", "\uE001", "B"), value.wrap(13).map { plain(it.component()) })
        assertEquals("IA", requests.distinct().single().provider)
        val big = rich.measure("<image:CE:demo:star>", TitleFont.font(16), 16, true)
        assertEquals(13, big.width)
        assertEquals("\uE001", plain(big.component()))
        assertEquals(imageFont, big.component().font())
        assertEquals(
            MenuImageRequest("CE", "demo:star", 1, 2),
            RichMenuText.imageRequest(listOf("demo", "star", "1", "2")),
        )
    }

    @Test
    fun `canvas retains exact cursor width and click event after translated image`() {
        val rich =
            RichMenuText(
                translations,
                Locale.US,
                { MenuImage(Component.text("\uE001").font(Key.key("demo:icons")), 13) },
            )
        val canvas = DialogCanvas(rows = 1, richText = rich) { ClickEvent.runCommand("/example") }
        canvas.text(10, 0, "<image:demo:star><l10n:title>", action = "go")
        val root = canvas.build()
        var width = 0f
        var foundImage = false
        fun visit(component: Component, font: Key? = null, click: ClickEvent? = null) {
            val currentFont = component.font() ?: font
            val currentClick = component.clickEvent() ?: click
            if (component is TextComponent)
                component.content().codePoints().forEach { cp ->
                    width +=
                        when (currentFont) {
                            DialogCanvas.FONT -> (cp - 0xEA00).toFloat()
                            Key.key("demo:icons") -> {
                                foundImage = true
                                assertNotNull(currentClick)
                                13f
                            }
                            else ->
                                LabelMetrics.width(
                                    String(Character.toChars(cp)),
                                    DialogCanvas.LABEL_FONT,
                                    false,
                                )
                        }
                }
            component.children().forEach { visit(it, currentFont, currentClick) }
        }
        visit(root)
        assertTrue(foundImage)
        assertEquals(DialogCanvas.LINE_WIDTH.toFloat(), width)
    }

    @Test
    fun `old text stays literal while translation formatting is retained`() {
        val rich =
            RichMenuText(
                MenuTranslations("en_us", mapOf("en_us" to mapOf("color" to "<red>Red</red>")))
            )
        assertEquals("<red>literal</red>", plain(rich.component("<red>literal</red>")))
        val value = measure(rich, "<i18n:color>")
        assertEquals("Red", plain(value.component()))
        assertEquals(NamedTextColor.RED, value.glyphs.first().component.color())
    }

    @Test
    fun `cycles and missing plugins give bounded visible fallback without raw network tags`() {
        val warnings = mutableListOf<String>()
        val rich =
            RichMenuText(
                MenuTranslations("en_us", mapOf("en_us" to mapOf("loop" to "<i18n:loop>"))),
                warning = warnings::add,
            )
        assertFalse(plain(rich.component("<i18n:loop>")).contains("<i18n:"))
        assertEquals("[image:demo:star]", plain(rich.component("<image:IA:demo:star>")))
        assertFalse(warnings.isEmpty())
        assertFalse(plain(rich.component("<image:demo:star:-1>")).contains("<image:"))
        assertThrows(IllegalArgumentException::class.java) {
            RichMenuText.imageRequest(listOf("IA", "demo", "star", "0"))
        }
    }

    @Test
    fun `documented menu parses and renders translated buttons without raw tags`(
        @TempDir directory: Path
    ) {
        MenuTranslations.initialize(directory.toFile())
        val source = Path.of("docs/wiki/examples/text-tags.yml").toFile().readText()
        val catalog =
            MenuCatalogParser.parse(
                "Version: 3\nDefaultMenu: text-tags\n",
                mapOf("text-tags" to source),
            )
        val template = catalog.templates.getValue("text-tags/main")
        val rich = RichMenuText(MenuTranslations.read(directory.toFile()), Locale.US)
        val canvas =
            TemplateRenderer.render(
                template,
                emptyMap(),
                { TemplateRenderer.expand(it, emptyMap(), "Alex", "uuid") },
                rich,
            ) {
                ClickEvent.runCommand("/close")
            }
        val output = plain(canvas.build())
        assertTrue(output.contains("Hello, Alex!"))
        assertTrue(output.contains("Close"))
        assertFalse(output.contains("<l10n:"))
        assertEquals(1, canvas.hits.size)
    }

    @Test
    fun `bitmap metrics trim transparency and select the requested cell`() {
        val png = BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB)
        png.setRGB(7, 4, -1)
        png.setRGB(31, 4, -1)
        assertEquals(9, MenuImages.bitmapAdvance(png, 16, 1, 2, 0, 0))
        assertEquals(17, MenuImages.bitmapAdvance(png, 16, 1, 2, 0, 1))
        assertEquals(5, MenuImages.bitmapAdvance(png, 8, 1, 2, 0, 0))
    }

    @Test
    fun `translation files load nested keys and reject invalid reload input`(
        @TempDir directory: Path
    ) {
        MenuTranslations.initialize(directory.toFile())
        val file = directory.resolve("translations/zh_cn.yml").toFile()
        assertEquals("关闭", MenuTranslations.read(directory.toFile()).get("demo.close", null))
        file.writeText("demo:\n  close: 123\n")
        assertThrows(IllegalArgumentException::class.java) {
            MenuTranslations.read(directory.toFile())
        }
    }
}
