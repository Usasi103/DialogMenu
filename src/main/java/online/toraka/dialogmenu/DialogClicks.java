package online.toraka.dialogmenu;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.event.ClickEvent;

/** The two-argument factory is shared by Paper 1.21.11 and 26.x. */
public final class DialogClicks {

    private DialogClicks() {}

    public static ClickEvent custom(Key key) {
        return ClickEvent.custom(key, BinaryTagHolder.binaryTagHolder("{}"));
    }
}
