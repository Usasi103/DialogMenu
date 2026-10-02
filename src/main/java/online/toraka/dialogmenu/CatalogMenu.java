package online.toraka.dialogmenu;

import java.util.Map;
import java.util.Set;

/** A file is a menu; page IDs are local to that menu. */
public record CatalogMenu(
        String id,
        String defaultPage,
        MenuDefinition settings,
        Map<String, DialogTemplate> canvas) {

    public Set<String> pages() {
        return settings != null ? settings.pages().keySet() : canvas.keySet();
    }
}
