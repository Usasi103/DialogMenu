package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MenuCatalogParser {

    private static final Pattern identifier = Pattern.compile("[a-z][a-z0-9_-]{0,47}");

    private MenuCatalogParser() {}

    public static MenuCatalog parse(String configSource, Map<String, String> sources) {
        YamlConfiguration config = MenuConfigParser.yaml(configSource, "config.yml");
        keys(config, Kt.setOf("Version", "DefaultMenu", "ResourcePack"), "config.yml");
        Kt.require(
                !config.contains("ResourcePack") || config.isConfigurationSection("ResourcePack"),
                () -> "config.yml.ResourcePack: 需要配置段");
        MenuResourcePack resourcePack =
                MenuResourcePack.parse(config.getConfigurationSection("ResourcePack"));
        Kt.require(Objects.equals(config.get("Version"), 3), () -> "config.yml.Version: 必须为 3");
        Kt.require(sources.size() >= 1 && sources.size() <= 64, () -> "menus: 需要 1–64 个菜单文件");
        Map<String, YamlConfiguration> roots = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String id = entry.getKey();
            String source = entry.getValue();
            Kt.require(identifier.matcher(id).matches(), () -> "menus: 无效菜单文件名 " + id);
            String path = "menus/" + id + ".yml";
            YamlConfiguration root = MenuConfigParser.yaml(source, path);
            if (Objects.equals(root.getString("Type"), "quest-demo")) {
                roots.put(id, QuestDemoCompiler.compile(root, path));
            } else {
                roots.put(id, root);
            }
        }
        Map<String, String> defaults = new LinkedHashMap<>();
        for (Map.Entry<String, YamlConfiguration> entry : roots.entrySet()) {
            String id = entry.getKey();
            YamlConfiguration root = entry.getValue();
            ConfigurationSection pages =
                    Kt.requireNotNull(
                            root.getConfigurationSection("Pages"),
                            () -> "menus/" + id + ".yml: 缺少 Pages 配置段");
            Kt.require(
                    pages.getKeys(false).size() >= 1 && pages.getKeys(false).size() <= 64,
                    () -> "menus/" + id + ".yml.Pages: 需要 1–64 页");
            for (String page : pages.getKeys(false)) {
                Kt.require(
                        identifier.matcher(page).matches(),
                        () -> "menus/" + id + ".yml.Pages: 无效页面 ID " + page);
                Kt.require(
                        pages.isConfigurationSection(page),
                        () -> "menus/" + id + ".yml.Pages." + page + ": 需要配置段");
            }
            String configured = root.getString("DefaultPage");
            String defaultPage =
                    configured != null ? configured : pages.getKeys(false).iterator().next();
            Kt.require(
                    pages.getKeys(false).contains(defaultPage),
                    () -> "menus/" + id + ".yml.DefaultPage: 页面不存在 " + defaultPage);
            defaults.put(id, defaultPage);
        }
        Map<String, CatalogMenu> menus = new LinkedHashMap<>();
        Function<String, ReactionParser.Target> open = value -> open(value, roots, defaults);
        for (Map.Entry<String, YamlConfiguration> entry : roots.entrySet()) {
            String id = entry.getKey();
            YamlConfiguration root = entry.getValue();
            String path = "menus/" + id + ".yml";
            Kt.require(Objects.equals(root.get("Version"), 1), () -> path + ".Version: 必须为 1");
            ConfigurationSection pages =
                    Objects.requireNonNull(root.getConfigurationSection("Pages"));
            String type = root.getString("Type");
            CatalogMenu menu;
            if (Objects.equals(type, "settings") || Objects.equals(type, "settings-demo")) {
                keys(
                        root,
                        Kt.setOf(
                                "Version",
                                "Type",
                                "Title",
                                "DefaultPage",
                                "Language",
                                "Theme",
                                "HideFocusOutline",
                                "ShowFooter",
                                "Navigation",
                                "MainMenu",
                                "Pages"),
                        path);
                YamlConfiguration legacy = copy(root);
                legacy.set("Version", 2);
                legacy.set("Type", null);
                legacy.set("Pages", new ArrayList<>(pages.getKeys(false)));
                MenuDefinition definition;
                try {
                    definition =
                            SimpleMenuParser.parse(
                                    legacy.saveToString(),
                                    page ->
                                            copy(Objects.requireNonNull(
                                                            pages.getConfigurationSection(page)))
                                                    .saveToString(),
                                    open);
                } catch (Exception error) {
                    throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
                }
                MenuDefinition settings =
                        definition.withDemo(
                                Objects.equals(root.getString("Type"), "settings-demo"));
                if (settings.demo()) {
                    SettingsDemoSession.validate(settings);
                }
                menu =
                        new CatalogMenu(
                                id, Kt.getValue(defaults, id), settings, Collections.emptyMap());
            } else if (Objects.equals(type, "canvas")) {
                keys(
                        root,
                        Kt.setOf(
                                "Version",
                                "Type",
                                "Title",
                                "DefaultPage",
                                "Skin",
                                "Canvas",
                                "Variables",
                                "Placeholders",
                                "Pages"),
                        path);
                Map<String, DialogTemplate> templates = new LinkedHashMap<>();
                for (String page : pages.getKeys(false)) {
                    ConfigurationSection section =
                            Objects.requireNonNull(pages.getConfigurationSection(page));
                    keys(
                            section,
                            Kt.setOf(
                                    "Title",
                                    "Skin",
                                    "Canvas",
                                    "Variables",
                                    "Placeholders",
                                    "Elements"),
                            path + ".Pages." + page);
                    YamlConfiguration compiled = new YamlConfiguration();
                    compiled.set("Version", 1);
                    for (String key :
                            Kt.listOf("Title", "Skin", "Canvas", "Variables", "Placeholders")) {
                        if (root.contains(key)) {
                            put(compiled, key, root.get(key));
                        }
                    }
                    for (Map.Entry<String, Object> field : section.getValues(false).entrySet()) {
                        put(compiled, field.getKey(), field.getValue());
                    }
                    templates.put(
                            page,
                            TemplateParser.parse(
                                    id + "/" + page,
                                    compiled.saveToString(),
                                    path + ".Pages." + page,
                                    target ->
                                            pages.getKeys(false).contains(target)
                                                    ? new ReactionParser.Target(
                                                            "template", id + "/" + target)
                                                    : null,
                                    open));
                }
                menu = new CatalogMenu(id, Kt.getValue(defaults, id), null, templates);
            } else {
                throw Kt.error(path + ".Type: 使用 settings、settings-demo 或 canvas");
            }
            menus.put(id, menu);
        }
        String configuredMenu = config.getString("DefaultMenu");
        String defaultMenu = configuredMenu != null ? configuredMenu : "settings";
        Kt.require(
                menus.containsKey(defaultMenu),
                () -> "config.yml.DefaultMenu: 菜单不存在 " + defaultMenu);
        MenuCatalog catalog = new MenuCatalog(defaultMenu, menus, resourcePack);
        TemplateParser.validateLinks(catalog.templates());
        return catalog;
    }

    private static void keys(ConfigurationSection section, Set<String> allowed, String path) {
        Kt.require(
                Kt.minus(section.getKeys(false), allowed).isEmpty(),
                () -> path + ": 未知字段 " + Kt.minus(section.getKeys(false), allowed));
    }

    private static YamlConfiguration copy(ConfigurationSection section) {
        YamlConfiguration target = new YamlConfiguration();
        for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            put(target, entry.getKey(), entry.getValue());
        }
        return target;
    }

    /**
     * {@code open: menu} or {@code open: menu:page}. A canvas page is linked as {@code template:},
     * which keeps the current variable values; a settings menu opens through {@link
     * MenuRuntime#open}.
     */
    private static ReactionParser.Target open(
            String value, Map<String, YamlConfiguration> roots, Map<String, String> defaults) {
        String menu = Kt.trim(Kt.substringBefore(value, ':'));
        String page =
                value.indexOf(':') >= 0
                        ? Kt.trim(Kt.substringAfter(value, ':'))
                        : defaults.get(menu);
        YamlConfiguration destination = roots.get(menu);
        if (destination == null || page == null) {
            return null;
        }
        ConfigurationSection pages = destination.getConfigurationSection("Pages");
        if (pages == null || !pages.getKeys(false).contains(page)) {
            return null;
        }
        boolean canvas = Objects.equals(destination.getString("Type"), "canvas");
        return new ReactionParser.Target(canvas ? "template" : "open", menu + "/" + page);
    }

    private static void put(ConfigurationSection target, String key, Object value) {
        if (value instanceof ConfigurationSection section) {
            ConfigurationSection child = target.createSection(key);
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                put(child, entry.getKey(), entry.getValue());
            }
        } else {
            target.set(key, value);
        }
    }
}
