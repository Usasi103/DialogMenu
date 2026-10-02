package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parse completely before publishing a new immutable snapshot. Never save over the operator's YAML.
 */
public final class MenuConfigParser {

    private static final Map<String, DialogCanvas.Skin> skins = createSkins();

    private static final Set<String> requiredMessages =
            Kt.setOf(
                    "menu.title",
                    "search.title",
                    "search.keyword",
                    "search.submit",
                    "back",
                    "on",
                    "off",
                    "unavailable",
                    "pack.required",
                    "search.empty",
                    "setting.failed",
                    "setting.denied");

    private static final Set<String> builtinActions =
            Kt.setOf(
                    "close",
                    "refresh",
                    "search",
                    "language:zh_cn",
                    "language:en_us",
                    "theme:dark",
                    "theme:light");

    private static final Set<String> builtinStates =
            Kt.setOf("pickup", "loot-beams", "loot-sounds", "language", "theme");

    private static final Pattern STATE_VARIABLE = Pattern.compile("%[a-zA-Z0-9_:.\\-]+%");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]*}");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_-]{0,47}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    /** A clickable rectangle on the canvas, in pixels horizontally and rows vertically. */
    private record Area(int x, int row, int width, int rows) {}

    private MenuConfigParser() {}

    private static Map<String, DialogCanvas.Skin> createSkins() {
        Map<String, DialogCanvas.Skin> map = new LinkedHashMap<>();
        map.put("panel-top", DialogCanvas.PANEL_TOP);
        map.put("panel-bottom", DialogCanvas.PANEL_BOTTOM);
        map.put("nav", DialogCanvas.NAV);
        map.put("control", DialogCanvas.CONTROL);
        map.put("search", DialogCanvas.SEARCH);
        map.put("search-icon", DialogCanvas.SEARCH_ICON);
        map.put("refresh-icon", DialogCanvas.PANEL_ACTION);
        map.put("profile-icon", new DialogCanvas.Skin(0xE090, 9, 1));
        map.put("sound-icon", new DialogCanvas.Skin(0xE091, 9, 1));
        map.put("particles-icon", new DialogCanvas.Skin(0xE092, 9, 1));
        map.put("notices-icon", new DialogCanvas.Skin(0xE093, 9, 1));
        map.put("loot-icon", new DialogCanvas.Skin(0xE094, 9, 1));
        map.put("appearance-icon", new DialogCanvas.Skin(0xE095, 9, 1));
        return Collections.unmodifiableMap(map);
    }

    public static Map<String, DialogCanvas.Skin> skins() {
        return skins;
    }

    public static MenuDefinition parse(String menu, Map<MenuLanguage, String> languages) {
        YamlConfiguration root = yaml(menu, "menu.yml");
        keys(
                root,
                Kt.setOf(
                        "version",
                        "title",
                        "default-page",
                        "defaults",
                        "hide-focus-outline",
                        "navigation",
                        "footer",
                        "states",
                        "actions",
                        "common",
                        "pages"),
                "menu.yml");
        Kt.require(root.getInt("version") == 1, () -> "menu.yml: version 必须为 1");
        if (root.contains("hide-focus-outline")) {
            Kt.require(
                    root.isBoolean("hide-focus-outline"),
                    () -> "hide-focus-outline: 必须为 true / false");
        }
        Map<MenuLanguage, Map<String, String>> translations = new LinkedHashMap<>();
        for (Map.Entry<MenuLanguage, String> entry : languages.entrySet()) {
            MenuLanguage language = entry.getKey();
            String source = entry.getValue();
            String file = "languages/" + language.id() + ".yml";
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> value : yaml(source, file).getValues(true).entrySet()) {
                if (!(value.getValue() instanceof ConfigurationSection)) {
                    values.put(value.getKey(), value.getValue());
                }
            }
            boolean allStrings = true;
            for (Object value : values.values()) {
                if (!(value instanceof String)) {
                    allStrings = false;
                    break;
                }
            }
            Kt.require(allStrings, () -> file + ": 文案必须加引号并使用字符串");
            Map<String, String> messages = new LinkedHashMap<>();
            for (Map.Entry<String, Object> value : values.entrySet()) {
                messages.put(value.getKey(), (String) value.getValue());
            }
            Kt.require(
                    messages.keySet().containsAll(requiredMessages),
                    () -> file + ": 缺少文案 " + Kt.minus(requiredMessages, messages.keySet()));
            for (Map.Entry<String, String> message : messages.entrySet()) {
                line(message.getValue(), file + ":" + message.getKey());
            }
            translations.put(language, messages);
        }
        Kt.require(
                translations.keySet().equals(new LinkedHashSet<>(Kt.listOf(MenuLanguage.values()))),
                () -> "缺少语言文件");
        ConfigurationSection stateSection = section(root, "states");
        Map<String, String> states = new LinkedHashMap<>();
        for (String id : stateSection.getKeys(false)) {
            identifier(id, "states");
            String binding = string(stateSection, id);
            Kt.require(
                    builtinStates.contains(binding) || STATE_VARIABLE.matcher(binding).matches(),
                    () -> "states." + id + ": 未知状态 " + binding);
            states.put(id, binding);
        }
        ConfigurationSection actionSection = section(root, "actions");
        Map<String, MenuAction> actions = new LinkedHashMap<>();
        for (String id : actionSection.getKeys(false)) {
            identifier(id, "actions");
            String at = "actions." + id;
            ConfigurationSection conf = section(actionSection, id);
            keys(
                    conf,
                    Kt.setOf(
                            "type",
                            "command",
                            "when-true",
                            "when-false",
                            "state",
                            "value",
                            "permission",
                            "requires-plugin",
                            "close"),
                    at);
            String type = string(conf, "type");
            if (conf.contains("close")) {
                Kt.require(conf.isBoolean("close"), () -> at + ".close: 必须为 true / false");
            }
            for (String key : Kt.listOf("permission", "requires-plugin")) {
                if (conf.contains(key)) {
                    Kt.require(conf.get(key) instanceof String, () -> at + "." + key + ": 必须为字符串");
                }
            }
            Kt.require(
                    Kt.setOf(
                                    "builtin",
                                    "page",
                                    "player-command",
                                    "console-command",
                                    "toggle-command")
                            .contains(type),
                    () -> at + ".type: 未知动作 " + type);
            String value = Kt.setOf("builtin", "page").contains(type) ? string(conf, "value") : "";
            if (type.equals("builtin")) {
                Kt.require(builtinActions.contains(value), () -> at + ".value: 未知内置动作 " + value);
            }
            String command =
                    type.endsWith("-command") && !type.equals("toggle-command")
                            ? command(conf, at, "command")
                            : "";
            String whenTrue = type.equals("toggle-command") ? command(conf, at, "when-true") : "";
            String whenFalse = type.equals("toggle-command") ? command(conf, at, "when-false") : "";
            String state =
                    type.equals("toggle-command") ? state(states, string(conf, "state"), at) : "";
            actions.put(
                    id,
                    new MenuAction(
                            type,
                            command,
                            whenTrue,
                            whenFalse,
                            state,
                            value,
                            Objects.requireNonNull(conf.getString("permission", "")),
                            Objects.requireNonNull(conf.getString("requires-plugin", "")),
                            conf.getBoolean("close", false)));
        }
        ConfigurationSection pagesSection = section(root, "pages");
        Map<String, MenuPage> pages = new LinkedHashMap<>();
        for (String id : pagesSection.getKeys(false)) {
            identifier(id, "pages");
            ConfigurationSection conf = section(pagesSection, id);
            keys(conf, Kt.setOf("label", "icon", "keywords", "widgets"), "pages." + id);
            String icon = Objects.requireNonNull(conf.getString("icon", ""));
            if (conf.contains("keywords")) {
                Kt.require(
                        conf.isList("keywords")
                                && allStrings(Objects.requireNonNull(conf.getList("keywords"))),
                        () -> "pages." + id + ".keywords: 必须为字符串列表");
            }
            DialogCanvas.Skin iconSkin = skins.get(icon);
            Kt.require(
                    icon.isEmpty()
                            || (iconSkin != null && iconSkin.width() == 9 && iconSkin.rows() == 1),
                    () -> "pages." + id + ".icon: 必须使用 9 像素图标");
            String label = text(translations, string(conf, "label"), "pages." + id + ".label");
            List<String> keywords = new ArrayList<>();
            for (String keyword : conf.getStringList("keywords")) {
                if (Kt.isNotBlank(keyword)) {
                    keywords.add(keyword);
                }
            }
            List<MenuWidget> widgets = widgets(conf, "widgets", translations, states, actions);
            pages.put(id, new MenuPage(id, label, icon, keywords, widgets));
        }
        Kt.require(!pages.isEmpty() && pages.size() <= 12, () -> "pages: 需要 1–12 个页面，导航必须能放入画布");
        for (Map.Entry<String, MenuAction> entry : actions.entrySet()) {
            String id = entry.getKey();
            MenuAction definition = entry.getValue();
            if (definition.type().equals("page")) {
                Kt.require(
                        pages.containsKey(definition.value()),
                        () -> "actions." + id + ".value: 页面不存在");
            }
        }
        List<MenuWidget> common = widgets(root, "common", translations, states, actions);
        ConfigurationSection navigation = section(root, "navigation");
        keys(navigation, Kt.setOf("x", "row", "step", "font-size", "bold"), "navigation");
        int navX = number(navigation, "x", 0, "navigation");
        int navRow = number(navigation, "row", 3, "navigation");
        int navStep = number(navigation, "step", 2, "navigation");
        int navTextSize = number(navigation, "font-size", 8, "navigation");
        Kt.require(navTextSize >= 6 && navTextSize <= 12, () -> "navigation.font-size: 使用 6–12");
        Kt.require(
                !navigation.contains("bold") || navigation.isBoolean("bold"),
                () -> "navigation.bold: 需要 true / false");
        Kt.require(
                navX >= 0
                        && navX + 102 <= DialogCanvas.WIDTH
                        && navRow >= 0
                        && navStep >= 2
                        && navRow + (pages.size() - 1) * navStep + 2 <= DialogCanvas.ROWS,
                () -> "navigation: 导航超出画布或按钮重叠");
        // Prevent conflicting hit areas instead of silently routing a click to an obscured control.
        List<Area> navigationAreas = new ArrayList<>();
        for (int index = 0; index < pages.size(); index++) {
            navigationAreas.add(new Area(navX, navRow + index * navStep, 102, 2));
        }
        for (MenuPage page : pages.values()) {
            List<Area> areas = new ArrayList<>(navigationAreas);
            for (MenuWidget widget : Kt.plus(common, page.widgets())) {
                if (!widget.action().isEmpty()
                        || widget.kind() == WidgetKind.SLIDER
                        || widget.kind() == WidgetKind.DROPDOWN) {
                    int rows;
                    if (widget.kind() == WidgetKind.SPRITE) {
                        rows = Kt.getValue(skins, widget.skin()).rows();
                    } else if (widget.kind() == WidgetKind.TEXT
                            || widget.kind() == WidgetKind.HEADING) {
                        rows = TitleFont.lineRows(widget.textSize());
                    } else {
                        rows = 2;
                    }
                    areas.add(new Area(widget.x(), widget.row(), widget.width(), rows));
                }
            }
            for (int i = 0; i < areas.size(); i++) {
                Area a = areas.get(i);
                for (int j = i + 1; j < areas.size(); j++) {
                    Area b = areas.get(j);
                    Kt.require(
                            a.x() + a.width() <= b.x()
                                    || b.x() + b.width() <= a.x()
                                    || a.row() + a.rows() <= b.row()
                                    || b.row() + b.rows() <= a.row(),
                            () -> "pages." + page.id() + ": 点击区域重叠，请检查 x / row");
                }
            }
        }
        ConfigurationSection defaults = section(root, "defaults");
        keys(defaults, Kt.setOf("language", "theme"), "defaults");
        String language = string(defaults, "language");
        String theme = string(defaults, "theme");
        boolean knownLanguage = false;
        for (MenuLanguage entry : MenuLanguage.values()) {
            if (entry.id().equals(language)) {
                knownLanguage = true;
                break;
            }
        }
        boolean knownTheme = false;
        for (MenuTheme entry : MenuTheme.values()) {
            if (entry.id().equals(theme)) {
                knownTheme = true;
                break;
            }
        }
        Kt.require(knownLanguage && knownTheme, () -> "defaults: 语言或主题无效");
        String defaultPage = string(root, "default-page");
        Kt.require(pages.containsKey(defaultPage), () -> "default-page: 页面不存在 " + defaultPage);
        ConfigurationSection footer = section(root, "footer");
        keys(footer, Kt.setOf("enabled", "label", "action"), "footer");
        Kt.require(
                !footer.contains("enabled") || footer.isBoolean("enabled"),
                () -> "footer.enabled: 必须为 true / false");
        return new MenuDefinition(
                text(translations, string(root, "title"), "title"),
                defaultPage,
                MenuLanguage.parse(language),
                MenuTheme.parse(theme),
                root.getBoolean("hide-focus-outline", true),
                navX,
                navRow,
                navStep,
                text(translations, string(footer, "label"), "footer.label"),
                action(actions, string(footer, "action"), "footer.action"),
                states,
                actions,
                common,
                pages,
                translations,
                footer.getBoolean("enabled", false),
                navTextSize,
                navigation.getBoolean("bold", false),
                false);
    }

    /** A {@code $key} value must exist in every language file; any other text is literal. */
    private static String text(
            Map<MenuLanguage, Map<String, String>> translations, String value, String path) {
        line(value, path);
        if (value.startsWith("$")) {
            for (Map.Entry<MenuLanguage, Map<String, String>> entry : translations.entrySet()) {
                MenuLanguage language = entry.getKey();
                Map<String, String> messages = entry.getValue();
                Kt.require(
                        messages.containsKey(Kt.drop(value, 1)),
                        () -> path + ": languages/" + language.id() + ".yml 缺少 " + value);
            }
        }
        return value;
    }

    private static String state(Map<String, String> states, String id, String path) {
        Kt.require(states.containsKey(id), () -> path + ": 未定义状态 " + id);
        return id;
    }

    private static String action(Map<String, MenuAction> actions, String id, String path) {
        Kt.require(actions.containsKey(id), () -> path + ": 未定义动作 " + id);
        return id;
    }

    private static String command(ConfigurationSection conf, String at, String key) {
        String value = string(conf, key);
        line(value, at + "." + key);
        Kt.require(
                !value.startsWith("/") && value.length() <= 512,
                () -> at + "." + key + ": 指令不加 /，最长 512 字符");
        Kt.require(
                value.indexOf('%') < 0 && placeholdersOnly(value),
                () -> at + "." + key + ": 指令仅支持 {player}、{uuid} 占位符");
        return value;
    }

    /** Every {@code {...}} in a command is {@code {player}} or {@code {uuid}}. */
    private static boolean placeholdersOnly(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            if (!Kt.setOf("{player}", "{uuid}").contains(matcher.group())) {
                return false;
            }
        }
        return true;
    }

    private static boolean allStrings(List<?> values) {
        for (Object value : values) {
            if (!(value instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private static List<MenuWidget> widgets(
            ConfigurationSection parent,
            String key,
            Map<MenuLanguage, Map<String, String>> translations,
            Map<String, String> states,
            Map<String, MenuAction> actions) {
        Kt.require(parent.isList(key), () -> key + ": 必须是 YAML 列表");
        List<?> entries = Objects.requireNonNull(parent.getList(key));
        Kt.require(entries.size() <= 100, () -> key + ": 最多 100 个控件");
        List<MenuWidget> widgets = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            String at = parent.getCurrentPath() + "." + key + "[" + index + "]";
            widgets.add(widget(at, entries.get(index), translations, states, actions));
        }
        return widgets;
    }

    private static MenuWidget widget(
            String at,
            Object raw,
            Map<MenuLanguage, Map<String, String>> translations,
            Map<String, String> states,
            Map<String, MenuAction> actions) {
        Kt.require(raw instanceof Map<?, ?>, () -> at + ": 控件必须是对象");
        ConfigurationSection conf =
                new YamlConfiguration().createSection("widget", (Map<?, ?>) raw);
        keys(
                conf,
                Kt.setOf(
                        "type",
                        "x",
                        "row",
                        "text",
                        "label",
                        "label-x",
                        "width",
                        "color",
                        "skin",
                        "action",
                        "state",
                        "selected",
                        "options",
                        "toggle-style",
                        "font-size",
                        "bold",
                        "label-row"),
                at);
        WidgetKind kind = kind(conf);
        if (kind == null) {
            throw Kt.error(at + ".type: 未知控件类型");
        }
        ToggleStyle toggleStyle;
        if (conf.contains("toggle-style")) {
            Kt.require(kind == WidgetKind.TOGGLE, () -> at + ".toggle-style: 只用于 toggle");
            ToggleStyle found = toggleStyle(conf);
            if (found == null) {
                throw Kt.error(at + ".toggle-style: 使用 button / switch");
            }
            toggleStyle = found;
        } else {
            toggleStyle = ToggleStyle.BUTTON;
        }
        int defaultX =
                switch (kind) {
                    case HEADING -> 122;
                    case TEXT -> 123;
                    case SLIDER -> 280;
                    case SPRITE -> 114;
                    default -> 330;
                };
        for (String name :
                Kt.listOf("text", "label", "color", "skin", "action", "state", "selected")) {
            if (conf.contains(name)) {
                Kt.require(
                        conf.get(name) instanceof String,
                        () -> at + "." + name + ": 必须为字符串（off/on 请加引号）");
            }
        }
        if (conf.contains("width")) {
            Kt.require(
                    kind == WidgetKind.TEXT || kind == WidgetKind.HEADING,
                    () -> at + ".width: 只有 text/heading 可设置宽度；贴图控件使用固定尺寸");
        }
        int x = number(conf, "x", defaultX, at);
        int row = number(conf, "row", null, at);
        int labelX = number(conf, "label-x", 123, at);
        int textSize = number(conf, "font-size", 8, at);
        Kt.require(textSize >= 6 && textSize <= 24, () -> at + ".font-size: 使用 6–24");
        Kt.require(
                kind == WidgetKind.TEXT || kind == WidgetKind.HEADING || textSize <= 12,
                () -> at + ".font-size: 固定高度按钮、开关、滑条与下拉框支持 6–12；纯文字支持 6–24");
        Kt.require(
                !conf.contains("bold") || conf.isBoolean("bold"),
                () -> at + ".bold: 需要 true / false");
        boolean bold = conf.getBoolean("bold", false);
        int labelRow = number(conf, "label-row", row + (textSize == 8 ? 1 : 0), at);
        String skin =
                Objects.requireNonNull(
                        conf.getString(
                                "skin", kind == WidgetKind.SPRITE ? "panel-top" : "control"));
        DialogCanvas.Skin sprite = skins.get(skin);
        if (sprite == null) {
            throw Kt.error(at + ".skin: 未知贴图 " + skin);
        }
        if (kind == WidgetKind.BUTTON) {
            Kt.require(
                    Kt.setOf("nav", "search", "control").contains(skin),
                    () -> at + ".skin: 按钮贴图必须为 nav / search / control");
        }
        int width =
                switch (kind) {
                    case TEXT, HEADING -> number(conf, "width", 316, at);
                    case SLIDER -> 164;
                    case TOGGLE, DROPDOWN -> 114;
                    default -> sprite.width();
                };
        int height =
                switch (kind) {
                    case TEXT, HEADING -> TitleFont.lineRows(textSize);
                    case SPRITE -> sprite.rows();
                    default -> 2;
                };
        Kt.require(
                x >= 0
                        && row >= 0
                        && width > 0
                        && x + width <= DialogCanvas.WIDTH
                        && row + height <= DialogCanvas.ROWS,
                () -> at + ": 控件超出 450 像素 × 29 行画布");
        String label = text(translations, Objects.requireNonNull(conf.getString("label", "")), at);
        if (!label.isEmpty()) {
            Kt.require(
                    labelX >= 0
                            && labelRow >= 0
                            && labelRow + TitleFont.lineRows(textSize) <= DialogCanvas.ROWS
                            && labelX < x - (kind == WidgetKind.SLIDER ? 60 : 6),
                    () -> at + ".label-x: 左侧标签空间不足");
        }
        if (kind == WidgetKind.SLIDER) {
            Kt.require(x >= 60, () -> at + ".x: 滑条左侧需要 60 像素显示当前值");
        }
        List<MenuOption> options;
        if (kind == WidgetKind.SLIDER || kind == WidgetKind.DROPDOWN) {
            List<Map<?, ?>> list = conf.getMapList("options");
            options = new ArrayList<>(list.size());
            for (int n = 0; n < list.size(); n++) {
                options.add(option(at, n, list.get(n), translations, actions));
            }
        } else {
            options = Collections.emptyList();
        }
        if (kind == WidgetKind.SLIDER || kind == WidgetKind.DROPDOWN) {
            Set<String> distinct = new LinkedHashSet<>();
            for (MenuOption option : options) {
                distinct.add(Kt.lower(option.value()));
            }
            Kt.require(
                    options.size() >= 2 && options.size() <= 8 && distinct.size() == options.size(),
                    () -> at + ".options: 需要 2–8 个不同档位");
        }
        if (kind == WidgetKind.DROPDOWN) {
            Kt.require(
                    row + 2 + options.size() * 2 <= DialogCanvas.ROWS,
                    () -> at + ".options: 展开后的下拉列表超出画布，请上移 row 或减少选项");
        }
        String binding = Objects.requireNonNull(conf.getString("state", ""));
        if (kind == WidgetKind.TOGGLE
                || kind == WidgetKind.SLIDER
                || kind == WidgetKind.DROPDOWN
                || conf.contains("selected")) {
            state(states, binding, at);
        } else if (!binding.isEmpty()) {
            state(states, binding, at);
        }
        String click = Objects.requireNonNull(conf.getString("action", ""));
        if (kind == WidgetKind.BUTTON || kind == WidgetKind.TOGGLE || !click.isEmpty()) {
            action(actions, click, at);
        }
        String color =
                Objects.requireNonNull(
                        conf.getString("color", kind == WidgetKind.HEADING ? "heading" : "muted"));
        Kt.require(
                Kt.setOf("text", "muted", "heading").contains(color)
                        || COLOR.matcher(color).matches(),
                () -> at + ".color: 使用 text/muted/heading 或 '#RRGGBB'");
        String text = text(translations, Objects.requireNonNull(conf.getString("text", "")), at);
        return new MenuWidget(
                kind,
                x,
                row,
                text,
                label,
                labelX,
                width,
                color,
                skin,
                click,
                binding,
                Objects.requireNonNull(conf.getString("selected", "")),
                options,
                toggleStyle,
                textSize,
                bold,
                labelRow);
    }

    /** Case-insensitive {@code type}; the value is read for every candidate like the original. */
    private static WidgetKind kind(ConfigurationSection conf) {
        for (WidgetKind entry : WidgetKind.values()) {
            if (Kt.equalsIgnoreCase(entry.name(), string(conf, "type"))) {
                return entry;
            }
        }
        return null;
    }

    private static ToggleStyle toggleStyle(ConfigurationSection conf) {
        for (ToggleStyle entry : ToggleStyle.values()) {
            if (Kt.equalsIgnoreCase(entry.name(), string(conf, "toggle-style"))) {
                return entry;
            }
        }
        return null;
    }

    private static MenuOption option(
            String at,
            int n,
            Map<?, ?> option,
            Map<MenuLanguage, Map<String, String>> translations,
            Map<String, MenuAction> actions) {
        boolean known = true;
        for (Object key : option.keySet()) {
            if (!Kt.setOf("value", "label", "action").contains(key)) {
                known = false;
                break;
            }
        }
        Kt.require(known, () -> at + ".options[" + n + "]: 未知字段");
        String value = field(option, "value", at, n);
        String label = text(translations, field(option, "label", at, n), at);
        String action = action(actions, field(option, "action", at, n), at);
        return new MenuOption(value, label, action);
    }

    private static String field(Map<?, ?> option, String name, String at, int n) {
        if (option.get(name) instanceof String value && Kt.isNotBlank(value)) {
            return value;
        }
        throw Kt.error(at + ".options[" + n + "]." + name + ": 必须是字符串（off/on 请加引号）");
    }

    static YamlConfiguration yaml(String source, String path) {
        Kt.require(source.length() <= 1_048_576, () -> path + ": 文件超过 1 MiB");
        try {
            // Bukkit accepts duplicate keys by default. Reject them before
            // converting into sections so an accidental second pages/actions
            // block cannot silently replace the administrator's first block.
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(40);
            options.setCodePointLimit(1_048_576);
            new Yaml(new SafeConstructor(options)).load(source);
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(source);
            return configuration;
        } catch (Exception error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    private static void keys(ConfigurationSection conf, Set<String> allowed, String path) {
        Set<String> unknown = Kt.minus(conf.getKeys(false), allowed);
        Kt.require(unknown.isEmpty(), () -> path + ": 未知字段 " + unknown);
    }

    private static void identifier(String value, String path) {
        Kt.require(IDENTIFIER.matcher(value).matches(), () -> path + ": ID 只能使用小写字母、数字、-、_（以字母开头）");
    }

    private static void line(String value, String path) {
        Kt.require(Kt.noControl(value), () -> path + ": 不支持换行或控制字符");
    }

    private static ConfigurationSection section(ConfigurationSection conf, String key) {
        ConfigurationSection section = conf.getConfigurationSection(key);
        if (section == null) {
            throw Kt.error(conf.getCurrentPath() + "." + key + ": 缺少配置段");
        }
        return section;
    }

    private static String string(ConfigurationSection conf, String key) {
        if (conf.get(key) instanceof String value && Kt.isNotBlank(value)) {
            return value;
        }
        throw Kt.error(conf.getCurrentPath() + "." + key + ": 需要非空字符串（off/on 请加引号）");
    }

    private static int number(
            ConfigurationSection conf, String key, Integer defaultValue, String path) {
        if (!conf.contains(key) && defaultValue != null) {
            return defaultValue;
        }
        Kt.require(conf.isInt(key), () -> path + "." + key + ": 需要整数");
        return conf.getInt(key);
    }
}
