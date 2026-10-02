package online.toraka.dialogmenu;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Pure file installation, separate from Bukkit and from the resource-pack sender. */
public final class BundledResourcePack {
    public static final String RESOURCE = "bundled/DialogMenu-resourcepack.zip";

    private final Path dataDirectory;
    private final byte[] archive;

    /** Kotlin {@code by lazy}: computed on first use and cached once it succeeded. */
    private Map<String, byte[]> entries;

    public BundledResourcePack(Path dataDirectory, byte[] archive) {
        this.dataDirectory = dataDirectory;
        this.archive = archive;
    }

    public static AvailableResourceProvider detect(
            MenuResourcePack config, List<AvailableResourceProvider> available) {
        List<AvailableResourceProvider> eligible;
        if (config.provider().equals("craftengine")) {
            eligible = new ArrayList<>();
            for (AvailableResourceProvider candidate : available) {
                if (candidate.provider() == ResourcePackProvider.CRAFT_ENGINE) {
                    eligible.add(candidate);
                }
            }
        } else {
            eligible = available;
        }
        // minByOrNull: the first provider wins ties.
        AvailableResourceProvider best = null;
        for (AvailableResourceProvider candidate : eligible) {
            if (best == null || candidate.provider().ordinal() < best.provider().ordinal()) {
                best = candidate;
            }
        }
        return best;
    }

    public static AvailableResourceProvider select(
            MenuResourcePack config, List<AvailableResourceProvider> available) {
        if (config.autoInstall()) {
            return detect(config, available);
        }
        return null;
    }

    private static String digest(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw Kt.sneaky(error);
        }
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private static boolean validName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        if (name.startsWith("/")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (character == '\\' || character == ':' || Character.isISOControl(character)) {
                return false;
            }
        }
        for (String part : Kt.split(name, '/')) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static Path safePath(Path root, String name) throws IOException {
        return safePath(root, name, null);
    }

