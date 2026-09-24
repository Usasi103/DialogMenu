package online.toraka.dialogmenu

import java.util.TreeMap
import kotlin.math.ceil
import kotlin.math.floor
import net.kyori.adventure.key.Key

/** A player's canvas scale. Minecraft's global GUI scale remains client-owned. */
enum class MenuScale(val id: String, val percent: Int) {
    SMALL("50", 50),
    MEDIUM("75", 75),
    NORMAL("100", 100);

    val factor: Float
        get() = percent / 100f

    fun pixel(value: Float): Float = floor(value * factor * 2 + 0.5f) / 2

    fun y(row: Int): Int = floor(row * 9 * factor + 0.5f).toInt() + if (this == NORMAL) 0 else 3

    fun rows(sourceRows: Int): Int = ceil((y(sourceRows)) / 9.0).toInt()

    fun bodyWidth(width: Int, hideFocus: Boolean): Int =
        ceil(width * factor).toInt() +
            DialogCanvas.WRAP_SLACK +
            DialogCanvas.BODY_PADDING * 2 +
            if (hideFocus) 14 else 0

    companion object {
        fun parse(value: String?): MenuScale = entries.firstOrNull { it.id == value } ?: NORMAL
    }
}

/** Advances measured from the same scaled bitmap definitions sent to the client. */
object ScaledFonts {
    private data class Range(val end: Int, val advance: Float)

    private val metrics =
        mutableMapOf<String, TreeMap<Int, Range>>().apply {
            requireNotNull(ScaledFonts::class.java.getResourceAsStream("/scaled-font-metrics.txt"))
                .bufferedReader()
                .useLines { lines ->
                    lines
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .forEach { line ->
                            val parts = line.split(' ')
                            if (parts[0] == "@") put(parts[1], TreeMap())
                            else
                                getValue(parts[0])[parts[1].toInt()] =
                                    Range(parts[2].toInt(), parts[3].toFloat())
                        }
                }
        }

    fun font(original: Key, scale: MenuScale, offset: Int): Key {
        require(metrics.containsKey("$original@${scale.percent}")) {
            "No menu-scale font for $original"
        }
        return Key.key(original.namespace(), "scaled/${scale.percent}/$offset/${original.value()}")
    }

    fun supports(original: Key, scale: MenuScale): Boolean =
        scale == MenuScale.NORMAL || metrics.containsKey("$original@${scale.percent}")

    fun advance(original: Key, scale: MenuScale, codepoint: Int): Float {
        val ranges = metrics.getValue("$original@${scale.percent}")
        return ranges.floorEntry(codepoint)?.value?.takeIf { codepoint <= it.end }?.advance ?: 6f
    }
}
