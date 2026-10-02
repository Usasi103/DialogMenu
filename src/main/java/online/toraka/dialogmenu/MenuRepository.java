package online.toraka.dialogmenu;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MenuRepository {
    private final File directory;
    private MenuDefinition current;

    public MenuRepository(File directory) {
        this.directory = directory;
        this.current = bundled();
    }

    public MenuDefinition current() {
        return current;
    }

    public void initialize() {
        boolean simple = new File(directory, "config.yml").exists();
        boolean legacy = new File(directory, "menu.yml").exists();
        if (!simple && !legacy) {
            List<String> existing = new ArrayList<>();
            existing.add("config.yml");
            for (String page : SimpleMenuParser.defaultPages()) {
                existing.add("menus/" + page + ".yml");
            }
            for (String name : existing) {
                File file = new File(directory, name);
                Kt.require(!file.exists(), () -> name + " 已存在：请补全 config.yml，现有页面不会被覆盖");
            }
            // Export pages first: a completed config.yml selects this format on subsequent starts.
            List<String> exported = new ArrayList<>();
            for (String page : SimpleMenuParser.defaultPages()) {
                exported.add("menus/" + page + ".yml");
            }
            exported.add("config.yml");
            for (String name : exported) {
                File file = new File(directory, name);
                file.getParentFile().mkdirs();
                Kt.writeText(file, resource("simple/" + name));
            }
        }
        List<String> extras =
                legacy && !simple
                        ? Kt.listOf("languages/zh_cn.yml", "languages/en_us.yml", "配置说明.md")
                        : Kt.listOf("配置说明.md", "examples/items.yml");
        for (String name : extras) {
            File file = new File(directory, name);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                Kt.writeText(file, resource(name));
            }
        }
        reload();
    }

    public void reload() {
        current = readDefinition();
    }

    public void install(MenuDefinition definition) {
        current = definition;
    }

    public MenuDefinition readDefinition() {
        if (new File(directory, "config.yml").exists()) {
            return SimpleMenuParser.parse(read("config.yml"), id -> read("menus/" + id + ".yml"));
        }
        String menu = read("menu.yml");
        Map<MenuLanguage, String> languages = new LinkedHashMap<>();
        for (MenuLanguage language : MenuLanguage.values()) {
            languages.put(language, read("languages/" + language.id() + ".yml"));
        }
        return MenuConfigParser.parse(menu, languages);
    }

    private String read(String name) {
        File file = new File(directory, name);
        Kt.require(file.isFile(), () -> name + ": 文件不存在");
        Kt.require(file.length() <= 1_048_576, () -> name + ": 文件超过 1 MiB");
        return Kt.readText(file);
    }

    static String resource(String name) {
        InputStream stream =
                Kt.requireNotNull(
                        MenuRepository.class.getResourceAsStream("/" + name),
                        () -> "缺少默认配置 " + name);
        try (InputStream input = stream) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw Kt.sneaky(error);
        }
    }

    public static MenuDefinition bundled() {
        Map<MenuLanguage, String> languages = new LinkedHashMap<>();
        String menu = resource("menu.yml");
        for (MenuLanguage language : MenuLanguage.values()) {
            languages.put(language, resource("languages/" + language.id() + ".yml"));
        }
        return MenuConfigParser.parse(menu, languages);
    }
}
