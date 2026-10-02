package online.toraka.dialogmenu;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Menu-owned translations; independent of CraftEngine and the settings language preference. */
public record MenuTranslations(String defaultLanguage, Map<String, Map<String, String>> messages) {

    private static final Pattern LOCALE = Pattern.compile("[a-z]{2,8}(?:_[a-z0-9]{2,8})*");

    public MenuTranslations() {
        this("zh_cn", Collections.emptyMap());
    }

    /** The player's exact locale, then its language, then the configured default. */
    public String get(String key, Locale locale) {
        String requested =
                locale == null ? null : Kt.lower(locale.toLanguageTag().replace('-', '_'));
        Set<String> candidates = new LinkedHashSet<>();
        if (requested != null) {
            candidates.add(requested);
            candidates.add(Kt.substringBefore(requested, '_'));
        }
        candidates.add(defaultLanguage);
        for (String candidate : candidates) {
            Map<String, String> values = messages.get(candidate);
            String value = values == null ? null : values.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static void initialize(File directory) {
        for (String name :
                Kt.listOf("text.yml", "translations/zh_cn.yml", "translations/en_us.yml")) {
            File file = new File(directory, name);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                Kt.writeText(file, MenuRepository.resource(name));
            }
        }
    }

    public static MenuTranslations read(File directory) {
        return read(directory, BrokenFiles.Result.none());
    }

    /** {@link #read(File)}, leaving out the translation files {@code broken} could not read. */
    public static MenuTranslations read(File directory, BrokenFiles.Result broken) {
        File settingsFile = new File(directory, "text.yml");
        File[] listed = new File(directory, "translations").listFiles();
        List<File> files = new ArrayList<>(listed == null ? List.of() : Arrays.asList(listed));
        files.removeIf(
                file -> !file.isFile() || !Kt.extension(file).equals("yml") || broken.skips(file));
        files.sort(Comparator.comparing(File::getName));
        Map<String, String> sources = new LinkedHashMap<>();
        for (File file : files) {
            sources.put(Kt.nameWithoutExtension(file), Kt.readText(file));
        }
        return read(Kt.readText(settingsFile), sources);
    }

    static MenuTranslations read(String settingsText, Map<String, String> sources) {
        YamlConfiguration settings = MenuConfigParser.yaml(settingsText, "text.yml");
        Kt.require(
                settings.getKeys(false).stream().allMatch(key -> key.equals("DefaultLanguage")),
                () -> "text.yml: 仅支持 DefaultLanguage");
        Kt.require(
                !settings.contains("DefaultLanguage") || settings.isString("DefaultLanguage"),
                () -> "text.yml.DefaultLanguage: 需要语言代码字符串");
        String defaultLanguage =
                Kt.lower(settings.getString("DefaultLanguage", "zh_cn").replace('-', '_'));
        Map<String, Map<String, String>> translations = new LinkedHashMap<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            String name = source.getKey() + ".yml";
            String locale = Kt.lower(source.getKey().replace('-', '_'));
            Kt.require(
                    LOCALE.matcher(locale).matches(), () -> "translations/" + name + ": 无效语言文件名");
            Kt.require(
                    !translations.containsKey(locale),
                    () -> "translations/" + name + ": 重复语言 " + locale);
            YamlConfiguration root =
                    MenuConfigParser.yaml(source.getValue(), "translations/" + name);
            Map<String, String> values = new LinkedHashMap<>();
            boolean valid = true;
            for (Map.Entry<String, Object> entry : root.getValues(true).entrySet()) {
                Object value = entry.getValue();
                if (value instanceof ConfigurationSection) {
                    continue;
                }
                if (value instanceof String text && text.length() <= 8192) {
                    values.put(entry.getKey(), text);
                } else {
                    valid = false;
                }
            }
            boolean allStrings = valid;
            Kt.require(allStrings, () -> "translations/" + name + ": 翻译必须为不超过 8192 字符的字符串");
            translations.put(locale, values);
        }
        Kt.require(
                translations.containsKey(defaultLanguage),
                () -> "text.yml.DefaultLanguage: 缺少 translations/" + defaultLanguage + ".yml");
        return new MenuTranslations(defaultLanguage, translations);
    }
}
