package online.toraka.dialogmenu

import java.util.Locale
import kotlin.math.ceil
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags

data class MenuImageRequest(
    val provider: String,
    val id: String,
    val row: Int = 0,
    val column: Int = 0,
)

data class MenuImage(val component: Component, val advance: Int)

data class MeasuredGlyph(val component: Component, val advance: Float)

/** Immutable measured glyphs keep images intact during wrapping, clipping and popup masking. */
data class MeasuredText(val glyphs: List<MeasuredGlyph>) {
    val advance: Float
        get() = glyphs.sumOf { it.advance.toDouble() }.toFloat()

    val width: Int
        get() = ceil(advance).toInt()

    fun component(): Component {
        val first = glyphs.firstOrNull()?.component ?: return Component.empty()
        if (
            glyphs.all {
                it.component is TextComponent &&
                    it.component.children().isEmpty() &&
                    it.component.style() == first.style()
            }
        ) {
            return Component.text(
                    glyphs.joinToString("") { (it.component as TextComponent).content() }
                )
                .style(first.style())
        }
        return Component.empty().children(glyphs.map { it.component }).compact()
    }

    fun fit(pixels: Int): MeasuredText {
        var width = 0f
        return MeasuredText(
            glyphs.takeWhile {
                width += it.advance
                width <= pixels
            }
        )
    }

    operator fun plus(other: MeasuredText) = MeasuredText(glyphs + other.glyphs)

    fun wrap(pixels: Int): List<MeasuredText> {
        val lines = mutableListOf<MeasuredText>()
        var line = mutableListOf<MeasuredGlyph>()
        var width = 0f
        for (glyph in glyphs) {
            if (width + glyph.advance > pixels && line.isNotEmpty()) {
                lines += MeasuredText(line)
                line = mutableListOf()
                width = 0f
            }
            // A glyph wider than the entire region cannot be split or painted outside the canvas.
            if (glyph.advance <= pixels) {
                line += glyph
                width += glyph.advance
            }
        }
        lines += MeasuredText(line)
        return lines
    }
}

