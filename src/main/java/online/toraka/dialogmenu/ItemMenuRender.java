package online.toraka.dialogmenu;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.util.List;
import java.util.Map;

public record ItemMenuRender(
        List<DialogBody> bodies, List<ActionButton> buttons, Map<String, ItemDisplay> guards) {}
