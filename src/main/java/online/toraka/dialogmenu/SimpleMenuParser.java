package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Compile the operator-facing format into the same validated canvas and action model as v1. */
public final class SimpleMenuParser {

    private static final List<String> defaultPages =
            Kt.listOf("profile", "sound", "particles", "notices", "loot", "appearance", "help");

    private record Binding(String state, String action, Map<String, String> choices) {
        /** Kotlin default: a toggle binding without choices. */
        Binding(String state, String action) {
            this(state, action, Collections.emptyMap());
        }
    }

    /** A {@code [X 像素, Y 行号]} position from the simple format. */
    private record Position(int x, int row) {}

    private static final Map<String, Binding> bindings = createBindings();

    private static final Pattern PAGE_ID = Pattern.compile("[a-z][a-z0-9_-]{0,47}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]*}");
    private static final Pattern STATE_VARIABLE = Pattern.compile("%[a-zA-Z0-9_:.\\-]+%");

    private SimpleMenuParser() {}

    private static Map<String, Binding> createBindings() {
        Map<String, String> density = new LinkedHashMap<>();
        for (String level : Kt.listOf("off", "low", "medium", "high")) {
            density.put(level, "density_" + level);
        }
        return Kt.mapOf(
                "language",
                new Binding(
                        "language",
                        "",
                        Kt.mapOf("zh_cn", "language_zh_cn", "en_us", "language_en_us")),
                "theme",
                new Binding("theme", "", Kt.mapOf("dark", "theme_dark", "light", "theme_light")),
                "particle-density",
                new Binding("density", "", density),
                "particles",
                new Binding("particles", "particles"),
                "sounds",
                new Binding("sounds", "sounds"),
                "leaves",
                new Binding("leaves", "leaves"),
                "firefly",
                new Binding("firefly", "firefly"),
                "biome",
                new Binding("biome", "biome"),
                "pickup",
                new Binding("pickup", "pickup"),
                "loot-beams",
                new Binding("beams", "beam"),
                "loot-sounds",
                new Binding("beam-sounds", "beam_sounds"));
    }

    public static List<String> defaultPages() {
        return defaultPages;
    }

    public static MenuDefinition parse(String source, Function<String, String> readPage) {
        return parse(source, readPage, null);
    }

