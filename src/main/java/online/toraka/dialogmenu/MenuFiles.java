package online.toraka.dialogmenu;

import dev.keystone.config.ConfigProblems;
import dev.keystone.config.LoadProblem;
import dev.keystone.config.ReloadTransaction;
import dev.keystone.storage.FileBackup;
import dev.keystone.storage.StorageWriter;
import dev.keystone.storage.WriteGuard;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/** Original-byte inputs. Neither collecting nor parsing this candidate writes a file or header. */
final class MenuFiles {

    private static final class Input {
        final String path;
        final File file;
        final String resource;
        byte[] original;
        String text;
        LoadProblem problem;
        boolean replacement;
        boolean tabs;

        Input(File directory, String path, String resource) {
            this.path = path;
            this.file = new File(directory, path);
            this.resource = resource;
        }
    }

    private final File directory;
    private final Map<String, Input> inputs = new LinkedHashMap<>();

    boolean guardedLanguages;

    private MenuFiles(File directory) {
        this.directory = directory;
    }

    static MenuFiles collect(File directory, boolean startup) {
        MenuFiles result = new MenuFiles(directory);
        Map<String, String> names = new LinkedHashMap<>(BrokenFiles.candidates(directory));
        if (startup || new File(directory, "update-check.yml").exists()) {
            names.put("update-check.yml", "update-check.yml");
        }
        File[] languages =
                new File(directory, "lang")
                        .listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
        if (languages != null) {
            Arrays.sort(languages, java.util.Comparator.comparing(File::getName));
            for (File file : languages) {
                String path = "lang/" + file.getName();
                names.put(path, MenuFiles.class.getResource("/" + path) == null ? null : path);
            }
        }
        if (startup) {
            names.putIfAbsent("lang/zh_CN.yml", "lang/zh_CN.yml");
            names.putIfAbsent("lang/en_US.yml", "lang/en_US.yml");
            names.putIfAbsent("text.yml", "text.yml");
            names.putIfAbsent("translations/zh_cn.yml", "translations/zh_cn.yml");
            names.putIfAbsent("translations/en_us.yml", "translations/en_us.yml");
            File config = new File(directory, "config.yml");
            if (!config.exists() && !new File(directory, "menu.yml").exists()) {
                if (new File(directory, "menus").exists()
                        || new File(directory, "templates").exists()) {
                    // Finish language preflight too; an incomplete layout never exports over it.
                    names.put("config.yml", null);
                } else {
                    names.put("config.yml", "catalog/config.yml");
                    for (String id : CatalogRepository.defaults()) {
                        names.put("menus/" + id + ".yml", "catalog/menus/" + id + ".yml");
                    }
                }
            } else if (!"catalog".equals(BrokenFiles.layout(config))) {
                if (!new File(directory, "templates").exists()) {
                    for (String id : TemplateRepository.defaults()) {
                        names.put("templates/" + id + ".yml", "templates/" + id + ".yml");
                    }
                }
                if (!config.exists()) {
                    for (MenuLanguage language : MenuLanguage.values()) {
                        String path = "languages/" + language.id() + ".yml";
                        names.putIfAbsent(path, path);
                    }
                }
            }
            // A settings file with an illegal/missing Version is still a built-in settings file.
            // Prefer the existing layout, otherwise the current public catalog default.
            if (config.exists() && names.get("config.yml") == null) {
                boolean simple =
                        SimpleMenuParser.defaultPages().stream()
                                .anyMatch(
                                        id -> new File(directory, "menus/" + id + ".yml").isFile());
                String layout = simple ? "simple" : "catalog";
                names.put("config.yml", layout + "/config.yml");
                for (String path : names.keySet().toArray(String[]::new)) {
                    if (path.startsWith("menus/")
                            && MenuFiles.class.getResource("/" + layout + "/" + path) != null) {
                        names.put(path, layout + "/" + path);
                    }
                }
            }
        }
        for (Map.Entry<String, String> name : names.entrySet()) {
            Input input = new Input(directory, name.getKey(), name.getValue());
            result.inputs.put(input.path, input);
            ReloadTransaction tx = ReloadTransaction.current();
            if (tx != null) {
                tx.checkYaml(input.file, input.path);
            }
            try {
                if (!input.file.exists()) {
                    Kt.require(startup && input.resource != null, () -> input.path + ": 文件不存在");
                    input.text = MenuRepository.resource(input.resource);
                } else {
                    Kt.require(input.file.length() <= 1_048_576, () -> input.path + ": 文件超过 1 MiB");
                    input.original = Files.readAllBytes(input.file.toPath());
                    input.text = decode(input.original);
                    // Keep source spelling and positions for strict duplicate/alias validation.
                    String repaired = indentation(input.text);
                    input.tabs = !repaired.equals(input.text);
                    input.text = repaired;
                }
                MenuConfigParser.yaml(input.text, input.path);
                if (input.path.startsWith("lang/")) {
                    StartupLanguages.nodes(input.text, input.path);
                }
                if (input.path.equals("update-check.yml")) {
                    StartupUpdates.validate(input.text);
                }
            } catch (IOException | RuntimeException error) {
                input.problem = result.problem(input, error);
                if (tx != null && tx.valid()) {
                    tx.problem(input.problem);
                }
                if (startup) {
                    result.quarantine(input);
                }
            }
        }
        return result;
    }

