package online.toraka.dialogmenu

import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

data class ItemMenuRender(
    val bodies: List<DialogBody>,
    val buttons: List<ActionButton>,
    val guards: Map<String, ItemDisplay>,
)

/** Item models use Minecraft's item body. They cannot be inserted into the bitmap font canvas. */
object ItemMenuRenderer {
    fun render(
        menu: MenuDefinition,
        page: MenuPage,
        language: MenuLanguage,
        expand: (String) -> String,
        resolve: (ItemDisplay) -> ResolvedMenuItem,
        click: (String) -> ClickEvent,
    ): ItemMenuRender {
        fun text(value: String) = expand(menu.text(language, value))
        val bodies = mutableListOf<DialogBody>()
        val buttons = mutableListOf<ActionButton>()
        val guards = mutableMapOf<String, ItemDisplay>()
        page.itemEntries.forEach { entry ->
            val resolved = entry.display?.let(resolve)
            val available = resolved == null || resolved.available
            val label = text(entry.name)
            var caption = Component.text(label).decorate(TextDecoration.BOLD)
            entry.description.forEach {
                caption =
                    caption
                        .appendNewline()
                        .append(Component.text(text(it)).decoration(TextDecoration.BOLD, false))
            }
            if (!available) {
                caption =
                    caption
                        .appendNewline()
                        .append(
                            Component.text(
                                MenuText.get(language, "unavailable"),
                                NamedTextColor.RED,
                            )
                        )
            }
            if (entry.kind == "button") {
                val route = "action/${entry.action}"
                val event = if (available) click(route) else null
                if (available && entry.display != null) guards[route] = entry.display
                if (event != null) caption = caption.clickEvent(event)
                buttons +=
                    ActionButton.create(
                        Component.text(
                            label,
                            if (available) NamedTextColor.WHITE else NamedTextColor.GRAY,
                        ),
                        null,
                        200,
                        event?.let(DialogAction::staticAction),
                    )
            }
            val description = DialogBody.plainMessage(caption, 360)
            bodies +=
                resolved?.item?.let { DialogBody.item(it, description, true, true, 24, 24) }
                    ?: description
        }
        menu.pages.values
            .filter { it.id != page.id }
            .forEach { other ->
                buttons +=
                    ActionButton.create(
                        Component.text(text(other.label)),
                        null,
                        200,
                        DialogAction.staticAction(click("page/${other.id}")),
                    )
            }
        return ItemMenuRender(bodies, buttons, guards)
    }
}