    /**
     * {@code open} resolves {@code open:} targets to other menus of the catalog (see {@link
     * ReactionParser.Scope}); without a catalog it is null and {@code open:} is rejected.
     */
    public static MenuDefinition parse(
            String source,
            Function<String, String> readPage,
            Function<String, ReactionParser.Target> open) {
        YamlConfiguration config = MenuConfigParser.yaml(source, "config.yml");
        keys(
                config,
                Kt.setOf(
                        "Version",
                        "Title",
                        "DefaultPage",
                        "Language",
                        "Theme",
                        "HideFocusOutline",
                        "ShowFooter",
                        "Navigation",
                        "Pages",
                        "MainMenu"),
                "config.yml");
        Kt.require(
                config.isInt("Version") && config.getInt("Version") == 2,
                () -> "config.yml.Version: 必须为 2");
        List<String> ids = strings(config.get("Pages"), "config.yml.Pages");
        Kt.require(
                !ids.isEmpty() && ids.size() <= 8 && new LinkedHashSet<>(ids).size() == ids.size(),
                () -> "config.yml.Pages: 需要 1–8 个不同的页面 ID");
        for (String it : ids) {
            Kt.require(PAGE_ID.matcher(it).matches(), () -> "config.yml.Pages: 无效页面 ID " + it);
        }
        YamlConfiguration compiled =
                MenuConfigParser.yaml(MenuRepository.resource("menu.yml"), "bundled/menu.yml");
        Map<MenuLanguage, YamlConfiguration> languages = new LinkedHashMap<>();
        for (MenuLanguage language : MenuLanguage.values()) {
            languages.put(
                    language,
                    MenuConfigParser.yaml(
                            MenuRepository.resource("languages/" + language.id() + ".yml"),
                            "bundled/" + language.id()));
        }
        Context context = new Context(ids, compiled, languages, open);
        if (config.contains("Title")) {
            compiled.set("title", context.label(config.get("Title"), "config.yml.Title"));
        }
        String defaultPage = optionalString(config, "DefaultPage", "config.yml");
        compiled.set("default-page", defaultPage.isEmpty() ? Kt.first(ids) : defaultPage);
        String language = optionalString(config, "Language", "config.yml");
        compiled.set("defaults.language", language.isEmpty() ? "zh_cn" : language);
        String theme = optionalString(config, "Theme", "config.yml");
        compiled.set("defaults.theme", theme.isEmpty() ? "dark" : theme);
        Kt.require(
                ids.contains(compiled.getString("default-page")),
                () -> "config.yml.DefaultPage: 必须为 Pages 中的页面");
        boolean knownLanguage = false;
        for (MenuLanguage entry : MenuLanguage.values()) {
            if (entry.id().equals(compiled.getString("defaults.language"))) {
                knownLanguage = true;
                break;
            }
        }
        Kt.require(knownLanguage, () -> "config.yml.Language: 使用 zh_cn / en_us");
        boolean knownTheme = false;
        for (MenuTheme entry : MenuTheme.values()) {
            if (entry.id().equals(compiled.getString("defaults.theme"))) {
                knownTheme = true;
                break;
            }
        }
        Kt.require(knownTheme, () -> "config.yml.Theme: 使用 dark / light");
        if (config.contains("HideFocusOutline")) {
            Kt.require(
                    config.isBoolean("HideFocusOutline"),
                    () -> "config.yml.HideFocusOutline: 必须为 true / false");
            compiled.set("hide-focus-outline", config.getBoolean("HideFocusOutline"));
        }
        if (config.contains("ShowFooter")) {
            Kt.require(
                    config.isBoolean("ShowFooter"),
                    () -> "config.yml.ShowFooter: 必须为 true / false");
            compiled.set("footer.enabled", config.getBoolean("ShowFooter"));
        }
        if (config.contains("MainMenu")) {
            String action = context.actions(config.get("MainMenu"), "config.yml.MainMenu");
            List<Map<String, Object>> common = new ArrayList<>();
            for (Map<?, ?> value : compiled.getMapList("common")) {
                Map<String, Object> entry = new LinkedHashMap<>();
                for (Map.Entry<?, ?> it : value.entrySet()) {
                    entry.put(String.valueOf(it.getKey()), Kt.requireNotNull(it.getValue()));
                }
                common.add(entry);
            }
            Map<String, Object> main = null;
            for (Map<String, Object> it : common) {
                if ("main".equals(it.get("action"))) {
                    main = it;
                    break;
                }
            }
            if (main == null) {
                throw new NoSuchElementException(
                        "Collection contains no element matching the predicate.");
            }
            main.put("action", action);
            compiled.set("common", common);
        }
        if (config.contains("Navigation")) {
            ConfigurationSection navigation = section(config, "Navigation", "config.yml");
            keys(navigation, Kt.setOf("Position", "Step", "FontSize", "Bold"), "Navigation");
            Position position = position(navigation, "Position", "Navigation");
            if (position != null) {
                compiled.set("navigation.x", position.x());
                compiled.set("navigation.row", position.row());
            }
            Map<String, String> renamed =
                    Kt.mapOf("Step", "step", "FontSize", "font-size", "Bold", "bold");
            for (Map.Entry<String, String> entry : renamed.entrySet()) {
                String from = entry.getKey();
                String to = entry.getValue();
                if (navigation.contains(from)) {
                    compiled.set("navigation." + to, navigation.get(from));
                }
            }
        }
        compiled.set("pages", null);
        for (String id : ids) {
            String file = "menus/" + id + ".yml";
            YamlConfiguration page = MenuConfigParser.yaml(readPage.apply(id), file);
            try {
                context.page(id, file, page);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(file + ": " + error.getMessage(), error);
            } catch (IllegalStateException error) {
                throw new IllegalArgumentException(file + ": " + error.getMessage(), error);
            }
        }
        Map<MenuLanguage, String> sources = new LinkedHashMap<>();
        for (Map.Entry<MenuLanguage, YamlConfiguration> entry : languages.entrySet()) {
            sources.put(entry.getKey(), entry.getValue().saveToString());
        }
        MenuDefinition parsed = MenuConfigParser.parse(compiled.saveToString(), sources);
        Map<String, MenuPage> pages = new LinkedHashMap<>();
        for (Map.Entry<String, MenuPage> entry : parsed.pages().entrySet()) {
            List<ItemMenuEntry> items = context.itemPages.get(entry.getKey());
            pages.put(
                    entry.getKey(),
                    items != null ? entry.getValue().withItems(items) : entry.getValue());
        }
        Map<String, MenuAction> actions = new LinkedHashMap<>();
        for (Map.Entry<String, MenuAction> entry : parsed.actions().entrySet()) {
            MenuReaction reaction = context.reactions.get(entry.getKey());
            actions.put(
                    entry.getKey(),
                    reaction != null ? entry.getValue().withReaction(reaction) : entry.getValue());
        }
        return parsed.withPagesAndActions(pages, actions);
    }

