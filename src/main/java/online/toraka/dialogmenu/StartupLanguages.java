package online.toraka.dialogmenu;

import dev.keystone.lang.Lang;
import dev.keystone.lang.LangNode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

/** Plugin language preflight; preserves Keystone's text/scalar/list representation. */
final class StartupLanguages {
    private StartupLanguages() {}

    static Map<String, LangNode> nodes(String source, String path) {
        YamlConfiguration yaml = MenuConfigParser.yaml(source, path);
        Map<String, LangNode> result = new LinkedHashMap<>();
        for (String key : yaml.getKeys(false)) {
            if (key.equals("___version___")) {
                continue;
            }
            Object value = yaml.get(key);
            if (value instanceof List<?> list) {
                List<LangNode.Text> lines = new ArrayList<>();
                for (Object item : list) {
                    Kt.require(
                            item == null || scalar(item),
                            () -> path + "." + key + ": 语言列表仅支持文字或标量");
                    String text = item == null ? null : item.toString();
                    lines.add(new LangNode.Text(text == null || text.isEmpty() ? null : text));
                }
                result.put(key, new LangNode.Lines(lines));
            } else {
                Kt.require(scalar(value), () -> path + "." + key + ": 语言值需要文字、标量或文字列表");
                String text = value.toString();
                result.put(key, new LangNode.Text(text.isEmpty() ? null : text));
            }
        }
        return result;
    }

    private static boolean scalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    /**
     * TODO(keystone): expose installing a prevalidated Lang candidate for startup with guarded
     * files. Normal Lang.load unblocks files and recovers journals; it cannot read those files.
     * This adapter only swaps Keystone's language snapshot, never filesystem or guard state.
     */
    static void install(Map<String, String> sources) {
        Map<String, Map<String, LangNode>> languages = new LinkedHashMap<>();
        for (String code : Kt.listOf("zh_CN", "en_US")) {
            languages.put(
                    code,
                    new LinkedHashMap<>(
                            nodes(
                                    MenuRepository.resource("lang/" + code + ".yml"),
                                    "lang/" + code + ".yml")));
        }
        for (Map.Entry<String, String> source : sources.entrySet()) {
            String code = source.getKey();
            String existing =
                    languages.keySet().stream()
                            .filter(key -> key.equalsIgnoreCase(code))
                            .findFirst()
                            .orElse(code);
            languages
                    .computeIfAbsent(existing, ignored -> new LinkedHashMap<>())
                    .putAll(nodes(source.getValue(), "lang/" + code + ".yml"));
        }
        Map<String, Map<String, LangNode>> frozen = new LinkedHashMap<>();
        languages.forEach((code, nodes) -> frozen.put(code, Collections.unmodifiableMap(nodes)));
        try {
            Field files = Lang.class.getDeclaredField("files");
            Field ceAbsent = Lang.class.getDeclaredField("ceAbsent");
            files.setAccessible(true);
            ceAbsent.setAccessible(true);
            files.set(null, Collections.unmodifiableMap(frozen));
            ceAbsent.setBoolean(null, !Bukkit.getPluginManager().isPluginEnabled("CraftEngine"));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("无法安装已预检的启动语言候选", error);
        }
    }
}
