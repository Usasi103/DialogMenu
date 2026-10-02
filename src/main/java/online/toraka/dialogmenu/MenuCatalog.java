package online.toraka.dialogmenu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** All menus of the one-file-per-menu format; canvas pages are addressed as {@code menu/page}. */
public final class MenuCatalog {

    private final String defaultMenu;
    private final Map<String, CatalogMenu> menus;
    private final MenuResourcePack resourcePack;
    private final Map<String, DialogTemplate> templates;

    public MenuCatalog(String defaultMenu, Map<String, CatalogMenu> menus) {
        this(defaultMenu, menus, MenuResourcePack.legacy());
    }

    public MenuCatalog(
            String defaultMenu, Map<String, CatalogMenu> menus, MenuResourcePack resourcePack) {
        this.defaultMenu = defaultMenu;
        this.menus = menus;
        this.resourcePack = resourcePack;
        Map<String, DialogTemplate> all = new LinkedHashMap<>();
        for (CatalogMenu menu : menus.values()) {
            for (DialogTemplate template : menu.canvas().values()) {
                all.put(template.id(), template);
            }
        }
        this.templates = Collections.unmodifiableMap(all);
    }

    public String defaultMenu() {
        return defaultMenu;
    }

    public Map<String, CatalogMenu> menus() {
        return menus;
    }

    public MenuResourcePack resourcePack() {
        return resourcePack;
    }

    public Map<String, DialogTemplate> templates() {
        return templates;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MenuCatalog that
                && defaultMenu.equals(that.defaultMenu)
                && menus.equals(that.menus)
                && resourcePack.equals(that.resourcePack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultMenu, menus, resourcePack);
    }

    @Override
    public String toString() {
        return "MenuCatalog(defaultMenu="
                + defaultMenu
                + ", menus="
                + menus
                + ", resourcePack="
                + resourcePack
                + ")";
    }
}