    /**
     * The state that the Kotlin parse() locals shared with its local functions while one
     * config.yml compiles: generated IDs, compiled YAML, reactions and native item pages.
     */
    private static final class Context {
        private final List<String> ids;
        private final YamlConfiguration compiled;
        private final Map<MenuLanguage, YamlConfiguration> languages;
        private final Function<String, ReactionParser.Target> open;
        private final Map<String, MenuReaction> reactions = new LinkedHashMap<>();
        private final Map<String, List<ItemMenuEntry>> itemPages = new LinkedHashMap<>();
        private int serial = 0;

        // Layout state of the canvas page being compiled; lowerPanel() updates it.
        private String file;
        private List<Map<String, Object>> widgets;
        private int row;
        private boolean lower;

        Context(
                List<String> ids,
                YamlConfiguration compiled,
                Map<MenuLanguage, YamlConfiguration> languages,
                Function<String, ReactionParser.Target> open) {
            this.ids = ids;
            this.compiled = compiled;
            this.languages = languages;
            this.open = open;
        }

        String next() {
            return "simple-" + serial++;
        }

        String label(Object raw, String path) {
            if (raw instanceof String text) {
                Kt.require(Kt.isNotBlank(text) && Kt.noControl(text), () -> path + ": 需要非空单行文字");
                // A leading dollar is literal in the simple format, just like any other text.
                if (!text.startsWith("$")) {
                    return text;
                }
            }
            Map<?, ?> values;
            if (raw instanceof String literal) {
                Map<String, Object> same = new LinkedHashMap<>();
                for (MenuLanguage language : MenuLanguage.values()) {
                    same.put(language.id(), literal);
                }
                values = same;
            } else if (raw instanceof ConfigurationSection section) {
                values = section.getValues(false);
            } else if (raw instanceof Map<?, ?> map) {
                values = map;
            } else {
                throw Kt.error(path + ": 填写文字，或 {zh_cn: 中文, en_us: English}");
            }
            Kt.require(
                    values.keySet().equals(Kt.setOf("zh_cn", "en_us")),
                    () -> path + ": 双语文字需要 zh_cn 和 en_us");
            String key = next();
            for (MenuLanguage language : MenuLanguage.values()) {
                Object value = values.get(language.id());
                Kt.require(
                        value instanceof String text && Kt.isNotBlank(text) && Kt.noControl(text),
                        () -> path + "." + language.id() + ": 需要非空单行文字");
                Kt.getValue(languages, language).set(key, value);
            }
            return "$" + key;
        }

        String register(Map<String, Object> definition) {
            String id = next();
            compiled.set("actions." + id, definition);
            return id;
        }

        String actions(Object raw, String path) {
            return actions(raw, path, "", "");
        }

        /**
         * An Actions value in TrMenu's reaction format. The compiled action is a placeholder that
         * carries the permission and plugin checks; the reaction is attached after parsing.
         */
        String actions(Object raw, String path, String permission, String plugin) {
            MenuReaction reaction =
                    ReactionParser.parse(
                            raw,
                            path,
                            new ReactionParser.Scope(
                                    false,
                                    null,
                                    Kt.setOf("player", "uuid"),
                                    Collections.emptyMap(),
                                    target ->
                                            ids.contains(target)
                                                    ? new ReactionParser.Target("page", target)
                                                    : null,
                                    open));
            String id =
                    register(
                            Kt.mapOf(
                                    "type",
                                    "builtin",
                                    "value",
                                    "refresh",
                                    "permission",
                                    permission,
                                    "requires-plugin",
                                    plugin));
            reactions.put(id, reaction);
            return id;
        }