    static String decode(byte[] bytes) throws CharacterCodingException {
        String text =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /** Public tx.checkYaml owns repair/backup; the business parser consumes the same indentation. */
    static String indentation(String source) {
        return Pattern.compile("(?m)^[ \\t]*")
                .matcher(source)
                .replaceAll(match -> match.group().replace("\t", "    "));
    }

    boolean has(String path) {
        Input input = inputs.get(path);
        return input != null && input.text != null;
    }

    String text(String path) {
        Input input = inputs.get(path);
        Kt.require(input != null && input.text != null, () -> path + ": 文件未加载或不存在");
        return input.text;
    }

    Map<String, String> group(String folder) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Input input : inputs.values()) {
            if (input.path.startsWith(folder + "/") && input.text != null) {
                result.put(Kt.nameWithoutExtension(input.file), input.text);
            }
        }
        return result;
    }

    private Input owner(Throwable error) {
        String message = String.valueOf(error.getMessage());
        for (Input input : inputs.values()) {
            if (message.startsWith(input.path)) {
                return input;
            }
        }
        for (Input input : inputs.values()) {
            if (message.contains(input.path + ":") || message.contains(input.path + ".")) {
                return input;
            }
        }
        for (Input input : inputs.values()) {
            String id = Kt.nameWithoutExtension(input.file);
            if ((input.path.startsWith("menus/") && message.contains(id + "/"))
                    || (input.path.startsWith("templates/")
                            && message.contains("templates." + id))) {
                return input;
            }
        }
        if (message.startsWith("menus: 无效菜单文件名 ")) {
            String id = message.substring("menus: 无效菜单文件名 ".length());
            return inputs.get("menus/" + id + ".yml");
        }
        return null;
    }

    LoadProblem problem(Throwable error) {
        Input input = owner(error);
        if (input == null) {
            return LoadProblem.builder("config.yml", new File(directory, "config.yml"))
                    .content(String.valueOf(error.getMessage()), -1, -1)
                    .build();
        }
        return problem(input, error);
    }

    private LoadProblem problem(Input input, Throwable error) {
        LoadProblem.Builder builder =
                LoadProblem.builder(input.path, input.file).error(error).source(input.text);
        if (error instanceof CharacterCodingException && input.original != null) {
            return builder.encoding(input.original).build();
        }
        LoadProblem parsed = builder.build();
        if (parsed.line() > 0 || error instanceof IOException) {
            return parsed;
        }
        String cause = String.valueOf(error.getMessage());
        SourcePosition position = semanticPosition(input, cause);
        return LoadProblem.builder(input.path, input.file)
                .content(cause, position.line(), position.column())
                .build();
    }

    private record SourcePosition(int line, int column) {
        static SourcePosition of(Node node) {
            return new SourcePosition(
                    node.getStartMark().getLine() + 1, node.getStartMark().getColumn() + 1);
        }
    }

    /** Locate the actual original YAML key, including inline mappings; never guess between repeats. */
    private static SourcePosition semanticPosition(Input input, String cause) {
        SourcePosition unknown = new SourcePosition(-1, -1);
        if (input.text == null) return unknown;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(40);
            options.setCodePointLimit(1_048_576);
            Node root = new Yaml(options).compose(new StringReader(input.text));
            Matcher path =
                    Pattern.compile(
                                    Pattern.quote(input.path)
                                            + "((?:\\.[A-Za-z0-9_-]+|\\[\\d+\\])+)")
                            .matcher(cause);
            if (path.find()) {
                Node current = root;
                Node key = null;
                boolean complete = true;
                for (String part : path.group(1).substring(1).split("\\.")) {
                    String name = part.replaceAll("\\[\\d+\\]", "");
                    if (!(current instanceof MappingNode map)) {
                        complete = false;
                        break;
                    }
                    var field =
                            map.getValue().stream()
                                    .filter(
                                            tuple ->
                                                    tuple.getKeyNode() instanceof ScalarNode scalar
                                                            && scalar.getValue().equals(name))
                                    .findFirst();
                    if (field.isEmpty()) {
                        complete = false;
                        break;
                    }
                    key = field.get().getKeyNode();
                    current = field.get().getValueNode();
                }
                if (complete && key != null) return SourcePosition.of(key);
            }
            Matcher field =
                    Pattern.compile("\\.([A-Za-z][A-Za-z0-9_-]*)(?=[:.\\[])").matcher(cause);
            String key = null;
            while (field.find()) key = field.group(1);
            if (key != null) {
                java.util.List<Node> matches = new java.util.ArrayList<>();
                findKeys(root, key, matches);
                if (matches.size() == 1) return SourcePosition.of(matches.getFirst());
            }
        } catch (RuntimeException ignored) {
            // Syntax/encoding positions were already taken from the original parser above.
        }
        return unknown;
    }

    private static void findKeys(Node node, String key, java.util.List<Node> matches) {
        if (node instanceof MappingNode map) {
            for (var tuple : map.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode scalar
                        && scalar.getValue().equals(key)) matches.add(tuple.getKeyNode());
                findKeys(tuple.getValueNode(), key, matches);
            }
        } else if (node instanceof SequenceNode list) {
            for (Node child : list.getValue()) findKeys(child, key, matches);
        }
    }

    private void quarantine(Input input) {
        input.replacement = input.resource != null;
        input.text = input.replacement ? MenuRepository.resource(input.resource) : null;
        if (input.replacement && input.path.equals("config.yml")) {
            String layout = input.resource.startsWith("simple/") ? "simple" : "catalog";
            Iterable<String> ids =
                    layout.equals("simple")
                            ? SimpleMenuParser.defaultPages()
                            : CatalogRepository.defaults();
            for (String id : ids) {
                String path = "menus/" + id + ".yml";
                if (!inputs.containsKey(path) && !new File(directory, path).exists()) {
                    Input missing = new Input(directory, path, layout + "/" + path);
                    missing.text = MenuRepository.resource(missing.resource);
                    inputs.put(path, missing);
                }
            }
        }
    }

    MenuCandidate startupCandidate() {
        for (int attempt = 0; attempt <= inputs.size(); attempt++) {
            try {
                return MenuCandidate.parse(this);
            } catch (RuntimeException error) {
                Input input = owner(error);
                if (input == null || input.problem != null) {
                    throw error;
                }
                // A dependency of a skipped custom menu must not replace another good file.
                String message = String.valueOf(error.getMessage());
                for (Input skipped : inputs.values()) {
                    if (skipped.problem != null
                            && !skipped.replacement
                            && message.contains(Kt.nameWithoutExtension(skipped.file))) {
                        throw error;
                    }
                }
                input.problem = problem(input, error);
                quarantine(input);
            }
        }
        throw new IllegalStateException("启动候选无法完成校验");
    }

    /** Called only after the whole read-only startup preflight. */
    void applyStartup() {
        for (Input input : inputs.values()) {
            if (input.problem == null) {
                if (input.original == null && input.text != null) {
                    try {
                        // A file created during preflight belongs to its creator, not this export.
                        if (!input.file.exists()) {
                            StorageWriter.writeAtomic(input.file, input.text);
                        }
                    } catch (IOException error) {
                        guardedLanguages |= input.path.startsWith("lang/");
                        MenuLog.severe(input.path + " 默认文件导出失败：" + error.getMessage());
                    }
                } else if (input.tabs) {
                    try {
                        Kt.require(
                                Arrays.equals(
                                        input.original, Files.readAllBytes(input.file.toPath())),
                                () -> "预检期间文件已修改，不修复 TAB");
                        FileBackup.Backup backup = FileBackup.beside(input.file, input.original);
                        WriteGuard.unblock(input.file);
                        StorageWriter.writeAtomic(input.file, input.text);
                        ConfigProblems.resolve(input.file);
                        MenuLog.info(input.path + " 的 TAB 缩进已修复，原字节备份已读回核对：" + backup.display());
                    } catch (IOException | RuntimeException error) {
                        guardedLanguages |= input.path.startsWith("lang/");
                        WriteGuard.block(input.file, "TAB 修复失败：" + error.getMessage());
                        ConfigProblems.report(
                                LoadProblem.builder(input.path, input.file)
                                        .content("TAB 修复失败：" + error.getMessage(), -1, -1)
                                        .backupFailed(error)
                                        .outcome(LoadProblem.Outcome.WRITE_BLOCKED)
                                        .build());
                    }
                } else {
                    WriteGuard.unblock(input.file);
                    ConfigProblems.resolve(input.file);
                }
                continue;
            }
            // Forward the already formatted cause without content(), which appends positions.
            // A second ordinary Lang.load would also erase a just-reported recovery outcome.
            guardedLanguages |= input.path.startsWith("lang/");
            LoadProblem found = input.problem;
            LoadProblem.Builder report =
                    LoadProblem.builder(input.path, input.file)
                            .kind(found.kind())
                            .cause(found.cause())
                            .position(found.line(), found.column())
                            .contentDefaults(input.replacement);
            if (!input.replacement) {
                guardedLanguages |= input.path.startsWith("lang/");
                WriteGuard.block(input.file, "自建内容无法加载");
                ConfigProblems.report(
                        report.outcome(LoadProblem.Outcome.NOT_LOADED)
                                .note("自建文件保持原样，本次跳过；修正后执行 /dmenu reload。")
                                .build());
                continue;
            }
            FileBackup.Backup backup = null;
            try {
                Kt.require(input.original != null, () -> "没有可核对的原字节");
                Kt.require(
                        Arrays.equals(input.original, Files.readAllBytes(input.file.toPath())),
                        () -> "预检期间文件已修改，不替换");
                backup = FileBackup.beside(input.file, input.original);
                report.backup(backup);
                WriteGuard.unblock(input.file);
                StorageWriter.writeAtomic(input.file, input.text);
                Kt.require(
                        input.text.equals(
                                Files.readString(input.file.toPath(), StandardCharsets.UTF_8)),
                        () -> "默认文件读回不一致");
                ConfigProblems.report(report.outcome(LoadProblem.Outcome.REPLACED).build());
            } catch (IOException | RuntimeException error) {
                guardedLanguages |= input.path.startsWith("lang/");
                WriteGuard.block(input.file, "默认恢复失败：" + error.getMessage());
                if (backup == null) {
                    report.backupFailed(error);
                } else {
                    report.note("默认写入失败，已核验的原字节备份保留；内存使用完整默认，暂停写入：" + error.getMessage());
                }
                ConfigProblems.report(report.outcome(LoadProblem.Outcome.WRITE_BLOCKED).build());
            }
        }
    }

    void loadStartupLanguages() {
        if (guardedLanguages) {
            StartupLanguages.install(group("lang"));
        } else {
            dev.keystone.lang.Lang.load();
        }
    }

    /** A deleted or inactive content file no longer participates in a successful reload. */
    void resolveInactive() {
        var folder = directory.getAbsoluteFile().toPath().normalize();
        for (LoadProblem problem : ConfigProblems.current()) {
            var file = problem.file().getAbsoluteFile().toPath().normalize();
            if (!file.startsWith(folder)) continue;
            String path = folder.relativize(file).toString().replace('\\', '/');
            boolean owned =
                    path.equals("menu.yml")
                            || path.equals("config.yml")
                            || path.equals("text.yml")
                            || path.startsWith("menus/")
                            || path.startsWith("templates/")
                            || path.startsWith("translations/")
                            || path.startsWith("languages/")
                            || path.startsWith("lang/");
            if (owned && !inputs.containsKey(path)) {
                ConfigProblems.resolve(problem.file());
                WriteGuard.unblock(problem.file());
            }
        }
    }

    static MenuCandidate bundled() {
        MenuFiles files = new MenuFiles(new File("."));
        Map<String, String> names = new LinkedHashMap<>();
        names.put("config.yml", "catalog/config.yml");
        names.put("text.yml", "text.yml");
        names.put("translations/zh_cn.yml", "translations/zh_cn.yml");
        names.put("translations/en_us.yml", "translations/en_us.yml");
        for (String id : CatalogRepository.defaults()) {
            names.put("menus/" + id + ".yml", "catalog/menus/" + id + ".yml");
        }
        for (Map.Entry<String, String> entry : names.entrySet()) {
            Input input = new Input(files.directory, entry.getKey(), entry.getValue());
            input.text = MenuRepository.resource(input.resource);
            files.inputs.put(input.path, input);
        }
        return MenuCandidate.parse(files);
    }
}
