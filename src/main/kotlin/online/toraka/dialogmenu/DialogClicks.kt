package online.toraka.dialogmenu

import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.api.BinaryTagHolder
import net.kyori.adventure.text.event.ClickEvent

/** The two-argument factory is shared by Paper 1.21.11 and 26.x. */
object DialogClicks {
    fun custom(key: Key): ClickEvent = ClickEvent.custom(key, BinaryTagHolder.binaryTagHolder("{}"))
}