        String protectedAction(String id, String permission, String plugin) {
            Map<String, Object> definition =
                    new LinkedHashMap<>(
                            Objects.requireNonNull(
                                            compiled.getConfigurationSection("actions." + id))
                                    .getValues(false));
            if (!permission.isEmpty()) {
                definition.put("permission", permission);
            }
            if (!plugin.isEmpty()) {
                Object original = definition.get("requires-plugin");
                Kt.require(
                        original == null || original.equals(plugin),
                        () -> "Bind 的插件依赖不能替换为 " + plugin);
                definition.put("requires-plugin", plugin);
            }
            return register(definition);
        }

        /** The body of the Kotlin page loop; errors are prefixed with the file by the caller. */
        void page(String id, String file, YamlConfiguration page) {
            this.file = file;
            keys(
                    page,
                    Kt.setOf(
                            "Title",
                            "TitleStyle",
                            "Icon",
                            "Keywords",
                            "Layout",
                            "Icons",
                            "Renderer"),
                    file);
            String title = label(page.get("Title"), file + ".Title");
            List<String> layout = strings(page.get("Layout"), file + ".Layout");
            Kt.require(
                    layout.size() >= 1
                            && layout.size() <= 30
                            && new LinkedHashSet<>(layout).size() == layout.size(),
                    () -> file + ".Layout: 需要 1–30 个不同的控件名称");
            ConfigurationSection icons = section(page, "Icons", file);
            for (String name : layout) {
                Kt.require(
                        Kt.isNotBlank(name) && name.indexOf('.') < 0 && Kt.noControl(name),
                        () -> file + ".Layout: 控件名不能含点或控制字符");
            }
            if (ItemMenuPage.usesItems(page, layout, file)) {
                Kt.require(
                        !page.contains("TitleStyle"), () -> file + ".TitleStyle: 原生物品页不使用画布标题样式");
                itemPages.put(
                        id, ItemMenuPage.parse(icons, layout, file, this::label, this::actions));
                String icon = optionalString(page, "Icon", file);
                List<String> keywords =
                        page.contains("Keywords")
                                ? strings(page.get("Keywords"), file + ".Keywords")
                                : Collections.emptyList();
                compiled.set(
                        "pages." + id,
                        Kt.mapOf(
                                "label",
                                title,
                                "icon",
                                icon,
                                "keywords",
                                keywords,
                                "widgets",
                                new ArrayList<Map<String, Object>>()));
                return;
            }
            widgets = new ArrayList<>();
            widgets.add(Kt.mapOf("type", "heading", "row", 1, "text", title));
            if (page.contains("TitleStyle")) {
                ConfigurationSection titleStyle = section(page, "TitleStyle", file);
                keys(
                        titleStyle,
                        Kt.setOf("Position", "FontSize", "Bold", "Width", "Color"),
                        file + ".TitleStyle");
                Map<String, Object> heading = new LinkedHashMap<>(widgets.get(0));
                appearance(titleStyle, heading, file + ".TitleStyle");
                widgets.set(0, heading);
            }
            int titleRow = (Integer) widgets.get(0).get("row");
            Object titleSize = widgets.get(0).get("font-size");
            row =
                    Math.max(
                            3,
                            titleRow
                                    + TitleFont.lineRows(
                                            titleSize instanceof Integer size ? size : 8));
            lower = false;
            for (String name : layout) {
                layoutEntry(name, icons);
            }
            String icon = optionalString(page, "Icon", file);
            List<String> keywords =
                    page.contains("Keywords")
                            ? strings(page.get("Keywords"), file + ".Keywords")
                            : Collections.emptyList();
            compiled.set(
                    "pages." + id,
                    Kt.mapOf(
                            "label",
                            title,
                            "icon",
                            icon,
                            "keywords",
                            keywords,
                            "widgets",
                            widgets));
        }

        void lowerPanel(String heading) {
            Kt.require(!lower, () -> file + ".Layout: 内容超出两个面板，请减少说明、拆分页面或调整顺序");
            lower = true;
            row = 13;
            widgets.add(Kt.mapOf("type", "heading", "row", 11, "text", heading));
        }

