package online.toraka.dialogmenu;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Compile a small editable demo catalog to ordinary canvas pages and session variables. */
public final class QuestDemoCompiler {

    private static final Pattern identifier = Pattern.compile("[a-z][a-z0-9_-]{0,19}");
    private static final Properties icons = loadIcons();

    private QuestDemoCompiler() {}

    private static Properties loadIcons() {
        Properties properties = new Properties();
        try (InputStream input =
                Kt.requireNotNull(
                        QuestDemoCompiler.class.getResourceAsStream("/quest-icons.properties"))) {
            properties.load(input);
        } catch (IOException error) {
            throw Kt.sneaky(error);
        }
        return properties;
    }

    private record Position(int x, int row) {}

    private record Icon(String font, String glyph, int width, int advance) {}

    private record Reward(String text, Icon icon) {}

    private record Task(
            String id,
            String name,
            String category,
            String description,
            String objective,
            int current,
            int total,
            boolean claimed,
            Icon icon,
            List<Reward> rewards) {

        boolean complete() {
            return current == total;
        }

        String variable() {
            return "claim-" + id;
        }
    }

    public static YamlConfiguration compile(ConfigurationSection root, String path) {
        MenuType.require(root, MenuType.DIALOG, path);
        keys(
                root,
                Kt.setOf(
                        "MenuType",
                        "Version",
                        "Type",
                        "Title",
                        "Subtitle",
                        "Skin",
                        "HideFocusOutline",
                        "PageSize",
                        "Categories",
                        "Layout",
                        "Tasks"),
                path);
        Kt.require(Objects.equals(root.get("Version"), 1), () -> path + ".Version: 必须为 1");
        Object configuredPageSize = root.get("PageSize");
        int pageSize =
                integer(
                        configuredPageSize != null ? configuredPageSize : 5,
                        1,
                        5,
                        path + ".PageSize");
        ConfigurationSection categories = section(root, "Categories", path);
        Kt.require(
                categories.getKeys(false).size() >= 1 && categories.getKeys(false).size() <= 3,
                () -> path + ".Categories: 需要 1–3 个分类");
        Map<String, String> names = new LinkedHashMap<>();
        names.put("all", "全部");
        for (String id : categories.getKeys(false)) {
            Kt.require(
                    identifier.matcher(id).matches() && !Kt.setOf("all", "completed").contains(id),
                    () -> path + ".Categories: 无效分类 ID " + id);
            names.put(id, text(categories.get(id), path + ".Categories." + id, 4));
        }
        names.put("completed", "已完成");
        ConfigurationSection taskSection = section(root, "Tasks", path);
        Kt.require(
                taskSection.getKeys(false).size() >= 1 && taskSection.getKeys(false).size() <= 15,
                () -> path + ".Tasks: 需要 1–15 个演示任务");
        List<Task> tasks = new ArrayList<>();
        for (String id : taskSection.getKeys(false)) {
            Kt.require(
                    identifier.matcher(id).matches() && !id.equals("none"),
                    () -> path + ".Tasks: 无效任务 ID " + id);
            String at = path + ".Tasks." + id;
            ConfigurationSection value = section(taskSection, id, path + ".Tasks");
            keys(
                    value,
                    Kt.setOf(
                            "Name",
                            "Category",
                            "Description",
                            "Objective",
                            "Progress",
                            "Claimed",
                            "Icon",
                            "Rewards"),
                    at);
            String category = value.getString("Category");
            Kt.require(
                    category != null && categories.getKeys(false).contains(category),
                    () -> at + ".Category: 分类不存在");
            List<?> progress = value.getList("Progress");
            Kt.require(
                    progress != null && progress.size() == 2, () -> at + ".Progress: [当前进度, 目标数量]");
            int total = integer(progress.get(1), 1, 1_000_000, at + ".Progress[1]");
            int current = integer(progress.get(0), 0, total, at + ".Progress[0]");
            Kt.require(
                    !value.contains("Claimed") || value.isBoolean("Claimed"),
                    () -> at + ".Claimed: 需要 true/false");
            boolean claimed = value.getBoolean("Claimed", false);
            Kt.require(!claimed || current == total, () -> at + ".Claimed: 未完成目标不能标记已领取");
            List<?> rewardList = value.getList("Rewards");
            Kt.require(
                    rewardList != null && rewardList.size() >= 1 && rewardList.size() <= 3,
                    () -> at + ".Rewards: 需要 1–3 项奖励");
            List<Reward> rewards = new ArrayList<>();
            for (int index = 0; index < rewardList.size(); index++) {
                int rewardIndex = index;
                Map<?, ?> reward = mapping(rewardList.get(index), at + ".Rewards[" + index + "]");
                Kt.require(
                        reward.keySet().equals(Kt.setOf("Text", "Icon")),
                        () -> at + ".Rewards[" + rewardIndex + "]: 使用 Text 和 Icon");
                rewards.add(
                        new Reward(
                                text(reward.get("Text"), at + ".Rewards[" + index + "].Text", 30),
                                icon(
                                        reward.get("Icon"),
                                        at + ".Rewards[" + index + "].Icon",
                                        true)));
            }
            tasks.add(
                    new Task(
                            id,
                            text(value.get("Name"), at + ".Name", 20),
                            category,
                            text(value.get("Description"), at + ".Description", 256),
                            text(value.get("Objective"), at + ".Objective", 80),
                            current,
                            total,
                            claimed,
                            icon(value.get("Icon"), at + ".Icon", false),
                            rewards));
        }
        ConfigurationSection layout = root.getConfigurationSection("Layout");
        Kt.require(!root.contains("Layout") || layout != null, () -> path + ".Layout: 需要配置段");
        if (layout != null) {
            keys(layout, Kt.setOf("List", "Categories", "Detail", "Pagination"), path + ".Layout");
        }
        Position list = position(layout, path, "List", 16, 6);
        Position tabs = position(layout, path, "Categories", 16, 3);
        Position detail = position(layout, path, "Detail", 222, 6);
        Position pagination = position(layout, path, "Pagination", 16, 17);
        YamlConfiguration result = new YamlConfiguration();
        result.set("MenuType", MenuType.DIALOG.id());
        result.set("Version", 1);
        result.set("Type", "canvas");
        Object configuredTitle = root.get("Title");
        result.set(
                "Title",
                text(configuredTitle != null ? configuredTitle : "任务列表", path + ".Title", 40));
        Object configuredSkin = root.get("Skin");
        result.set("Skin", configuredSkin != null ? configuredSkin : "stone");
        Object configuredHide = root.get("HideFocusOutline");
        Map<String, Object> canvas = new LinkedHashMap<>();
        canvas.put("Width", 552);
        canvas.put("Rows", 20);
        canvas.put("Background", "panel");
        canvas.put("HideFocusOutline", configuredHide != null ? configuredHide : true);
        result.set("Canvas", canvas);
        List<String> tracked = new ArrayList<>();
        tracked.add("none");
        for (Task task : tasks) {
            tracked.add(task.id());
        }
        result.set("Variables.tracked", tracked);
        for (Task task : tasks) {
            result.set(
                    "Variables." + task.variable(),
                    task.claimed() ? Kt.listOf("claimed", "ready") : Kt.listOf("ready", "claimed"));
        }
        result.set("DefaultPage", page("all", Kt.first(tasks)));
        for (String category : names.keySet()) {
            List<Task> filtered = new ArrayList<>();
            for (Task task : tasks) {
                if (inCategory(task, category)) {
                    filtered.add(task);
                }
            }
            List<Task> selections = filtered.isEmpty() ? Collections.singletonList(null) : filtered;
            for (int index = 0; index < selections.size(); index++) {
                Task chosen = selections.get(index);
                int pageIndex = index / pageSize;
                List<Task> visible = drop(filtered, pageIndex * pageSize);
                visible = visible.subList(0, Math.min(pageSize, visible.size()));
                ConfigurationSection elements =
                        result.createSection("Pages." + page(category, chosen) + ".Elements");
                label(
                        elements,
                        "heading",
                        16,
                        1,
                        300,
                        Objects.requireNonNull(result.getString("Title")),
                        "#eeeeee",
                        1,
                        null);
                Object configuredSubtitle = root.get("Subtitle");
                label(
                        elements,
                        "subtitle",
                        318,
                        3,
                        210,
                        text(
                                configuredSubtitle != null ? configuredSubtitle : "选择任务，查看目标与奖励",
                                path + ".Subtitle",
                                80),
                        "#b8b8b8",
                        1,
                        null);
                button(elements, "close", 522, 1, "close", "X", Kt.listOf("close"), null, null);
                elements.set("close.Bold", true);
                int tabIndex = 0;
                for (Map.Entry<String, String> entry : names.entrySet()) {
                    Task first = null;
                    for (Task task : tasks) {
                        if (inCategory(task, entry.getKey())) {
                            first = task;
                            break;
                        }
                    }
                    button(
                            elements,
                            "category-" + entry.getKey(),
                            tabs.x() + tabIndex * 60,
                            tabs.row(),
                            entry.getKey().equals(category) ? "quest-tab-selected" : "quest-tab",
                            entry.getValue(),
                            Kt.listOf("page: " + page(entry.getKey(), first)),
                            null,
                            null);
                    tabIndex++;
                }
                sprite(elements, "divider", detail.x() - 18, list.row(), "quest-divider");
                for (int slot = 0; slot < visible.size(); slot++) {
                    Task task = visible.get(slot);
                    int row = list.row() + slot * 2;
                    String style =
                            chosen != null && task.id().equals(chosen.id())
                                    ? "quest-row-selected"
                                    : "quest-row";
                    List<String> action = Kt.listOf("page: " + page(category, task));
                    if (task.complete()) {
                        button(
                                elements,
                                "task-" + task.id(),
                                list.x(),
                                row,
                                style,
                                task.name() + " · 可领取",
                                action,
                                task.variable() + "=ready",
                                null);
                        button(
                                elements,
                                "done-" + task.id(),
                                list.x(),
                                row,
                                style,
                                task.name() + " · 已完成",
                                action,
                                task.variable() + "=claimed",
                                null);
                    } else {
                        button(
                                elements,
                                "task-" + task.id(),
                                list.x(),
                                row,
                                style,
                                task.name() + " · " + task.current() + "/" + task.total(),
                                action,
                                null,
                                null);
                    }
                    picture(elements, "icon-" + task.id(), list.x() + 6, row, task.icon());
                }
                int pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
                label(
                        elements,
                        "page-number",
                        pagination.x() + 72,
                        pagination.row() + 1,
                        45,
                        (pageIndex + 1) + " / " + pages,
                        "#c6c6c6",
                        1,
                        null);
                if (pageIndex > 0) {
                    button(
                            elements,
                            "previous",
                            pagination.x(),
                            pagination.row(),
                            "quest-tab",
                            "上一页",
                            Kt.listOf(
                                    "page: "
                                            + page(
                                                    category,
                                                    filtered.get((pageIndex - 1) * pageSize))),
                            null,
                            null);
                }
                if (pageIndex + 1 < pages) {
                    button(
                            elements,
                            "next",
                            pagination.x() + 126,
                            pagination.row(),
                            "quest-tab",
                            "下一页",
                            Kt.listOf(
                                    "page: "
                                            + page(
                                                    category,
                                                    filtered.get((pageIndex + 1) * pageSize))),
                            null,
                            null);
                }
                if (chosen == null) {
                    label(
                            elements,
                            "empty-list",
                            list.x() + 24,
                            list.row() + 4,
                            145,
                            "此分类暂无任务",
                            "#b8b8b8",
                            1,
                            null);
                    label(
                            elements,
                            "empty-detail",
                            detail.x(),
                            detail.row() + 3,
                            300,
                            "请切换到其他任务分类。",
                            "#b8b8b8",
                            1,
                            null);
                    continue;
                }
                label(
                        elements,
                        "task-title",
                        detail.x(),
                        detail.row(),
                        220,
                        chosen.name(),
                        "#eeeeee",
                        1,
                        null);
                label(
                        elements,
                        "description",
                        detail.x(),
                        detail.row() + 2,
                        312,
                        chosen.description(),
                        "#b8b8b8",
                        2,
                        null);
                label(
                        elements,
                        "objective",
                        detail.x(),
                        detail.row() + 4,
                        232,
                        chosen.objective(),
                        "#eeeeee",
                        1,
                        null);
                label(
                        elements,
                        "progress-value",
                        detail.x() + 237,
                        detail.row() + 4,
                        75,
                        chosen.current() + "/" + chosen.total(),
                        "#eeeeee",
                        1,
                        null);
                sprite(
                        elements,
                        "progress-bar",
                        detail.x(),
                        detail.row() + 5,
                        "quest-progress-" + (long) chosen.current() * 20 / chosen.total());
                label(
                        elements,
                        "rewards-heading",
                        detail.x(),
                        detail.row() + 7,
                        160,
                        "任务奖励",
                        "#c6c6c6",
                        1,
                        null);
                for (int rewardIndex = 0; rewardIndex < chosen.rewards().size(); rewardIndex++) {
                    Reward reward = chosen.rewards().get(rewardIndex);
                    picture(
                            elements,
                            "reward-" + rewardIndex,
                            detail.x() + rewardIndex * 104,
                            detail.row() + 8,
                            reward.icon());
                    label(
                            elements,
                            "reward-label-" + rewardIndex,
                            detail.x() + rewardIndex * 104 + reward.icon().width() + 5,
                            detail.row() + 9,
                            98 - reward.icon().width(),
                            reward.text(),
                            "#eeeeee",
                            1,
                            null);
                }
                if (chosen.complete()) {
                    label(
                            elements,
                            "status-ready",
                            detail.x() + 249,
                            detail.row(),
                            63,
                            "可领取",
                            "#dcc784",
                            1,
                            chosen.variable() + "=ready");
                    label(
                            elements,
                            "status-claimed",
                            detail.x() + 249,
                            detail.row(),
                            63,
                            "已完成",
                            "#91ac99",
                            1,
                            chosen.variable() + "=claimed");
                    button(
                            elements,
                            "claim",
                            detail.x() + 204,
                            detail.row() + 11,
                            "button",
                            "领取奖励",
                            Kt.listOf(
                                    "set: " + chosen.variable() + "=claimed",
                                    "message: 演示：已领取 " + chosen.name() + " 的奖励（不会发放真实物品）。",
                                    "refresh"),
                            chosen.variable() + "=ready",
                            null);
                    label(
                            elements,
                            "claimed",
                            detail.x() + 218,
                            detail.row() + 12,
                            94,
                            "已领取奖励",
                            "#91ac99",
                            1,
                            chosen.variable() + "=claimed");
                } else {
                    label(
                            elements,
                            "status",
                            detail.x() + 249,
                            detail.row(),
                            63,
                            "进行中",
                            "#c6c6c6",
                            1,
                            null);
                    button(
                            elements,
                            "track",
                            detail.x() + 204,
                            detail.row() + 11,
                            "button",
                            "追踪任务",
                            Kt.listOf("set: tracked=" + chosen.id(), "refresh"),
                            null,
                            "tracked=" + chosen.id());
                    button(
                            elements,
                            "untrack",
                            detail.x() + 90,
                            detail.row() + 11,
                            "button",
                            "取消追踪",
                            Kt.listOf("set: tracked=none", "refresh"),
                            "tracked=" + chosen.id(),
                            null);
                }
            }
        }
        return result;
    }

