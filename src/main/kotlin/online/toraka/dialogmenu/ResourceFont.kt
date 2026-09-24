package online.toraka.dialogmenu

import net.kyori.adventure.key.Key

/** Existing custom menus keep working with the renamed resource-pack namespaces. */
internal fun resourceFont(id: String): Key {
    val key = Key.key(id)
    val namespace =
        when (key.namespace()) {
            "toraka_settings" -> "dialogmenu_settings"
            "toraka_dialogue" -> "dialogmenu_dialogue"
            else -> key.namespace()
        }
    return Key.key(namespace, key.value())
}