        /** One Layout entry of a canvas page (the body of the Kotlin layout loop). */
        private void layoutEntry(String name, ConfigurationSection icons) {
            Kt.require(
                    Kt.isNotBlank(name) && name.indexOf('.') < 0 && Kt.noControl(name),
                    () -> file + ".Layout: 控件名不能含点或控制字符");
            String at = file + ".Icons." + name;
            ConfigurationSection icon = section(icons, name, file);
            keys(
                    icon,
                    Kt.setOf(
                            "Type",
                            "Style",
                            "Name",
                            "Description",
                            "Bind",
                            "State",
                            "Options",
                            "Actions",
                            "Permission",
                            "RequiresPlugin",
                            "Position",
                            "LabelPosition",
                            "FontSize",
                            "Bold",
                            "Width",
                            "Color"),
                    at);
            String typeValue = optionalString(icon, "Type", at);
            String type = typeValue.isEmpty() ? "button" : typeValue;
            Kt.require(
                    Kt.setOf("button", "toggle", "slider", "dropdown", "heading", "text")
                            .contains(type),
                    () -> at + ".Type: 未知控件 " + type);
            String style = optionalString(icon, "Style", at);
            if (icon.contains("Style")) {
                Kt.require(
                        type.equals("toggle") && Kt.setOf("button", "switch").contains(style),
                        () -> at + ".Style: 仅 toggle 可使用 button / switch");
            }
            Position position = position(icon, "Position", at);
            int fontSize;
            if (icon.contains("FontSize")) {
                Kt.require(
                        icon.isInt("FontSize")
                                && icon.getInt("FontSize") >= 6
                                && icon.getInt("FontSize") <= 24,
                        () -> at + ".FontSize: 使用 6–24 的整数");
                fontSize = icon.getInt("FontSize");
            } else {
                fontSize = 8;
            }
            int textRows = TitleFont.lineRows(fontSize);
            Object rawName = icon.get("Name");
            String text = label(rawName != null ? rawName : name, at + ".Name");
            Object rawDescription = icon.get("Description");
            List<String> descriptions;
            if (rawDescription == null) {
                descriptions = Collections.emptyList();
            } else if (rawDescription instanceof List<?> values) {
                descriptions = new ArrayList<>(values.size());
                for (int index = 0; index < values.size(); index++) {
                    descriptions.add(label(values.get(index), at + ".Description[" + index + "]"));
                }
            } else {
                descriptions = Kt.listOf(label(rawDescription, at + ".Description"));
            }
            Kt.require(descriptions.size() <= 3, () -> at + ".Description: 最多 3 行");
            if (type.equals("heading")) {
                Kt.require(
                        descriptions.isEmpty()
                                && Kt.setOf(
                                                "Type",
                                                "Name",
                                                "Position",
                                                "FontSize",
                                                "Bold",
                                                "Width",
                                                "Color")
                                        .containsAll(icon.getKeys(false)),
                        () -> at + ": heading 支持 Type、Name、Position、FontSize、Bold、Width、Color");
                lowerPanel(text);
                Map<String, Object> heading = new LinkedHashMap<>(Kt.last(widgets));
                appearance(icon, heading, at);
                widgets.set(widgets.size() - 1, heading);
                row = Math.max(13, (Integer) heading.get("row") + textRows);
                return;
            }
            int height = Math.max(2, textRows) + descriptions.size() * textRows;
            // Popup rows align with the lower panel's first row so its
            // whole bitmap never paints over half an expanded option.
            if (position == null && type.equals("dropdown") && row % 2 != 0) {
                row++;
            }
            if (position == null && !lower && row + height > 9) {
                lowerPanel(text);
            }
            if (position != null) {
                row = position.row();
            }
            if (position == null && type.equals("dropdown") && row % 2 != 0) {
                row++;
            }
            Kt.require(
                    row + height <= (position != null ? DialogCanvas.ROWS : 24),
                    () -> at + ": 面板已放不下此控件，请减少说明或拆分页面");
            Map<String, Object> widget = new LinkedHashMap<>();
            widget.put("type", type);
            widget.put("row", row);
            widget.put(
                    Kt.setOf("dropdown", "slider", "toggle").contains(type) ? "label" : "text",
                    text);
            appearance(icon, widget, at);
            int originalX =
                    switch (type) {
                        case "text" -> 123;
                        case "slider" -> 280;
                        default -> 330;
                    };
            int deltaX = (position != null ? position.x() : originalX) - originalX;
            if (Kt.setOf("toggle", "slider", "dropdown").contains(type)) {
                Position labelPosition = position(icon, "LabelPosition", at);
                widget.put("label-x", labelPosition != null ? labelPosition.x() : 123 + deltaX);
                widget.put(
                        "label-row",
                        labelPosition != null
                                ? labelPosition.row()
                                : row + (fontSize == 8 ? 1 : 0));
            } else {
                Kt.require(
                        !icon.contains("LabelPosition"),
                        () -> at + ".LabelPosition: 仅用于开关、滑条、下拉框左侧的 Name");
            }
            Kt.require(
                    type.equals("text") || !icon.contains("Color"),
                    () -> at + ".Color: 仅用于 text / heading");
            if (!style.isEmpty()) {
                widget.put("toggle-style", style);
            }
            String bind = optionalString(icon, "Bind", at);
            Binding binding;
            if (bind.isEmpty()) {
                binding = null;
            } else {
                Binding found = bindings.get(bind);
                if (found == null) {
                    throw Kt.error(at + ".Bind: 未知绑定 " + bind);
                }
                binding = found;
            }
            String permission = optionalString(icon, "Permission", at);
            String plugin = optionalString(icon, "RequiresPlugin", at);
            if (type.equals("text")) {
                Kt.require(
                        Kt.setOf(
                                        "Type",
                                        "Name",
                                        "Description",
                                        "Position",
                                        "FontSize",
                                        "Bold",
                                        "Width",
                                        "Color")
                                .containsAll(icon.getKeys(false)),
                        () -> at + ": text 支持 Name、Description、Position、FontSize、Bold、Width、Color");
            }
            if (binding != null) {
                Kt.require(
                        Kt.setOf("toggle", "slider", "dropdown").contains(type)
                                && !icon.contains("State")
                                && !icon.contains("Actions"),
                        () -> at + ": Bind 用于开关/滑条/下拉框，不与 State 或 Actions 混用");
                Kt.require(
                        type.equals("toggle") == binding.choices().isEmpty(),
                        () -> at + ".Bind: " + bind + " 与 " + type + " 类型不匹配");
                widget.put("state", binding.state());
            } else if (Kt.setOf("toggle", "slider", "dropdown").contains(type)) {
                String state = optionalString(icon, "State", at);
                Kt.require(
                        STATE_VARIABLE.matcher(state).matches(),
                        () -> at + ".State: 无 Bind 时需要完整的 %PAPI变量%");
                String stateId = next();
                compiled.set("states." + stateId, state);
                widget.put("state", stateId);
            }
            if (type.equals("button") || type.equals("toggle")) {
                if (type.equals("button")) {
                    Kt.require(
                            !icon.contains("State"),
                            () -> at + ".State: button 不读取状态，使用 toggle / dropdown / slider");
                }
                Kt.require(!icon.contains("Options"), () -> at + ".Options: 只用于滑条或下拉框");
                widget.put(
                        "action",
                        binding != null
                                ? protectedAction(binding.action(), permission, plugin)
                                : actions(
                                        icon.get("Actions"), at + ".Actions", permission, plugin));
            }
            if (type.equals("slider") || type.equals("dropdown")) {
                Kt.require(
                        !icon.contains("Actions"), () -> at + ".Actions: 选择控件的动作写在 Options 每个选项内");
                ConfigurationSection options = section(icon, "Options", at);
                Kt.require(
                        options.getKeys(false).size() >= 2 && options.getKeys(false).size() <= 8,
                        () -> at + ".Options: 需要 2–8 个选项（off 必须加引号）");
                if (type.equals("dropdown")) {
                    Kt.require(
                            row + 2 + options.getKeys(false).size() * 2 <= DialogCanvas.ROWS,
                            () -> at + ".Options: 展开列表超出画布，请在 Layout 中前移或减少选项");
                }
                List<Map<String, Object>> choices = new ArrayList<>();
                for (String value : options.getKeys(false)) {
                    Kt.require(
                            value.indexOf('.') < 0 && Kt.isNotBlank(value),
                            () -> at + ".Options: 无效选项值 " + value);
                    Object item = options.get(value);
                    String optionLabel;
                    String action;
                    if (binding != null) {
                        String builtin = binding.choices().get(value);
                        if (builtin == null) {
                            throw Kt.error(
                                    at
                                            + ".Options."
                                            + value
                                            + ": "
                                            + bind
                                            + " 不支持此值，可选 "
                                            + binding.choices().keySet());
                        }
                        optionLabel = label(item, at + ".Options." + value);
                        action = protectedAction(builtin, permission, plugin);
                    } else {
                        ConfigurationSection option = section(options, value, at);
                        keys(option, Kt.setOf("Name", "Actions"), at + ".Options." + value);
                        optionLabel = label(option.get("Name"), at + ".Options." + value + ".Name");
                        action =
                                actions(
                                        option.get("Actions"),
                                        at + ".Options." + value + ".Actions",
                                        permission,
                                        plugin);
                    }
                    choices.add(Kt.mapOf("value", value, "label", optionLabel, "action", action));
                }
                widget.put("options", choices);
            }
            widgets.add(widget);
            for (int index = 0; index < descriptions.size(); index++) {
                Object width = widget.get("width");
                widgets.add(
                        Kt.mapOf(
                                "type",
                                "text",
                                "x",
                                123 + deltaX,
                                "row",
                                row + Math.max(2, textRows) + index * textRows,
                                "width",
                                Math.min(
                                        width instanceof Integer pixels ? pixels : 316,
                                        DialogCanvas.WIDTH - 123 - deltaX),
                                "text",
                                descriptions.get(index),
                                "font-size",
                                fontSize,
                                "bold",
                                icon.getBoolean("Bold", false)));
            }
            row += height;
        }
    }

