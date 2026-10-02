package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.bukkit.configuration.ConfigurationSection;

/** Native item bodies are independent of the font canvas; never silently flatten a CE model. */
public final class ItemMenuPage {

    /** Compiles an Actions list (raw value, path, permission, required plugin) to an action ID. */
    @FunctionalInterface
    public interface Actions {
        String apply(Object raw, String path, String permission, String plugin);
    }

    private ItemMenuPage() {}

    public static boolean usesItems(ConfigurationSection page, List<String> layout, String path) {
        Object renderer = page.get("Renderer");
        Kt.require(
                renderer == null || Kt.setOf("canvas", "items").contains(renderer),
                () -> path + ".Renderer: 使用 canvas / items");
        boolean items = false;
        for (String it : layout) {
            if (page.contains("Icons." + it + ".Display.Material")
                    || "item".equals(page.getString("Icons." + it + ".Type"))) {
                items = true;
                break;
            }
        }
        Kt.require(
                !"canvas".equals(renderer) || !items,
                () -> path + ".Renderer: Display.Material 需要 items 布局，不能放进字体画布");
        return "items".equals(renderer) || items;
    }

    public static List<ItemMenuEntry> parse(
            ConfigurationSection icons,
            List<String> layout,
            String file,
            BiFunction<Object, String, String> label,
            Actions actions) {
        List<ItemMenuEntry> entries = new ArrayList<>(layout.size());
        for (String id : layout) {
            entries.add(entry(icons, id, file, label, actions));
        }
        return entries;
    }

    private static ItemMenuEntry entry(
            ConfigurationSection icons,
            String id,
            String file,
            BiFunction<Object, String, String> label,
            Actions actions) {
        String at = file + ".Icons." + id;
        ConfigurationSection icon = icons.getConfigurationSection(id);
        if (icon == null) {
            throw Kt.error(at + ": 缺少控件定义");
        }
        keys(
                icon,
                Kt.setOf(
                        "Type",
                        "Name",
                        "Description",
                        "Display",
                        "Actions",
                        "Permission",
                        "RequiresPlugin"),
                at);
        String type = string(icon, "Type", "button", at);
        Kt.require(
                Kt.setOf("button", "item", "text", "heading").contains(type),
                () -> at + ".Type: 物品页面支持 button / item / text / heading；开关、滑条、下拉框请保留在 canvas 页面");
        ConfigurationSection display;
        if (icon.contains("Display")) {
            ConfigurationSection found = icon.getConfigurationSection("Display");
            if (found == null) {
                throw Kt.error(at + ".Display: 需要配置段");
            }
            display = found;
        } else {
            display = null;
        }
        if (display != null) {
            keys(
                    display,
                    Kt.setOf("Material", "Name", "Lore", "Amount", "Fallback"),
                    at + ".Display");
        }
        Kt.require(
                !(icon.contains("Name") && display != null && display.contains("Name")),
                () -> at + ": Name 与 Display.Name 只填写一个");
        Kt.require(
                !(icon.contains("Description") && display != null && display.contains("Lore")),
                () -> at + ": Description 与 Display.Lore 只填写一个");
        Object rawName = display != null ? display.get("Name") : null;
        if (rawName == null) {
            rawName = icon.get("Name");
        }
        if (rawName == null) {
            rawName = id;
        }
        String name = label.apply(rawName, at + ".Name");
        Object rawDescription = display != null ? display.get("Lore") : null;
        if (rawDescription == null) {
            rawDescription = icon.get("Description");
        }
        List<String> description;
        if (rawDescription == null) {
            description = Collections.emptyList();
        } else if (rawDescription instanceof List<?> values) {
            description = new ArrayList<>(values.size());
            for (int index = 0; index < values.size(); index++) {
                description.add(label.apply(values.get(index), at + ".Description[" + index + "]"));
            }
        } else {
            description = Kt.listOf(label.apply(rawDescription, at + ".Description"));
        }
        Kt.require(description.size() <= 6, () -> at + ".Description: 最多 6 行");
        ItemDisplay item;
        if (display != null && display.contains("Material")) {
            Kt.require(
                    type.equals("button") || type.equals("item"),
                    () -> at + ".Display.Material: 只用于 button / item");
            ItemReference material =
                    ItemReference.parse(
                            string(display, "Material", null, at + ".Display"),
                            at + ".Display.Material");
            ItemReference fallback;
            if (display.contains("Fallback")) {
                fallback =
                        ItemReference.parse(
                                string(display, "Fallback", null, at + ".Display"),
                                at + ".Display.Fallback");
                Kt.require(
                        fallback.provider().equals("minecraft"),
                        () -> at + ".Display.Fallback: 回退物品必须为原版物品");
            } else {
                fallback = null;
            }
            Kt.require(
                    !display.contains("Amount") || display.isInt("Amount"),
                    () -> at + ".Display.Amount: 需要整数");
            int amount = display.getInt("Amount", 1);
            Kt.require(amount >= 1 && amount <= 99, () -> at + ".Display.Amount: 使用 1–99");
            item = new ItemDisplay(material, fallback, amount, at + ".Display");
        } else {
            Kt.require(!type.equals("item"), () -> at + ".Display.Material: item 控件需要物品源");
            Kt.require(
                    !(display != null && display.contains("Amount"))
                            && !(display != null && display.contains("Fallback")),
                    () -> at + ".Display: Amount / Fallback 需要 Material");
            item = null;
        }
        String action;
        if (type.equals("button")) {
            action =
                    actions.apply(
                            icon.get("Actions"),
                            at + ".Actions",
                            string(icon, "Permission", "", at),
                            string(icon, "RequiresPlugin", "", at));
        } else {
            boolean interactive = false;
            for (String key : Kt.listOf("Actions", "Permission", "RequiresPlugin")) {
                if (icon.contains(key)) {
                    interactive = true;
                    break;
                }
            }
            Kt.require(!interactive, () -> at + ": 交互动作和权限只用于 button");
            action = "";
        }
        return new ItemMenuEntry(type, name, description, item, action);
    }

    private static void keys(ConfigurationSection section, Set<String> allowed, String path) {
        Kt.require(
                Kt.minus(section.getKeys(false), allowed).isEmpty(),
                () -> path + ": 未知字段 " + Kt.minus(section.getKeys(false), allowed));
    }

    private static String string(
            ConfigurationSection section, String key, String defaultValue, String path) {
        if (!section.contains(key) && defaultValue != null) {
            return defaultValue;
        }
        Object value = section.get(key);
        Kt.require(
                value instanceof String text && Kt.isNotBlank(text) && Kt.noControl(text),
                () -> path + "." + key + ": 需要非空字符串");
        return (String) value;
    }
}
