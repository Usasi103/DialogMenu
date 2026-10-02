package online.toraka.dialogmenu;

import net.kyori.adventure.key.Key;

/** Existing custom menus keep working with the renamed resource-pack namespaces. */
final class ResourceFont {

    private ResourceFont() {}

    static Key resourceFont(String id) {
        Key key = Key.key(id);
        String namespace =
                switch (key.namespace()) {
                    case "toraka_settings" -> "dialogmenu_settings";
                    case "toraka_dialogue" -> "dialogmenu_dialogue";
                    default -> key.namespace();
                };
        return Key.key(namespace, key.value());
    }
}