    /** Every {@code {...}} in a command is {@code {player}} or {@code {uuid}}. */
    private static boolean placeholdersOnly(String argument) {
        Matcher matcher = PLACEHOLDER.matcher(argument);
        while (matcher.find()) {
            if (!Kt.setOf("{player}", "{uuid}").contains(matcher.group())) {
                return false;
            }
        }
        return true;
    }

    private static Position position(ConfigurationSection section, String key, String path) {
        if (!section.contains(key)) {
            return null;
        }
        List<?> values = section.getList(key);
        boolean valid = values != null && values.size() == 2;
        if (valid) {
            for (Object value : values) {
                if (!(value instanceof Integer number && number >= 0)) {
                    valid = false;
                    break;
                }
            }
        }
        Kt.require(valid, () -> path + "." + key + ": 使用 [X 像素, Y 行号]，每行 9 像素");
        return new Position((Integer) values.get(0), (Integer) values.get(1));
    }

    private static void appearance(
            ConfigurationSection source, Map<String, Object> target, String path) {
        Position position = position(source, "Position", path);
        if (position != null) {
            target.put("x", position.x());
            target.put("row", position.row());
        }
        Map<String, String> renamed =
                Kt.mapOf(
                        "FontSize",
                        "font-size",
                        "Bold",
                        "bold",
                        "Width",
                        "width",
                        "Color",
                        "color");
        for (Map.Entry<String, String> entry : renamed.entrySet()) {
            String from = entry.getKey();
            String to = entry.getValue();
            if (source.contains(from)) {
                target.put(to, Kt.requireNotNull(source.get(from)));
            }
        }
    }

