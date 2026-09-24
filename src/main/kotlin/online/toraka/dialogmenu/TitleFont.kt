package online.toraka.dialogmenu

import java.util.Properties
import kotlin.math.floor
import net.kyori.adventure.key.Key

/** The same bitmap rounding and one-pixel bold advance used by the client. */
object TitleFont {
    private data class Glyph(val inkWidth: Int, val cellHeight: Int, val padded: Boolean)

    private val metrics: Map<Int, Glyph> =
        Properties()
            .apply {
                requireNotNull(
                        TitleFont::class.java.getResourceAsStream("/title-metrics.properties")
                    )
                    .use { load(it) }
            }
            .entries
            .associate { (key, value) ->
                val parts = value.toString().split(',').map(String::toInt)
                key.toString().toInt() to Glyph(parts[0], parts[1], parts[2] == 1)
            }

    fun font(size: Int, raised: Boolean = false): Key =
        Key.key("dialogmenu_dialogue:text_$size" + if (raised && size <= 12) "_button" else "")

    fun lineRows(size: Int): Int = if (size == 8) 1 else ((size * 3 + 1) / 2 + 8) / 9

    private fun advance(character: Char, size: Int): Int {
        if (character == ' ') return (size + 1) / 2
        val glyph = metrics[character.code] ?: metrics.getValue(63)
        val height = if (glyph.padded) (size * 3 + 1) / 2 else size
        return floor(glyph.inkWidth.toDouble() * height / glyph.cellHeight + 0.5).toInt() + 1
    }

    fun normalize(text: String): String =
        text.map { if (metrics.containsKey(it.code)) it else '?' }.joinToString("")

    fun width(text: String, size: Int): Int = text.sumOf { advance(it, size) }
}
