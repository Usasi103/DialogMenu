package online.toraka.dialogmenu;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** One rendering backend per menu file; pages and controls inherit it. */
public enum MenuType {
    DIALOG("dialog"),
    FULLSCREEN("fullscreen");

    private final String id;

    MenuType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Strict YAML and rendering-type validation used by the menu parsers. */
    public static YamlConfiguration parse(String source, MenuType expected, String path) {
        YamlConfiguration root = MenuConfigParser.yaml(source, path);
        require(root, expected, path);
        return root;
    }

    public static void require(ConfigurationSection root, MenuType expected, String path) {
        MenuType actual = read(root, path);
        if (actual != expected) {
            throw new IllegalArgumentException(
                    path
                            + ".MenuType: 此加载入口只支持 "
                            + expected.id
                            + "，不能混用 "
                            + actual.id
                            + " 的布局或按钮；请使用对应菜单入口");
        }
    }

    public static MenuType read(ConfigurationSection root, String path) {
        Object value = root.get("MenuType");
        if (!(value instanceof String name)
                || !(name.equals(DIALOG.id) || name.equals(FULLSCREEN.id))) {
            throw new IllegalArgumentException(
                    path + ".MenuType: 文件开头必须声明一个菜单类型：dialog 或 fullscreen（不能使用列表）");
        }
        if (!root.getKeys(false).iterator().next().equals("MenuType")) {
            throw new IllegalArgumentException(path + ".MenuType: 必须放在文件开头，作为第一个配置项");
        }
        return value.equals(DIALOG.id) ? DIALOG : FULLSCREEN;
    }
}
