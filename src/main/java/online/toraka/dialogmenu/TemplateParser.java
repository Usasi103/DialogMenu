package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class TemplateParser {

    private static final Pattern idPattern = Pattern.compile("[a-z][a-z0-9_-]{0,47}");
    private static final Pattern valuePattern = Pattern.compile("[a-zA-Z0-9_-]{1,48}");
    private static final Pattern placeholderPattern = Pattern.compile("%[^%\\p{Cntrl}]{1,254}%");
    private static final Pattern colorPattern = Pattern.compile("#[a-fA-F0-9]{6}");

    private TemplateParser() {}

    public static DialogTemplate parse(String id, String source) {
        return parse(id, source, "templates/" + id + ".yml");
    }

    public static DialogTemplate parse(String id, String source, String path) {
        return parse(id, source, path, null, null);
    }

    /**
     * {@code page} and {@code open} resolve a menu's {@code page:} / {@code open:} targets (see
     * {@link ReactionParser.Scope}); standalone templates pass null and only link with {@code
     * template:}.
     */
    public static DialogTemplate parse(
            String id,
            String source,
            String path,
            Function<String, ReactionParser.Target> page,
            Function<String, ReactionParser.Target> open) {
        Kt.require(
                Kt.split(id, '/').size() <= 2 && allMatch(Kt.split(id, '/'), idPattern),
                () -> "无效菜单页面 ID " + id);
        YamlConfiguration root = MenuConfigParser.yaml(source, path);
        keys(
                root,
                Kt.setOf(
                        "Version",
                        "Title",
                        "Skin",
                        "Canvas",
                        "Variables",
                        "Placeholders",
                        "Elements"),
                path);
        Kt.require(Objects.equals(root.get("Version"), 1), () -> path + ".Version: 必须为 1");
        String configuredTitle = root.getString("Title");
        String title = line(configuredTitle != null ? configuredTitle : id, path);
        String theme = Objects.requireNonNull(root.getString("Skin", "amethyst"));
        Kt.require(
                Kt.setOf("amethyst", "parchment").contains(theme),
                () -> path + ".Skin: amethyst 或 parchment");
        ConfigurationSection geometry = root.getConfigurationSection("Canvas");
        if (geometry != null) {
            keys(
                    geometry,
                    Kt.setOf("Width", "Rows", "Background", "HideFocusOutline"),
                    path + ".Canvas");
        }
        int width = integer(geometry, "Width", 552, 180, 960, path);
        int rows = integer(geometry, "Rows", 20, 8, 28, path);
        Kt.require(
                geometry == null
                        || !geometry.contains("HideFocusOutline")
                        || geometry.isBoolean("HideFocusOutline"),
                () -> path + ".Canvas.HideFocusOutline: 需要 true/false");
        boolean hide = geometry != null ? geometry.getBoolean("HideFocusOutline", true) : true;
        Kt.require(
                !hide || width == 552 && rows == 20,
                () -> path + ".Canvas: 隐藏焦点框只支持默认 552 × 20 行；修改尺寸时将 HideFocusOutline 设为 false");
        String configuredBackground =
                geometry != null ? geometry.getString("Background", "panel") : null;
        String backgroundName = configuredBackground != null ? configuredBackground : "panel";
        DialogCanvas.Skin background =
                backgroundName.equals("none") ? null : TemplateSkins.get(theme, backgroundName);
        Kt.require(
                background == null || background.width() <= width && background.rows() <= rows,
                () -> path + ": 背景超出画布");
        ConfigurationSection variableSection = root.getConfigurationSection("Variables");
        Map<String, List<String>> variables;
        if (variableSection != null) {
            variables = new LinkedHashMap<>();
            for (String key : variableSection.getKeys(false)) {
                Kt.require(
                        idPattern.matcher(key).matches()
                                && !Kt.setOf("player", "uuid").contains(key),
                        () -> path + ".Variables: 无效变量 " + key);
                List<String> values =
                        strings(root.get("Variables." + key), path + ".Variables." + key);
                Kt.require(
                        !values.isEmpty()
                                && values.size() <= 16
                                && new LinkedHashSet<>(values).size() == values.size()
                                && allMatch(values, valuePattern),
                        () -> path + ".Variables." + key + ": 需要不重复的字母/数字/-/_枚举值，首项为默认值");
                variables.put(key, values);
            }
        } else {
            variables = Collections.emptyMap();
        }
        Kt.require(
                !root.contains("Placeholders") || root.isConfigurationSection("Placeholders"),
                () -> path + ".Placeholders: 需要 名称: \"%PAPI变量%\" 配置段");
        ConfigurationSection placeholderSection = root.getConfigurationSection("Placeholders");
        Map<String, String> placeholders;
        if (placeholderSection != null) {
            placeholders = new LinkedHashMap<>();
            for (String key : placeholderSection.getKeys(false)) {
                Kt.require(
                        idPattern.matcher(key).matches()
                                && !Kt.setOf("player", "uuid", "ping", "world").contains(key)
                                && !variables.containsKey(key),
                        () ->
                                path
                                        + ".Placeholders: 无效名称 "
                                        + key
                                        + "，不能与 Variables 或 player/uuid/ping/world 重名");
                Object token = root.get("Placeholders." + key);
                Kt.require(
                        token instanceof String text && placeholderPattern.matcher(text).matches(),
                        () ->
                                path
                                        + ".Placeholders."
                                        + key
                                        + ": 需要一个完整的 %PAPI变量%，如 \"%player_level%\"");
                placeholders.put(key, (String) token);
            }
        } else {
            placeholders = Collections.emptyMap();
        }
        Kt.require(placeholders.size() <= 32, () -> path + ".Placeholders: 最多 32 个");
        Set<String> commandNames = new LinkedHashSet<>(Kt.listOf("player", "uuid"));
        commandNames.addAll(variables.keySet());
        commandNames.addAll(placeholders.keySet());
        ReactionParser.Scope scope =
                new ReactionParser.Scope(
                        true,
                        clause -> nameProblem(clause, variables, placeholders),
                        commandNames,
                        variables,
                        page,
                        open);
        ConfigurationSection section =
                Kt.requireNotNull(
                        root.getConfigurationSection("Elements"), () -> path + ": 缺少 Elements");
        Kt.require(
                section.getKeys(false).size() >= 1 && section.getKeys(false).size() <= 64,
                () -> path + ".Elements: 需要 1–64 个元素");
        List<TemplateElement> elements = new ArrayList<>();
        for (String name : section.getKeys(false)) {
            Kt.require(
                    idPattern.matcher(name).matches(),
                    () -> path + ".Elements: 元素 ID 使用小写英文 " + name);
            ConfigurationSection conf =
                    Kt.requireNotNull(
                            section.getConfigurationSection(name),
                            () -> path + "." + name + ": 需要配置段");
            String location = path + ".Elements." + name;
            keys(
                    conf,
                    Kt.setOf(
                            "Type",
                            "Position",
                            "Width",
                            "Rows",
                            "Text",
                            "Color",
                            "Sprite",
                            "SelectedSprite",
                            "VisibleWhen",
                            "SelectedWhen",
                            "Permission",
                            "Actions",
                            "Font",
                            "Glyph",
                            "Advance",
                            "Image",
                            "Cases",
                            "TextSize",
                            "FontSize",
                            "Bold"),
                    location);
            String type = Objects.requireNonNull(conf.getString("Type", "text"));
            Kt.require(
                    Kt.setOf("text", "button", "sprite").contains(type),
                    () -> location + ".Type: text/button/sprite");
            int legacySize = integer(conf, "TextSize", 8, 6, 24, location);
            int textSize = integer(conf, "FontSize", legacySize, 6, 24, location);
            Kt.require(
                    !conf.contains("FontSize")
                            || !conf.contains("TextSize")
                            || legacySize == textSize,
                    () -> location + ": FontSize 与 TextSize 同时设置时必须相同");
            Kt.require(
                    type.equals("text") || textSize == 8,
                    () -> location + ".FontSize: 仅文字元素支持自定义字号");
            Kt.require(
                    !conf.contains("Bold") || conf.isBoolean("Bold"),
                    () -> location + ".Bold: 需要 true/false");
            boolean bold = conf.getBoolean("Bold", false);
            int lineRows = TitleFont.lineRows(textSize);
            List<?> position = conf.getList("Position");
            Kt.require(
                    position != null
                            && position.size() == 2
                            && position.stream()
                                    .allMatch(it -> it instanceof Integer number && number >= 0),
                    () -> location + ".Position: [横向像素, 纵向行号]，每行 9 像素");
            int x = (Integer) position.get(0);
            int row = (Integer) position.get(1);
            String customFont = conf.getString("Font");
            Object rawImage = conf.get("Image");
            MenuImageRequest image =
                    rawImage != null ? parseImage(rawImage, location + ".Image") : null;
            Kt.require(
                    image == null || type.equals("sprite"), () -> location + ".Image: 仅用于 sprite");
            Kt.require(
                    image == null
                            || Kt.listOf("Font", "Glyph", "Advance", "Sprite").stream()
                                    .noneMatch(key -> conf.contains(key)),
                    () -> location + ": Image 不能与 Font / Glyph / Advance / Sprite 同时使用");
            DialogCanvas.Skin sprite;
            boolean resizable = false;
            if (customFont != null) {
                Kt.require(type.equals("sprite"), () -> location + ".Font: 仅用于 sprite");
                int spriteWidth = integer(conf, "Width", 108, 1, 256, location);
                int spriteRows = integer(conf, "Rows", 12, 1, 28, location);
                sprite =
                        new DialogCanvas.Skin(
                                glyph(conf.get("Glyph"), location + ".Glyph"),
                                spriteWidth,
                                spriteRows,
                                ResourceFont.resourceFont(customFont),
                                Kt.listOf(
                                        integer(
                                                conf,
                                                "Advance",
                                                spriteWidth + 1,
                                                0,
                                                1024,
                                                location)),
                                1);
            } else if (!type.equals("text") && image == null) {
                String spriteName =
                        Objects.requireNonNull(
                                conf.getString(
                                        "Sprite", type.equals("button") ? "button" : "emblem"));
                sprite = TemplateSkins.get(theme, spriteName);
                // Button looks stretch from their edges and middle column; height stays 2 rows.
                resizable = type.equals("button") && TemplateSkins.resizable(theme, spriteName);
                Kt.require(
                        resizable || !type.equals("button") || !conf.contains("Width"),
                        () -> location + ".Width: 仅 button / selected / wide-button 贴图的按钮可设置宽度");
                if (resizable) {
                    sprite =
                            TemplateSkins.get(
                                    theme,
                                    spriteName,
                                    integer(conf, "Width", sprite.width(), 16, 960, location));
                }
            } else {
                sprite = null;
            }
            int elementWidth;
            if (sprite != null) {
                elementWidth = sprite.width();
            } else if (image != null) {
                elementWidth = integer(conf, "Width", 108, 1, 256, location);
            } else {
                elementWidth = integer(conf, "Width", width - x - 12, 1, 960, location);
            }
            int elementRows;
            if (sprite != null) {
                elementRows = sprite.rows();
            } else {
                elementRows = integer(conf, "Rows", image != null ? 12 : lineRows, 1, 28, location);
            }
            Kt.require(
                    elementRows >= lineRows, () -> location + ".Rows: 当前字号至少占 " + lineRows + " 行");
            Kt.require(
                    x + elementWidth <= width && row + elementRows <= rows,
                    () -> location + ": 元素超出画布");
            Object rawText = conf.get("Text");
            List<String> lines;
            if (rawText == null) {
                lines = Collections.emptyList();
            } else if (rawText instanceof String text) {
                lines = Kt.listOf(line(text, location));
            } else {
                lines = new ArrayList<>();
                for (String text : strings(rawText, location)) {
                    lines.add(line(text, location));
                }
            }
            Kt.require(
                    !type.equals("button") || lines.size() == 1,
                    () -> location + ".Text: 按钮需要单行文字");
            Object rawActions = conf.get("Actions");
            Kt.require(
                    type.equals("button") == (rawActions != null),
                    () ->
                            location
                                    + ".Actions: "
                                    + (type.equals("button") ? "按钮需要动作" : "仅用于 button"));
            MenuReaction actions =
                    rawActions != null
                            ? ReactionParser.parse(rawActions, location + ".Actions", scope)
                            : MenuReaction.EMPTY;
            String selectedName = conf.getString("SelectedSprite");
            DialogCanvas.Skin selectedSprite;
            if (selectedName == null) {
                selectedSprite = null;
            } else if (resizable && TemplateSkins.resizable(theme, selectedName)) {
                // The selected look takes the button's width, configured or default.
                selectedSprite = TemplateSkins.get(theme, selectedName, sprite.width());
            } else {
                selectedSprite = TemplateSkins.get(theme, selectedName);
            }
            Kt.require(
                    selectedSprite == null
                            || sprite != null
                                    && selectedSprite.width() == sprite.width()
                                    && selectedSprite.rows() == sprite.rows(),
                    () -> location + ": 选中贴图尺寸必须相同");
            Object rawCases = conf.get("Cases");
            List<SpriteCase> cases;
            if (rawCases != null) {
                Kt.require(type.equals("sprite"), () -> location + ".Cases: 仅用于 sprite");
                Kt.require(
                        !conf.contains("SelectedSprite") && !conf.contains("SelectedWhen"),
                        () -> location + ": Cases 不能与 SelectedSprite / SelectedWhen 同时使用");
                Kt.require(
                        rawCases instanceof List<?> list
                                && list.size() >= 1
                                && list.size() <= 16
                                && list.stream().allMatch(it -> it instanceof Map<?, ?>),
                        () -> location + ".Cases: 需要 1–16 条 - When: 条件 列表");
                List<?> raw = (List<?>) rawCases;
                cases = new ArrayList<>();
                for (int index = 0; index < raw.size(); index++) {
                    String at = location + ".Cases[" + (index + 1) + "]";
                    Map<?, ?> entry = (Map<?, ?>) raw.get(index);
                    Set<String> fields = new LinkedHashSet<>();
                    for (Object field : entry.keySet()) {
                        fields.add(String.valueOf(field));
                    }
                    String look;
                    if (image != null) {
                        look = "Image";
                    } else if (customFont != null) {
                        look = "Glyph";
                    } else {
                        look = "Sprite";
                    }
                    Set<String> allowed = new LinkedHashSet<>(Kt.setOf("When", look));
                    if (customFont != null) {
                        allowed.addAll(Kt.setOf("Font", "Advance"));
                    }
                    Kt.require(
                            fields.stream().allMatch(allowed::contains) && fields.contains(look),
                            () ->
                                    at
                                            + ": 默认写法为 "
                                            + look
                                            + " 时，每条需要 When 和 "
                                            + look
                                            + "，可用字段 "
                                            + allowed);
                    MenuCondition condition =
                            Kt.requireNotNull(
                                    condition(
                                            entry.get("When"),
                                            at + ".When",
                                            variables,
                                            placeholders),
                                    () -> at + ".When: 缺少条件");
                    if (image != null) {
                        cases.add(
                                new SpriteCase(
                                        condition,
                                        null,
                                        parseImage(entry.get("Image"), at + ".Image")));
                    } else if (sprite != null && customFont != null) {
                        Object configuredFont = entry.get("Font");
                        Object font = configuredFont != null ? configuredFont : customFont;
                        Kt.require(font instanceof String, () -> at + ".Font: 需要字体 ID");
                        Object configuredAdvance = entry.get("Advance");
                        Object advance =
                                configuredAdvance != null
                                        ? configuredAdvance
                                        : Kt.single(sprite.advances());
                        Kt.require(
                                advance instanceof Integer number && 0 <= number && number <= 1024,
                                () -> at + ".Advance: 需要 0..1024 范围内整数");
                        cases.add(
                                new SpriteCase(
                                        condition,
                                        sprite.withLook(
                                                glyph(entry.get("Glyph"), at + ".Glyph"),
                                                ResourceFont.resourceFont((String) font),
                                                Kt.listOf((Integer) advance)),
                                        null));
                    } else {
                        Object skinName = entry.get("Sprite");
                        Kt.require(skinName instanceof String, () -> at + ".Sprite: 需要贴图 ID");
                        DialogCanvas.Skin skin = TemplateSkins.get(theme, (String) skinName);
                        Kt.require(
                                sprite != null
                                        && skin.width() == sprite.width()
                                        && skin.rows() == sprite.rows(),
                                () -> at + ".Sprite: 贴图尺寸必须与默认 Sprite 相同");
                        cases.add(new SpriteCase(condition, skin, null));
                    }
                }
            } else {
                cases = Collections.emptyList();
            }
            String color = Objects.requireNonNull(conf.getString("Color", "#e7deed"));
            Kt.require(colorPattern.matcher(color).matches(), () -> location + ".Color: #RRGGBB");
            elements.add(
                    new TemplateElement(
                            name,
                            type,
                            x,
                            row,
                            elementWidth,
                            elementRows,
                            lines,
                            sprite,
                            selectedSprite,
                            Integer.parseInt(Kt.drop(color, 1), 16),
                            condition(
                                    conf.get("VisibleWhen"),
                                    location + ".VisibleWhen",
                                    variables,
                                    placeholders),
                            condition(
                                    conf.get("SelectedWhen"),
                                    location + ".SelectedWhen",
                                    variables,
                                    placeholders),
                            Objects.requireNonNull(conf.getString("Permission", "")),
                            actions,
                            textSize,
                            bold,
                            image,
                            cases));
        }
        List<TemplateElement> buttons = new ArrayList<>();
        for (TemplateElement element : elements) {
            if (element.type().equals("button")) {
                buttons.add(element);
            }
        }
        for (int index = 0; index < buttons.size(); index++) {
            TemplateElement a = buttons.get(index);
            for (TemplateElement b : buttons.subList(index + 1, buttons.size())) {
                boolean exclusive =
                        a.condition() != null
                                && b.condition() != null
                                && a.condition().excludes(b.condition());
                Kt.require(
                        exclusive
                                || a.x() >= b.x() + b.width()
                                || b.x() >= a.x() + a.width()
                                || a.row() >= b.row() + b.rows()
                                || b.row() >= a.row() + a.rows(),
                        () -> path + ": 按钮点击区域重叠 " + a.id() + "/" + b.id());
            }
        }
        return new DialogTemplate(
                id, title, width, rows, hide, background, variables, elements, placeholders);
    }

    /** The {@code condition} local function of {@code parse}; it reads the declared names. */
    private static MenuCondition condition(
            Object raw,
            String at,
            Map<String, List<String>> variables,
            Map<String, String> placeholders) {
        if (raw == null) {
            return null;
        }
        List<String> texts =
                raw instanceof String text ? Kt.listOf(line(text, at)) : strings(raw, at);
        Kt.require(texts.size() >= 1 && texts.size() <= 8, () -> at + ": 需要 1–8 条条件");
        List<MenuCondition.Clause> clauses = new ArrayList<>();
        for (String text : texts) {
            MenuCondition.Clause clause =
                    Kt.requireNotNull(
                            MenuCondition.clause(text),
                            () -> at + ": 条件写作 名称=值、名称!=值 或 名称>=数字：" + text);
            Kt.require(!clause.value().startsWith("="), () -> at + ": 比较只用一个 =：" + text);
            List<String> options = variables.get(clause.name());
            if (options != null) {
                Kt.require(
                        Kt.setOf("=", "!=").contains(clause.operator())
                                && options.contains(clause.value()),
                        () -> at + ": 菜单变量只能用 = 或 !=，值须在 Variables 列表中：" + text);
            } else if (placeholders.containsKey(clause.name())) {
                Double number = Kt.toDoubleOrNull(clause.value());
                Kt.require(
                        Kt.setOf("=", "!=").contains(clause.operator())
                                || number != null && Double.isFinite(number),
                        () -> at + ": > >= < <= 右侧需要数字：" + text);
            } else {
                throw new IllegalArgumentException(
                        at + ": 未在 Variables 或 Placeholders 中声明 " + clause.name());
            }
            clauses.add(clause);
        }
        return new MenuCondition(clauses);
    }

    /** A reaction condition on a declared name follows the same rules as VisibleWhen. */
    private static String nameProblem(
            MenuCondition.Clause clause,
            Map<String, List<String>> variables,
            Map<String, String> placeholders) {
        List<String> options = variables.get(clause.name());
        if (options != null) {
            return Kt.setOf("=", "!=").contains(clause.operator())
                            && options.contains(clause.value())
                    ? null
                    : "菜单变量只能用 = 或 !=，值须在 Variables 列表中";
        }
        if (placeholders.containsKey(clause.name())) {
            Double number = Kt.toDoubleOrNull(clause.value());
            return Kt.setOf("=", "!=").contains(clause.operator())
                            || number != null && Double.isFinite(number)
                    ? null
                    : "> >= < <= 右侧需要数字";
        }
        return "未在 Variables 或 Placeholders 中声明 " + clause.name();
    }

    private static int glyph(Object raw, String path) {
        Kt.require(
                raw instanceof String text
                        && text.length() == 1
                        && !Character.isSurrogate(text.charAt(0)),
                () -> path + ": 需要单个 BMP 字符，可写 Unicode 转义");
        return ((String) raw).charAt(0);
    }

    private static MenuImageRequest parseImage(Object raw, String path) {
        Kt.require(raw instanceof String, () -> path + ": 需要图片 ID，如 \"CE:命名空间:图片\"");
        try {
            return RichMenuText.imageRequest(Kt.split((String) raw, ':'));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(
                    path + ": 格式为 [CE/IA:]命名空间:图片[:行:列]，" + error.getMessage(), error);
        }
    }

    public static void validateLinks(Map<String, DialogTemplate> templates) {
        for (DialogTemplate template : templates.values()) {
            for (TemplateElement element : template.elements()) {
                for (String action : element.actions()) {
                    if (action.startsWith("template:")) {
                        Kt.require(
                                templates.containsKey(Kt.trim(Kt.substringAfter(action, ':'))),
                                () -> "templates/" + template.id() + ".yml: 目标模板不存在 " + action);
                    }
                }
            }
        }
    }

    private static List<String> strings(Object raw, String path) {
        Kt.require(
                raw instanceof List<?> list && list.stream().allMatch(it -> it instanceof String),
                () -> path + ": 需要字符串列表（on/off 请加引号）");
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item instanceof String text) {
                result.add(text);
            }
        }
        for (String text : result) {
            line(text, path);
        }
        return result;
    }

    private static String line(String text, String path) {
        Kt.require(text.length() <= 2048 && Kt.noControl(text), () -> path + ": 需要不含控制字符的单行文字");
        return text;
    }

    private static void keys(ConfigurationSection conf, Set<String> allowed, String path) {
        Kt.require(
                Kt.minus(conf.getKeys(false), allowed).isEmpty(),
                () -> path + ": 未知字段 " + Kt.minus(conf.getKeys(false), allowed));
    }

    private static int integer(
            ConfigurationSection conf,
            String key,
            int defaultValue,
            int min,
            int max,
            String path) {
        Object configured = conf != null ? conf.get(key) : null;
        Object raw = configured != null ? configured : defaultValue;
        Kt.require(
                raw instanceof Integer number && min <= number && number <= max,
                () -> path + "." + key + ": 需要 " + Kt.range(min, max) + " 范围内整数");
        return (Integer) raw;
    }

    /** Kotlin {@code all { it.matches(pattern) }}. */
    private static boolean allMatch(List<String> values, Pattern pattern) {
        for (String value : values) {
            if (!pattern.matcher(value).matches()) {
                return false;
            }
        }
        return true;
    }
}