    private static Path safePath(Path root, String name, Path expectedRoot) throws IOException {
        Kt.require(validName(name), () -> "不安全的资源路径：" + name);
        Path absolute = root.toAbsolutePath().normalize();
        Files.createDirectories(absolute);
        Path realRoot = absolute.toRealPath();
        Kt.require(
                expectedRoot == null || realRoot.equals(expectedRoot),
                () -> "资源目标目录已改变，已停止写入：" + absolute);
        Path target = absolute.resolve(name).normalize();
        Kt.require(target.startsWith(absolute), () -> "资源路径超出目标目录：" + name);
        Path current = absolute;
        for (Path component : absolute.relativize(target)) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                Path checked = current;
                Kt.require(
                        !Files.isSymbolicLink(checked)
                                && checked.toRealPath()
                                        .equals(realRoot.resolve(absolute.relativize(checked))),
                        () -> "资源路径包含链接或重定向：" + checked);
            }
        }
        return target;
    }

    private static void atomicWrite(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".dialogmenu-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Map<String, byte[]> entries() throws IOException {
        if (entries == null) {
            entries = readEntries();
        }
        return entries;
    }

    private Map<String, byte[]> readEntries() throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        long expanded = 0L;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            while (true) {
                ZipEntry entry = zip.getNextEntry();
                if (entry == null) {
                    break;
                }
                if (entry.isDirectory()) {
                    continue;
                }
                Kt.require(validName(entry.getName()), () -> "内置资源包包含不安全路径：" + entry.getName());
                Kt.require(
                        !result.containsKey(entry.getName()),
                        () -> "内置资源包包含重复文件：" + entry.getName());
                byte[] bytes = zip.readNBytes(16 * 1024 * 1024 + 1);
                expanded += bytes.length;
                Kt.require(
                        bytes.length <= 16 * 1024 * 1024
                                && expanded <= 128 * 1024 * 1024
                                && result.size() < 50000,
                        () -> "内置资源包超过大小限制");
                result.put(entry.getName(), bytes);
            }
        }
        boolean anyAssets = false;
        for (String name : result.keySet()) {
            if (name.startsWith("assets/")) {
                anyAssets = true;
                break;
            }
        }
        Kt.require(
                result.containsKey("pack.mcmeta") && anyAssets,
                () -> "内置资源包缺少 pack.mcmeta 或 assets");
        return result;
    }

    /**
     * Only the provider's input root may redirect; all descendants stay inside its resolved root.
     */
    private record InstallationRoot(Path logicalRoot, Path logicalInput, Path resolvedInput) {
        Path resolve(String name) throws IOException {
            Kt.require(validName(name), () -> "不安全的资源路径：" + name);
            Path logicalTarget = logicalRoot.resolve(name).normalize();
            Kt.require(logicalTarget.startsWith(logicalInput), () -> "资源路径超出输入目录：" + name);
            List<String> parts = new ArrayList<>();
            for (Path part : logicalInput.relativize(logicalTarget)) {
                parts.add(part.toString());
            }
            String relative = String.join("/", parts);
            return safePath(resolvedInput, relative, resolvedInput);
        }
    }

    public ResourcePackInstallResult install(
            MenuResourcePack config, List<AvailableResourceProvider> available) {
        try {
            // Validate the entire archive before writing anything.
            Map<String, byte[]> assets = entries();
            Path exported = safePath(dataDirectory, "resourcepack/DialogMenu-resourcepack.zip");
            WriteResult exportResult =
                    synchronize(
                            dataDirectory,
                            "resourcepack/export-state.properties",
                            Kt.<String, byte[]>mapOf(
                                    "resourcepack/DialogMenu-resourcepack.zip", archive));
            AvailableResourceProvider provider = select(config, available);
            if (provider == null) {
                return new ResourcePackInstallResult(exported, null, 0, exportResult.conflicts());
            }
            String prefix = provider.provider().sourceDirectory();
            String destination =
                    switch (provider.provider()) {
                        case CRAFT_ENGINE, ITEMS_ADDER -> prefix + "/resourcepack";
                        default -> prefix;
                    };
            Map<String, byte[]> files = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> asset : assets.entrySet()) {
                String name = asset.getKey();
                byte[] bytes = asset.getValue();
                // Oraxen owns pack.mcmeta; CE and IA also generate their own top-level metadata.
                boolean license =
                        name.indexOf('/') < 0
                                && (name.regionMatches(true, 0, "LICENSE", 0, "LICENSE".length())
                                        || name.regionMatches(
                                                true, 0, "NOTICE", 0, "NOTICE".length()));
                if (name.startsWith("assets/")
                        || license
                        || provider.provider() == ResourcePackProvider.NEXO) {
                    files.put(destination + "/" + name, bytes);
                }
            }
            if (provider.provider() == ResourcePackProvider.CRAFT_ENGINE) {
                files.put(
                        prefix + "/pack.yml",
                        ("author: DialogMenu\ndescription: Bundled DialogMenu menu resources\n"
                                        + "version: '1.0'\nnamespace: dialogmenu\nenable: true\n")
                                .getBytes(StandardCharsets.UTF_8));
            }
            ExternalConflicts shared = externalConflicts(provider, assets);
            Map<String, String> external = new LinkedHashMap<>();
            for (Map.Entry<String, String> conflict : shared.files().entrySet()) {
                external.put(destination + "/" + conflict.getKey(), conflict.getValue());
            }
            WriteResult result =
                    synchronize(
                            provider.directory(),
                            "resourcepack/"
                                    + Kt.lower(provider.provider().name())
                                    + "-state.properties",
                            files,
                            external,
                            provider.provider().inputDirectory());
            List<String> conflicts = new ArrayList<>();
            conflicts.addAll(exportResult.conflicts());
            conflicts.addAll(provider.warnings());
            conflicts.addAll(shared.warnings());
            conflicts.addAll(result.conflicts());
            return new ResourcePackInstallResult(exported, provider, result.changed(), conflicts);
        } catch (IOException error) {
            // Kotlin has no checked exceptions: the same IOException reaches the caller.
            throw Kt.sneaky(error);
        }
    }

    private record WriteResult(int changed, List<String> conflicts) {}

    private WriteResult synchronize(Path targetRoot, String stateName, Map<String, byte[]> files)
            throws IOException {
        return synchronize(targetRoot, stateName, files, Collections.emptyMap(), "");
    }

    private WriteResult synchronize(
            Path targetRoot,
            String stateName,
            Map<String, byte[]> files,
            Map<String, String> external,
            String inputDirectory)
            throws IOException {
        Path stateFile = safePath(dataDirectory, stateName);
        Properties state = new Properties();
        if (Files.isRegularFile(stateFile)) {
            try (InputStream input = Files.newInputStream(stateFile)) {
                state.load(input);
            }
        }
        Path logicalRoot = targetRoot.toAbsolutePath().normalize();
        Path logicalInput = logicalRoot.resolve(inputDirectory);
        InstallationRoot root;
        try {
            Files.createDirectories(logicalInput);
            root = new InstallationRoot(logicalRoot, logicalInput, logicalInput.toRealPath());
        } catch (Exception error) {
            return new WriteResult(
                    0, Kt.listOf(logicalInput + "（无法访问资源输入目录：" + error.getMessage() + "）"));
        }
        String scope = logicalRoot.toString();
        String previousRoot = state.getProperty("resolved-root");
        // Legacy state did not record resolved paths. Only inherit it for an ordinary directory.
        boolean sameRoot;
        if (previousRoot == null) {
            sameRoot = root.resolvedInput().equals(logicalInput);
        } else {
            sameRoot = previousRoot.equals(root.resolvedInput().toString());
        }
        if (!Objects.equals(state.getProperty("target"), scope) || !sameRoot) {
            state.clear();
        }
        Properties next = new Properties();
        next.setProperty("target", scope);
        next.setProperty("resolved-root", root.resolvedInput().toString());
        int changed = 0;
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String name = file.getKey();
            byte[] bytes = file.getValue();
            String previous = state.getProperty("file." + name);
            if (previous != null) {
                next.setProperty("file." + name, previous);
            }
            try {
                Path target = root.resolve(name);
                String hash = digest(bytes);
                String current;
                if (Files.isRegularFile(target)) {
                    current = digest(Files.readAllBytes(target));
                } else {
                    current = null;
                }
                String elsewhere = external.get(name);
                if (elsewhere != null) {
                    // Remove our unchanged duplicate so it cannot override the user's shader during
                    // merging.
                    if (previous != null && Objects.equals(current, previous)) {
                        Files.delete(target);
                        next.remove("file." + name);
                        changed++;
                    }
                    conflicts.add(name + "（另一个资源源中有不同内容：" + elsewhere + "）");
                } else if (Files.exists(target)
                        && !Objects.equals(current, hash)
                        && (previous == null || !Objects.equals(current, previous))) {
                    conflicts.add(name + "（已有文件或目录由服主维护，已保留）");
                } else if (!Objects.equals(current, hash)) {
                    atomicWrite(target, bytes);
                    next.setProperty("file." + name, hash);
                    changed++;
                } else if (previous != null) {
                    next.setProperty("file." + name, hash);
                }
                // Identical unmanaged files remain unmanaged; never claim ownership of user files.
            } catch (Exception error) {
                conflicts.add(name + "（" + error.getMessage() + "）");
            }
        }
        List<String> managed = new ArrayList<>();
        for (String key : state.stringPropertyNames()) {
            if (key.startsWith("file.")) {
                managed.add(key);
            }
        }
        for (String key : managed) {
            String name = Kt.removePrefix(key, "file.");
            if (files.containsKey(name)) {
                continue;
            }
            try {
                Path target = root.resolve(name);
                if (Files.isRegularFile(target)
                        && digest(Files.readAllBytes(target)).equals(state.getProperty(key))) {
                    Files.delete(target);
                    changed++;
                }
            } catch (Exception error) {
                conflicts.add(name + "（旧文件保留：" + error.getMessage() + "）");
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        next.store(output, "DialogMenu managed resources; edited files are never overwritten");
        if (!next.equals(state)) {
            atomicWrite(stateFile, output.toByteArray());
        }
        return new WriteResult(changed, conflicts);
    }

    private record ExternalConflicts(Map<String, String> files, List<String> warnings) {}

    /** The state that the local helpers of {@link #externalConflicts} share. */
    private static final class ExternalScan {
        private final Map<String, byte[]> assets;
        private final List<Path> roots = new ArrayList<>();
        private final List<Path> archives = new ArrayList<>();
        private final Map<String, String> conflicts = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();

        private ExternalScan(Map<String, byte[]> assets) {
            this.assets = assets;
        }

        private void unreadable(Path path, Exception error) {
            warnings.add(path + "（无法检查已有资源，核心 shader 未安装：" + error.getMessage() + "）");
            List<String> shaders = new ArrayList<>();
            for (String name : assets.keySet()) {
                if (name.startsWith("assets/minecraft/shaders/")) {
                    shaders.add(name);
                }
            }
            for (String name : shaders) {
                conflicts.put(name, path.toString());
            }
        }

        private List<Path> children(Path folder) {
            try {
                if (!Files.isDirectory(folder)) {
                    return Collections.emptyList();
                }
                try (Stream<Path> stream = Files.list(folder)) {
                    return stream.toList();
                }
            } catch (Exception error) {
                unreadable(folder, error);
                return Collections.emptyList();
            }
        }

        private void imported(Path folder) {
            imported(folder, false);
        }

        private void imported(Path folder, boolean skipOwnDirectory) {
            List<Path> found = new ArrayList<>();
            for (Path child : children(folder)) {
                if (!skipOwnDirectory || !child.getFileName().toString().equals("dialogmenu")) {
                    found.add(child);
                }
            }
            for (Path child : found) {
                if (Files.isDirectory(child)) {
                    roots.add(child);
                } else if (endsWithIgnoreCase(child.getFileName().toString(), ".zip")) {
                    archives.add(child);
                }
            }
        }

        /** Kotlin {@code endsWith(suffix, ignoreCase = true)}. */
        private static boolean endsWithIgnoreCase(String text, String suffix) {
            return text.regionMatches(
                    true, text.length() - suffix.length(), suffix, 0, suffix.length());
        }
    }

    private ExternalConflicts externalConflicts(
            AvailableResourceProvider provider, Map<String, byte[]> assets) {
        ExternalScan scan = new ExternalScan(assets);
        Path base = provider.directory();
        scan.roots.addAll(provider.externalDirectories());
        scan.archives.addAll(provider.externalArchives());
        switch (provider.provider()) {
            case CRAFT_ENGINE -> {
                List<Path> packs = new ArrayList<>();
                for (Path child : scan.children(base.resolve("resources"))) {
                    if (!child.getFileName().toString().equals("dialogmenu")
                            && !child.getFileName().toString().startsWith(".")) {
                        packs.add(child);
                    }
                }
                for (Path pack : packs) {
                    scan.roots.add(pack.resolve("resourcepack"));
                }
            }
            case ITEMS_ADDER -> {
                List<Path> packs = new ArrayList<>();
                for (Path child : scan.children(base.resolve("contents"))) {
                    if (!child.getFileName().toString().equals("dialogmenu")) {
                        packs.add(child);
                    }
                }
                for (Path pack : packs) {
                    scan.roots.add(pack.resolve("resourcepack"));
                    scan.roots.add(pack);
                }
            }
            case NEXO -> {
                scan.roots.add(base.resolve("pack"));
                scan.imported(base.resolve("pack/external_packs"), true);
            }
            case ORAXEN -> scan.imported(base.resolve("pack/uploads"));
        }
        for (Map.Entry<String, byte[]> asset : filterAssets(assets).entrySet()) {
            String name = asset.getKey();
            byte[] bytes = asset.getValue();
            for (Path root : scan.roots) {
                List<Path> candidates;
                if (provider.provider() == ResourcePackProvider.ITEMS_ADDER) {
                    candidates =
                            Kt.listOf(
                                    root.resolve(name),
                                    root.resolve(Kt.removePrefix(name, "assets/")));
                } else {
                    candidates = Kt.listOf(root.resolve(name));
                }
                for (Path candidate : candidates) {
                    try {
                        if (Files.isRegularFile(candidate)) {
                            byte[] existing;
                            try (InputStream input = Files.newInputStream(candidate)) {
                                existing = input.readNBytes(bytes.length + 1);
                            }
                            if (!Arrays.equals(existing, bytes)) {
                                scan.conflicts.put(name, candidate.toString());
                            }
                        }
                    } catch (Exception error) {
                        scan.conflicts.put(name, candidate + "（" + error.getMessage() + "）");
                    }
                }
            }
        }
        for (Path archive : scan.archives) {
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                for (Map.Entry<String, byte[]> asset : filterAssets(assets).entrySet()) {
                    String name = asset.getKey();
                    byte[] bytes = asset.getValue();
                    ZipEntry entry = zip.getEntry(name);
                    if (entry == null) {
                        continue;
                    }
                    byte[] existing;
                    try (InputStream input = zip.getInputStream(entry)) {
                        existing = input.readNBytes(bytes.length + 1);
                    }
                    if (!Arrays.equals(existing, bytes)) {
                        scan.conflicts.put(name, archive + "!/" + name);
                    }
                }
            } catch (Exception error) {
                scan.unreadable(archive, error);
            }
        }
        return new ExternalConflicts(scan.conflicts, scan.warnings);
    }

    /** Kotlin {@code assets.filterKeys { it.startsWith("assets/") }}. */
    private static Map<String, byte[]> filterAssets(Map<String, byte[]> assets) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> asset : assets.entrySet()) {
            if (asset.getKey().startsWith("assets/")) {
                result.put(asset.getKey(), asset.getValue());
            }
        }
        return result;
    }
}
