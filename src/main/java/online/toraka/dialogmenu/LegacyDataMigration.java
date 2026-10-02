package online.toraka.dialogmenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Imports the old directory without deleting it or replacing administrator edits. */
public final class LegacyDataMigration {
    private static final String MARKER = ".playersettings-import";

    private LegacyDataMigration() {}

    public static boolean migrate(Path destination) throws IOException {
        return migrate(destination, Collections.emptyMap());
    }

    public static boolean migrate(Path destination, Map<String, byte[]> generatedFiles)
            throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path legacy = target.resolveSibling("PlayerSettings");
        Path marker = target.resolve(MARKER);
        List<String> mainFiles = Kt.listOf("config.yml", "menu.yml");
        Kt.require(
                !Files.isSymbolicLink(target) && !Files.isSymbolicLink(legacy),
                () -> "配置目录不能是符号链接：" + target + " / " + legacy);
        if (!Files.exists(marker) && anyRegularFile(target, mainFiles)) {
            return false;
        }
        if (!Files.isDirectory(legacy)) {
            Kt.check(!Files.exists(marker), () -> "迁移未完成，但旧目录已不存在：" + legacy);
            return false;
        }
        if (!anyRegularFile(legacy, mainFiles)) {
            return false;
        }
        List<Path> entries;
        try (Stream<Path> paths = Files.walk(legacy)) {
            entries = paths.filter(it -> !it.equals(legacy)).toList();
        }
        // Preflight every path before writing; existing user files must never be overwritten.
        for (Path source : entries) {
            Path relative = legacy.relativize(source);
            Path output = target.resolve(relative);
            Kt.require(
                    !Files.isSymbolicLink(source) && !Files.isSymbolicLink(output),
                    () -> "迁移不支持符号链接：" + source + " / " + output);
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                Kt.check(
                        !Files.exists(output)
                                || Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS),
                        () -> "迁移路径冲突：" + output);
            } else {
                Kt.check(
                        Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS),
                        () -> "迁移不支持此文件：" + source);
                if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] bundled = generatedFiles.get(relative.toString().replace('\\', '/'));
                    Kt.check(
                            Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)
                                    && (Files.mismatch(source, output) == -1L
                                            || bundled != null
                                                    && Arrays.equals(
                                                            Files.readAllBytes(output), bundled)),
                            () -> "迁移文件冲突，保留两份原文件，请手动合并：" + output);
                }
            }
        }
        Files.createDirectories(target);
        Files.writeString(marker, "PlayerSettings -> DialogMenu\n");
        // Configs are copied last, so an interrupted copy cannot look like a completed migration.
        List<Path> ordered = new ArrayList<>(entries);
        ordered.sort(
                Comparator.comparingInt(
                        it -> mainFiles.contains(legacy.relativize(it).toString()) ? 1 : 0));
        for (Path source : ordered) {
            Path output = target.resolve(legacy.relativize(source));
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(output);
            } else {
                Files.createDirectories(output.getParent());
                Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.delete(marker);
        return true;
    }

    /** Kotlin {@code mainFiles.any { Files.isRegularFile(directory.resolve(it)) }}. */
    private static boolean anyRegularFile(Path directory, List<String> names) {
        for (String name : names) {
            if (Files.isRegularFile(directory.resolve(name))) {
                return true;
            }
        }
        return false;
    }
}
