package online.toraka.dialogmenu;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CatalogRepository {

    private static final List<String> defaults =
            Kt.listOf("demo-settings", "demo-dialogue", "demo-boss", "demo-quests");

    private final File directory;
    private MenuCatalog current = null;

    public CatalogRepository(File directory) {
        this.directory = directory;
    }

    public static List<String> defaults() {
        return defaults;
    }

    public MenuCatalog current() {
        return current;
    }

    public MenuCatalog read() {
        return read(BrokenFiles.Result.none());
    }

    /** {@link #read()}, leaving out the menu files {@code broken} could not read. */
    public MenuCatalog read(BrokenFiles.Result broken) {
        File folder = new File(directory, "menus");
        Kt.require(folder.isDirectory(), () -> "menus: 目录不存在");
        FileFilter filter = file -> file.isFile() && Kt.extension(file).equals("yml");
        File[] files = Kt.requireNotNull(folder.listFiles(filter));
        Kt.require(files.length <= 64, () -> "menus: 最多 64 个菜单文件");
        String config = read(new File(directory, "config.yml"));
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        sorted.sort(Comparator.comparing(File::getName));
        Map<String, String> sources = new LinkedHashMap<>();
        for (File file : sorted) {
            if (broken.skips(file)) {
                continue;
            }
            sources.put(Kt.nameWithoutExtension(file), read(file));
        }
        return MenuCatalogParser.parse(config, sources);
    }

    /** The {@code read(file)} local function of {@code read()}. */
    private static String read(File file) {
        Kt.require(
                file.isFile() && file.length() <= 1_048_576,
                () -> file.getName() + ": 文件不存在或超过 1 MiB");
        return Kt.readText(file);
    }

    public void install(MenuCatalog next) {
        current = next;
    }

    public static boolean selected(File directory) {
        File file = new File(directory, "config.yml");
        return file.isFile()
                && Objects.equals(
                        MenuConfigParser.yaml(Kt.readText(file), "config.yml").get("Version"), 3);
    }

    public static void exportIfNew(File directory) {
        if (new File(directory, "config.yml").exists()
                || new File(directory, "menu.yml").exists()) {
            return;
        }
        Kt.require(
                !new File(directory, "menus").exists()
                        && !new File(directory, "templates").exists(),
                () -> "已有菜单目录但缺少 config.yml，请补全配置，现有文件不会覆盖");
        List<String> names = new ArrayList<>();
        for (String id : defaults) {
            names.add("menus/" + id + ".yml");
        }
        names.add("config.yml");
        for (String name : names) {
            File file = new File(directory, name);
            file.getParentFile().mkdirs();
            Kt.writeText(file, MenuRepository.resource("catalog/" + name));
        }
    }
}