    /** The {@code position} local function of {@code compile}; it reads the Layout section. */
    private static Position position(
            ConfigurationSection layout, String path, String name, int x, int row) {
        Object raw = layout != null ? layout.get(name) : null;
        if (raw == null) {
            return new Position(x, row);
        }
        Kt.require(
                raw instanceof List<?> list && list.size() == 2,
                () -> path + ".Layout." + name + ": [横向像素, 纵向行号]");
        List<?> values = (List<?>) raw;
        return new Position(
                integer(values.get(0), 0, 551, path + ".Layout." + name + "[0]"),
                integer(values.get(1), 0, 19, path + ".Layout." + name + "[1]"));
    }

    private static String page(String category, Task task) {
        return category + "-" + (task != null ? task.id() : "empty");
    }

    private static boolean inCategory(Task task, String category) {
        return switch (category) {
            case "all" -> true;
            case "completed" -> task.complete();
            default -> task.category().equals(category) && !task.complete();
        };
    }

    /** Kotlin {@code List.drop(n)}. */
    private static List<Task> drop(List<Task> values, int count) {
        return new ArrayList<>(values.subList(Math.min(count, values.size()), values.size()));
    }

    private static void element(
            ConfigurationSection elements,
            String id,
            String type,
            int x,
            int row,
            Map<String, Object> fields) {
        ConfigurationSection value = elements.createSection(id);
        value.set("Type", type);
        value.set("Position", Kt.listOf(x, row));
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            value.set(field.getKey(), field.getValue());
        }
    }

    private static void label(
            ConfigurationSection elements,
            String id,
            int x,
            int row,
            int width,
            String value,
            String color,
            int rows,
            String condition) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Width", width);
        fields.put("Rows", rows);
        fields.put("Text", value);
        fields.put("Color", color);
        if (condition != null) {
            fields.put("VisibleWhen", condition);
        }
        element(elements, id, "text", x, row, fields);
    }

    private static void button(
            ConfigurationSection elements,
            String id,
            int x,
            int row,
            String sprite,
            String value,
            List<String> actions,
            String condition,
            String selected) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Sprite", sprite);
        fields.put("Text", value);
        fields.put("Actions", actions);
        if (condition != null) {
            fields.put("VisibleWhen", condition);
        }
        if (selected != null) {
            fields.put("SelectedWhen", selected);
            fields.put("SelectedSprite", "selected");
        }
        element(elements, id, "button", x, row, fields);
    }

    private static void sprite(
            ConfigurationSection elements, String id, int x, int row, String name) {
        element(elements, id, "sprite", x, row, Kt.mapOf("Sprite", name));
    }

    private static void picture(
            ConfigurationSection elements, String id, int x, int row, Icon icon) {
        element(
                elements,
                id,
                "sprite",
                x,
                row,
                Kt.mapOf(
                        "Font",
                        icon.font(),
                        "Glyph",
                        icon.glyph(),
                        "Width",
                        icon.width(),
                        "Rows",
                        2,
                        "Advance",
                        icon.advance()));
    }

    private static Icon icon(Object raw, String path, boolean reward) {
        if (raw instanceof String name) {
            String metric =
                    Kt.requireNotNull(icons.getProperty(name), () -> path + ": 未知图标 " + name);
            List<Integer> data = new ArrayList<>();
            for (String part : Kt.split(metric, ',')) {
                data.add(Integer.parseInt(part));
            }
            return new Icon(
                    reward
                            ? "dialogmenu_dialogue:quest_rewards"
                            : "dialogmenu_dialogue:quest_items",
                    String.valueOf((char) data.get(0).intValue()),
                    12,
                    data.get(1));
        }
        Map<?, ?> value =
                mapping(
                        raw instanceof ConfigurationSection section
                                ? section.getValues(false)
                                : raw,
                        path);
        Set<String> allowed = Kt.setOf("Font", "Glyph", "Width", "Advance");
        Kt.require(
                value.keySet().stream().allMatch(allowed::contains),
                () -> path + ": 图标使用 Font/Glyph/Width/Advance");
        String font = text(value.get("Font"), path + ".Font", 120);
        Key.key(font);
        String glyph = text(value.get("Glyph"), path + ".Glyph", 1);
        Kt.require(!Character.isSurrogate(glyph.charAt(0)), () -> path + ".Glyph: 需要单个 BMP 字符");
        Object configuredWidth = value.get("Width");
        return new Icon(
                font,
                glyph,
                integer(configuredWidth != null ? configuredWidth : 12, 1, 18, path + ".Width"),
                integer(value.get("Advance"), 0, 1024, path + ".Advance"));
    }

    private static void keys(ConfigurationSection value, Set<String> allowed, String path) {
        Kt.require(
                value.getKeys(false).stream().allMatch(allowed::contains),
                () -> path + ": 未知字段 " + Kt.minus(value.getKeys(false), allowed));
    }

    private static ConfigurationSection section(
            ConfigurationSection value, String name, String path) {
        return Kt.requireNotNull(
                value.getConfigurationSection(name), () -> path + "." + name + ": 需要配置段");
    }

    private static String text(Object raw, String path, int max) {
        Kt.require(
                raw instanceof String text
                        && Kt.isNotBlank(text)
                        && text.length() <= max
                        && Kt.noControl(text),
                () -> path + ": 需要 1–" + max + " 字符的单行文字");
        return (String) raw;
    }

    private static int integer(Object raw, int min, int max, String path) {
        Kt.require(
                raw instanceof Integer number && min <= number && number <= max,
                () -> path + ": 需要 " + Kt.range(min, max) + " 范围内整数");
        return (Integer) raw;
    }

    private static Map<?, ?> mapping(Object raw, String path) {
        Kt.require(raw instanceof Map<?, ?>, () -> path + ": 需要配置段");
        return (Map<?, ?>) raw;
    }
}
