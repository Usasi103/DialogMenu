package online.toraka.dialogmenu

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogClicksTest {
    @Test
    fun `custom menu click serializes on the minimum supported Adventure API`() {
        val key = Key.key("dialogmenu_settings", "session/action/toggle")
        val component = Component.text("toggle").clickEvent(DialogClicks.custom(key))
        val serializer = GsonComponentSerializer.gson()
        val serialized = serializer.serialize(component)
        assertEquals(ClickEvent.Action.CUSTOM, component.clickEvent()!!.action())
        assertTrue(serialized.contains(key.asString()))
        assertEquals(component, serializer.deserialize(serialized))
    }
}
