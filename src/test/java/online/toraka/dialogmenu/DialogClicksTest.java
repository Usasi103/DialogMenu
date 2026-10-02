package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DialogClicksTest {

    @Test
    @DisplayName("custom menu click serializes on the minimum supported Adventure API")
    void customMenuClickSerializesOnTheMinimumSupportedAdventureApi() {
        Key key = Key.key("dialogmenu_settings", "session/action/toggle");
        Component component = Component.text("toggle").clickEvent(DialogClicks.custom(key));
        GsonComponentSerializer serializer = GsonComponentSerializer.gson();
        String serialized = serializer.serialize(component);
        assertEquals(ClickEvent.Action.CUSTOM, component.clickEvent().action());
        assertTrue(serialized.contains(key.asString()));
        assertEquals(component, serializer.deserialize(serialized));
    }
}
