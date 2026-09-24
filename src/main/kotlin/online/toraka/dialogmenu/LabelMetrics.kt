package online.toraka.dialogmenu

import java.util.TreeMap
import net.kyori.adventure.key.Key

/** Advances compiled from the same bitmap and Unihex providers shipped in the pack. */
object LabelMetrics {
    private data class Range(val end: Int, val advance: Float, val boldOffset: Float)

    private data class Font(val parent: String?, val ranges: TreeMap<Int, Range> = TreeMap())

    private val fonts =
        mutableMapOf<String, Font>().apply {
            requireNotNull(LabelMetrics::class.java.getResourceAsStream("/label-metrics.txt"))
                .bufferedReader()
                .useLines { lines ->
                    lines
                        .filter { !it.startsWith("#") && it.isNotBlank() }
                        .forEach { line ->
                            val parts = line.split(' ')
                            if (parts[0] == "@") {
                                put(parts[1], Font(parts[2].takeUnless { it == "-" }))
                            } else {
                                getValue(parts[0]).ranges[parts[1].toInt()] =
                                    Range(parts[2].toInt(), parts[3].toFloat(), parts[4].toFloat())
                            }
                        }
                }
        }

    private fun glyph(font: String, codepoint: Int): Range? {
        val data = fonts.getValue(font)
        return data.ranges.floorEntry(codepoint)?.value?.takeIf { codepoint <= it.end }
            ?: data.parent?.let { glyph(it, codepoint) }
    }

    fun width(text: String, font: Key, bold: Boolean): Float {
        var width = 0f
        text.codePoints().forEach { codepoint ->
            val glyph = glyph(font.asString(), codepoint)
            width += (glyph?.advance ?: 6f) + if (bold) glyph?.boldOffset ?: 1f else 0f
        }
        return width
    }
}
