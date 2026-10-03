package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A fully parsed menu snapshot. Preparing it never installs menus, images or resources. */
record MenuCandidate(
        MenuCatalog catalog,
        MenuDefinition definition,
        Map<String, DialogTemplate> templates,
        MenuTranslations translations,
        FullscreenPackSettings fullscreen,
        List<String> warnings) {

    static MenuCandidate parse(MenuFiles files) {
        FullscreenPackSettings fullscreen =
                FullscreenPackSettings.parse(
                        files.has("fullscreen.yml")
                                ? files.text("fullscreen.yml")
                                : MenuRepository.resource("fullscreen.yml"));
        MenuTranslations translations =
                MenuTranslations.read(files.text("text.yml"), files.group("translations"));
        List<String> warnings = new ArrayList<>();
        MenuItemSources itemSources = ItemSources.previewRegistry();
        if (files.has("config.yml")) {
            Object version =
                    MenuConfigParser.yaml(files.text("config.yml"), "config.yml").get("Version");
            if (Integer.valueOf(3).equals(version)) {
                MenuCatalog catalog =
                        MenuCatalogParser.parse(files.text("config.yml"), files.group("menus"));
                for (CatalogMenu menu : catalog.menus().values()) {
                    if (menu.settings() != null) {
                        try {
                            warnings.addAll(ItemSources.validate(menu.settings(), itemSources));
                        } catch (RuntimeException error) {
                            throw new IllegalArgumentException(
                                    "menus/" + menu.id() + ".yml: " + error.getMessage(), error);
                        }
                    }
                }
                return new MenuCandidate(
                        catalog, null, catalog.templates(), translations, fullscreen, warnings);
            }
        }
        Map<String, DialogTemplate> templates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : files.group("templates").entrySet()) {
            try {
                templates.put(
                        entry.getKey(), TemplateParser.parse(entry.getKey(), entry.getValue()));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException(
                        "templates/" + entry.getKey() + ".yml: " + error.getMessage(), error);
            }
        }
        TemplateParser.validateLinks(templates);
        MenuDefinition definition;
        if (files.has("config.yml")) {
            definition =
                    SimpleMenuParser.parse(
                            files.text("config.yml"), id -> files.text("menus/" + id + ".yml"));
        } else {
            Map<MenuLanguage, String> languages = new LinkedHashMap<>();
            for (MenuLanguage language : MenuLanguage.values()) {
                languages.put(language, files.text("languages/" + language.id() + ".yml"));
            }
            definition = MenuConfigParser.parse(files.text("menu.yml"), languages);
        }
        warnings.addAll(ItemSources.validate(definition, itemSources));
        return new MenuCandidate(
                null,
                definition,
                Collections.unmodifiableMap(templates),
                translations,
                fullscreen,
                warnings);
    }

    String count() {
        return catalog != null
                ? catalog.menus().size() + " 个菜单"
                : definition.pages().size() + " 个页面";
    }
}