    private static void keys(ConfigurationSection section, Set<String> allowed, String path) {
        Kt.require(
                Kt.minus(section.getKeys(false), allowed).isEmpty(),
                () -> path + ": 未知字段 " + Kt.minus(section.getKeys(false), allowed));
    }

    private static List<String> strings(Object value, String path) {
        boolean valid = value instanceof List<?>;
        if (valid) {
            for (Object item : (List<?>) value) {
                if (!(item instanceof String text && Kt.isNotBlank(text) && Kt.noControl(text))) {
                    valid = false;
                    break;
                }
            }
        }
        Kt.require(valid, () -> path + ": 需要字符串列表");
        List<?> values = (List<?>) value;
        List<String> result = new ArrayList<>(values.size());
        for (Object item : values) {
            result.add((String) item);
        }
        return result;
    }

    private static ConfigurationSection section(
            ConfigurationSection parent, String key, String path) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw Kt.error(path + "." + key + ": 缺少配置段");
        }
        return section;
    }

    private static String optionalString(ConfigurationSection section, String key, String path) {
        if (!section.contains(key)) {
            return "";
        }
        Object value = section.get(key);
        Kt.require(
                value instanceof String text && Kt.isNotBlank(text) && Kt.noControl(text),
                () -> path + "." + key + ": 需要非空字符串");
        return (String) value;
    }
}
