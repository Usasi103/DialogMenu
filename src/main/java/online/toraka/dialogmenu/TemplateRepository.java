package online.toraka.dialogmenu;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TemplateRepository {

    private static final List<String> defaults =
            Kt.listOf("npc-dialogue", "boss-intro", "boss-confirm");

    private final File directory;
    private Map<String, DialogTemplate> current = Collections.emptyMap();

    public TemplateRepository(File directory) {
        this.directory = directory;
    }

    public static List<String> defaults() {
        return defaults;
    }

    public Map<String, DialogTemplate> current() {
        return current;
    }

    public void initialize() {
        initialize(BrokenFiles.Result.none());
    }

    /** {@link #initialize()}, leaving out the templates {@code broken} could not read. */
    public void initialize(BrokenFiles.Result broken) {
        File folder = new File(directory, "templates");
        if (!folder.exists()) {
            folder.mkdirs();
            for (String id : defaults) {
                Kt.writeText(
                        new File(folder, id + ".yml"),
                        MenuRepository.resource("templates/" + id + ".yml"));
            }
        }
        install(read(broken));
    }

    public Map<String, DialogTemplate> read() {
        return read(BrokenFiles.Result.none());
    }

    /** {@link #read()}, leaving out the templates {@code broken} could not read. */
    public Map<String, DialogTemplate> read(BrokenFiles.Result broken) {
        File folder = new File(directory, "templates");
        Kt.require(folder.isDirectory(), () -> "templates: 目录不存在");
        FileFilter filter = f -> f.isFile() && Kt.extension(f).equals("yml");
        File[] listed = Objects.requireNonNull(folder.listFiles(filter));
        List<File> files = new ArrayList<>(Arrays.asList(listed));
        files.removeIf(broken::skips);
        files.sort(Comparator.comparing(File::getName));
        Kt.require(files.size() <= 64, () -> "templates: 最多 64 个模板");
        Map<String, DialogTemplate> templates = new LinkedHashMap<>();
        for (File file : files) {
            Kt.require(file.length() <= 1_048_576, () -> file.getName() + ": 超过 1 MiB");
            templates.put(
                    Kt.nameWithoutExtension(file),
                    TemplateParser.parse(Kt.nameWithoutExtension(file), Kt.readText(file)));
        }
        TemplateParser.validateLinks(templates);
        return templates;
    }

    public void install(Map<String, DialogTemplate> next) {
        current = next;
    }
}
