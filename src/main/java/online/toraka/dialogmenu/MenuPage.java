package online.toraka.dialogmenu;

import java.util.Collections;
import java.util.List;

public record MenuPage(
        String id,
        String label,
        String icon,
        List<String> keywords,
        List<MenuWidget> widgets,
        boolean itemLayout,
        List<ItemMenuEntry> itemEntries) {

    /** Kotlin defaults: a font-canvas page without item entries. */
    public MenuPage(
            String id, String label, String icon, List<String> keywords, List<MenuWidget> widgets) {
        this(id, label, icon, keywords, widgets, false, Collections.emptyList());
    }

    /** {@code copy(itemLayout = true, itemEntries = entries)}. */
    public MenuPage withItems(List<ItemMenuEntry> entries) {
        return new MenuPage(id, label, icon, keywords, widgets, true, entries);
    }

    /** {@code copy(widgets = widgets)}. */
    public MenuPage withWidgets(List<MenuWidget> replacement) {
        return new MenuPage(id, label, icon, keywords, replacement, itemLayout, itemEntries);
    }
}