/** Only the three explicit tags opt text into MiniMessage; existing plain text stays literal. */
class RichMenuText(
    private val translations: MenuTranslations = MenuTranslations(),
    private val locale: Locale = Locale.ENGLISH,
    private val images: (MenuImageRequest) -> MenuImage? = { null },
    private val warning: (String) -> Unit = {},
) {
    private data class Parsed(val component: Component, val images: Map<Key, MenuImage>)

    private val cache = mutableMapOf<String, Parsed>()
    private val mini =
        MiniMessage.builder()
            .tags(
                TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.reset(),
                )
            )
            .build()

    private fun parse(source: String): Parsed =
        cache.getOrPut(source) {
            val found = linkedMapOf<Key, MenuImage>()
            var count = 0
            fun deserialize(
                text: String,
                stack: Set<String>,
                arguments: List<String> = emptyList(),
            ): Component {
                require(stack.size <= 16 && ++count <= 256) { "翻译嵌套过深或标签过多" }
                val imageTag =
                    TagResolver.resolver("image") { args, _ ->
                        val parts = mutableListOf<String>()
                        while (args.hasNext()) parts += args.pop().value()
                        val request = imageRequest(parts)
                        val image = images(request)
                        if (image == null) {
                            warning("图片不可用 ${request.provider}:${request.id}")
                            Tag.selfClosingInserting(Component.text("[image:${request.id}]"))
                        } else {
                            val marker = Key.key("dialogmenu_internal", "image_${found.size}")
                            found[marker] = image
                            Tag.selfClosingInserting(Component.text("\uFFFC").font(marker))
                        }
                    }
                fun translationTag(name: String) =
                    TagResolver.resolver(name) { args, _ ->
                        val key = args.popOr("缺少翻译键").value()
                        val identity = "$name:$key"
                        if (identity in stack) {
                            warning("翻译循环引用 $key")
                            return@resolver Tag.selfClosingInserting(Component.text("[$key]"))
                        }
                        val values = mutableListOf<String>()
                        while (args.hasNext()) values += args.pop().value()
                        val value = translations.get(key, if (name == "l10n") locale else null)
                        if (value == null) {
                            warning("缺少翻译 $key")
                            Tag.selfClosingInserting(Component.text(key))
                        } else
                            Tag.selfClosingInserting(deserialize(value, stack + identity, values))
                    }
                val argumentTag =
                    TagResolver.resolver("arg") { args, _ ->
                        val index = args.popOr("缺少参数序号").value().toInt()
                        val value = arguments.getOrNull(index) ?: error("缺少翻译参数 $index")
                        Tag.selfClosingInserting(deserialize(value, stack))
                    }
                return mini.deserialize(
                    text,
                    imageTag,
                    translationTag("i18n"),
                    translationTag("l10n"),
                    argumentTag,
                )
            }
            val component =
                if (
                    !Regex("<(?:image|i18n|l10n):", RegexOption.IGNORE_CASE).containsMatchIn(source)
                ) {
                    Component.text(source)
                } else
                    try {
                        deserialize(source, emptySet())
                    } catch (error: Exception) {
                        warning("文本解析失败：${error.message}")
                        // Do not leak raw tags to CE's later packet replacement after measuring
                        // this fallback.
                        Component.text(source.replace('<', '‹').replace('>', '›'))
                    }
            fun sanitize(value: Component): Component {
                var next = value
                if (
                    value is TextComponent &&
                        Regex("<(?:image|i18n|l10n|arg):", RegexOption.IGNORE_CASE)
                            .containsMatchIn(value.content())
                ) {
                    warning("未能解析文本标签：${value.content()}")
                    next = value.content(value.content().replace('<', '‹').replace('>', '›'))
                }
                return next.children(next.children().map(::sanitize))
            }
            Parsed(sanitize(component), found)
        }

    fun component(source: String): Component {
        val parsed = parse(source)
        fun restore(component: Component): Component {
            parsed.images[component.font()]?.let {
                return it.component
            }
            return component.children(component.children().map(::restore))
        }
        return restore(parsed.component)
    }

    fun measure(source: String, font: Key, size: Int, bold: Boolean): MeasuredText {
        val parsed = parse(source)
        val glyphs = mutableListOf<MeasuredGlyph>()
        fun visit(component: Component, inherited: Style) {
            val style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            val image = parsed.images[component.font()]
            if (image != null) {
                glyphs +=
                    MeasuredGlyph(
                        image.component
                            .colorIfAbsent(NamedTextColor.WHITE)
                            .decoration(TextDecoration.BOLD, false)
                            .decoration(TextDecoration.ITALIC, false),
                        image.advance.toFloat(),
                    )
                return
            }
            if (component is TextComponent)
                component.content().codePoints().forEach { codepoint ->
                    val raw = String(Character.toChars(codepoint))
                    val value =
                        if (Character.isISOControl(codepoint)) " "
                        else if (size == 8) raw else TitleFont.normalize(raw)
                    val weight = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE
                    val advance =
                        if (size == 8) LabelMetrics.width(value, font, weight)
                        else
                            (TitleFont.width(value, size) + if (weight) value.length else 0)
                                .toFloat()
                    glyphs += MeasuredGlyph(Component.text(value).style(style), advance)
                }
            component.children().forEach { visit(it, style) }
        }
        visit(
            parsed.component,
            Style.style().font(font).decoration(TextDecoration.BOLD, bold).build(),
        )
        return MeasuredText(glyphs)
    }

    companion object {
        fun imageRequest(arguments: List<String>): MenuImageRequest {
            require(arguments.isNotEmpty()) { "缺少图片 ID" }
            val explicit =
                arguments.size >= 3 &&
                    arguments[0].lowercase(Locale.ROOT) in
                        setOf("ce", "craftengine", "ia", "itemsadder")
            val provider =
                if (explicit && arguments[0].lowercase(Locale.ROOT) in setOf("ia", "itemsadder"))
                    "IA"
                else "CE"
            val parts = if (explicit) arguments.drop(1) else arguments
            require(parts.size in 2..4) { "图片格式：<image:[CE/IA:]namespace:id[:row:column]>" }
            val id = "${parts[0]}:${parts[1]}"
            Key.key(id)
            val row = parts.getOrNull(2)?.toInt() ?: 0
            val column = parts.getOrNull(3)?.toInt() ?: 0
            require(row >= 0 && column >= 0) { "图片行列不可为负数" }
            require(provider != "IA" || parts.size == 2) { "IA 图片不接受行列参数" }
            return MenuImageRequest(provider, id, row, column)
        }
    }
}
