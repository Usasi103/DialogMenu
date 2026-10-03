package online.toraka.dialogmenu;

import java.util.Map;
import java.util.Set;

/** A file is a menu; page IDs are local to that menu. */
public record CatalogMenu(
        String id,
        String defaultPage,
        MenuDefinition settings,
        Map<String, DialogTemplate> canvas,
        MenuType menuType) {

    public CatalogMenu(
            String id,
            String defaultPage,
            MenuDefinition settings,
            Map<String, DialogTemplate> canvas) {
        this(id, defaultPage, settings, canvas, MenuType.DIALOG);
    }

    public Set<String> pages() {
        if (menuType == MenuType.FULLSCREEN) return Set.of("main");
        return settings != null ? settings.pages().keySet() : canvas.keySet();
    }
}
