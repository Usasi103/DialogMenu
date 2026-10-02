package online.toraka.dialogmenu;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Layout discovery and legacy diagnostic helpers; runtime recovery is in MenuFiles. */
final class BrokenFiles {

    /** What one pass found: files to leave out of the load, and the ERROR lines printed. */
    record Result(Set<File> skipped, List<String> errors) {

        static Result none() {
            return new Result(Collections.emptySet(), Collections.emptyList());
        }

        boolean skips(File file) {
            return skipped.contains(file.getAbsoluteFile());
        }
    }

    private static final Pattern VERSION =
            Pattern.compile("(?m)^Version\\s*:\\s*['\"]?(\\d+)['\"]?\\s*(?:#.*)?$");

    private static final Pattern POSITION = Pattern.compile("line (\\d+), column (\\d+)");

    private static final Pattern SOURCE_NAME =
            Pattern.compile("\\bin (?:'reader'|\"reader\"|reader|'string'|\"string\"|string), ");

    private BrokenFiles() {}

    /**
     * The content files of the layout in {@code directory} (relative path → jar default, or null
     * when there is none), in load order. Catalog ({@code config.yml} with {@code Version: 3}) and
     * simple ({@code Version: 2}) are told apart by the raw {@code Version:} line, so a {@code
     * config.yml} that no longer parses still finds its default; without that line it has none.
     */
    static Map<String, String> candidates(File directory) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("text.yml", shipped("text.yml"));
        for (String name : ymlFiles(new File(directory, "translations"))) {
            files.put("translations/" + name, shipped("translations/" + name));
        }
        File config = new File(directory, "config.yml");
        if (config.isFile()) {
            String layout = layout(config);
            files.put("config.yml", layout == null ? null : shipped(layout + "/config.yml"));
            for (String name : ymlFiles(new File(directory, "menus"))) {
                files.put(
                        "menus/" + name,
                        layout == null ? null : shipped(layout + "/menus/" + name));
            }
            if (!"catalog".equals(layout)) {
                templates(directory, files);
            }
        } else if (new File(directory, "menu.yml").isFile()) {
            files.put("menu.yml", shipped("menu.yml"));
            for (String name : ymlFiles(new File(directory, "languages"))) {
                files.put("languages/" + name, shipped("languages/" + name));
            }
            templates(directory, files);
        }
        return files;
    }

    private static void templates(File directory, Map<String, String> files) {
        for (String name : ymlFiles(new File(directory, "templates"))) {
            files.put("templates/" + name, shipped("templates/" + name));
        }
    }

    /** {@code catalog}, {@code simple}, or null when the raw text has no known Version line. */
    static String layout(File config) {
        String text;
        try {
            text = new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return null;
        }
        Matcher version = VERSION.matcher(text);
        if (!version.find()) {
            return null;
        }
        return switch (version.group(1)) {
            case "3" -> "catalog";
            case "2" -> "simple";
            default -> null;
        };
    }

    private static String shipped(String resource) {
        return BrokenFiles.class.getResource("/" + resource) != null ? resource : null;
    }

    private static List<String> ymlFiles(File folder) {
        File[] listed = folder.listFiles(file -> file.isFile() && Kt.extension(file).equals("yml"));
        List<File> files = new ArrayList<>(listed == null ? List.of() : Arrays.asList(listed));
        files.sort(Comparator.comparing(File::getName));
        List<String> names = new ArrayList<>();
        for (File file : files) {
            names.add(file.getName());
        }
        return names;
    }

    /**
     * What is wrong with an unreadable file, position first ({@code 第 3 行第 5 列：...}). Reads the
     * file again; never throws.
     */
    static String problem(File file) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (IOException error) {
            return "无法读取: " + error.getMessage();
        }
        String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (CharacterCodingException error) {
            return "不是 UTF-8 编码（可能被另存为 GBK）";
        }
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        try {
            new YamlConfiguration().loadFromString(text);
            return "未知的读取错误";
        } catch (InvalidConfigurationException | RuntimeException error) {
            return describe(error);
        }
    }

    /**
     * A YAML error as one line: the first {@code line N, column M} becomes {@code 第 N 行第 M 列}
     * in front, the rest follows with the parser's source name and caret lines dropped.
     */
    static String describe(Exception error) {
        String message =
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        Matcher position = POSITION.matcher(message);
        String where =
                position.find()
                        ? "第 " + position.group(1) + " 行第 " + position.group(2) + " 列："
                        : "";
        List<String> parts = new ArrayList<>();
        for (String raw : message.split("\\R")) {
            String line = SOURCE_NAME.matcher(raw.trim()).replaceAll("");
            line = POSITION.matcher(line).replaceAll("").trim();
            // A position line ("in 'reader', line 3, column 5:") leaves only its colon.
            if (line.isEmpty() || line.equals(":") || line.equals("^")) {
                continue;
            }
            parts.add(line);
        }
        return where + String.join(" / ", parts);
    }
}
