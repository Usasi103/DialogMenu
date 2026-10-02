package online.toraka.dialogmenu;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Item models use Minecraft's item body. They cannot be inserted into the bitmap font canvas. */
public final class ItemMenuRenderer {

    private ItemMenuRenderer() {}

    public static ItemMenuRender render(
            MenuDefinition menu,
            MenuPage page,
            MenuLanguage language,
            Function<String, String> expand,
            Function<ItemDisplay, ResolvedMenuItem> resolve,
            Function<String, ClickEvent> click) {
        return render(menu, page, language, expand, resolve, click, new RichMenuText());
    }

    public static ItemMenuRender render(
            MenuDefinition menu,
            MenuPage page,
            MenuLanguage language,
            Function<String, String> expand,
            Function<ItemDisplay, ResolvedMenuItem> resolve,
            Function<String, ClickEvent> click,
            RichMenuText richText) {
        Function<String, String> text = value -> expand.apply(menu.text(language, value));
        List<DialogBody> bodies = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        Map<String, ItemDisplay> guards = new LinkedHashMap<>();
        for (ItemMenuEntry entry : page.itemEntries()) {
            ResolvedMenuItem resolved =
                    entry.display() != null ? resolve.apply(entry.display()) : null;
            boolean available = resolved == null || resolved.available();
            String label = text.apply(entry.name());
            Component caption = richText.component(label).decorate(TextDecoration.BOLD);
            for (String line : entry.description()) {
                caption =
                        caption.appendNewline()
                                .append(
                                        richText.component(text.apply(line))
                                                .decoration(TextDecoration.BOLD, false));
            }
            if (!available) {
                caption =
                        caption.appendNewline()
                                .append(
                                        Component.text(
                                                MenuText.get(language, "unavailable"),
                                                NamedTextColor.RED));
            }
            if (entry.kind().equals("button")) {
                String route = "action/" + entry.action();
                ClickEvent event = available ? click.apply(route) : null;
                if (available && entry.display() != null) {
                    guards.put(route, entry.display());
                }
                if (event != null) {
                    caption = caption.clickEvent(event);
                }
                buttons.add(
                        ActionButton.create(
                                richText.component(label)
                                        .color(
                                                available
                                                        ? NamedTextColor.WHITE
                                                        : NamedTextColor.GRAY),
                                null,
                                200,
                                event != null ? DialogAction.staticAction(event) : null));
            }
            PlainMessageDialogBody description = DialogBody.plainMessage(caption, 360);
            if (resolved != null && resolved.item() != null) {
                bodies.add(DialogBody.item(resolved.item(), description, true, true, 24, 24));
            } else {
                bodies.add(description);
            }
        }
        for (MenuPage other : menu.pages().values()) {
            if (other.id().equals(page.id())) {
                continue;
            }
            buttons.add(
                    ActionButton.create(
                            richText.component(text.apply(other.label())),
                            null,
                            200,
                            DialogAction.staticAction(click.apply("page/" + other.id()))));
        }
        return new ItemMenuRender(bodies, buttons, guards);
    }
}
