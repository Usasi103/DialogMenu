package online.toraka.dialogmenu;

import cn.gtemc.itembridge.core.BukkitItemBridge;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.bukkit.Bukkit;

/** Metadata shared with the build's soft-dependency list; provider classes stay lazily loaded. */
public final class ItemBridgeSources {
    private static final Map<String, String> plugins = readPlugins();
    private static final Map<String, String> aliases =
            Kt.mapOf(
                    "ce", "craftengine",
                    "ia", "itemsadder",
                    "si", "sxitem",
                    "sx-item", "sxitem",
                    "ni", "neigeitems",
                    "mm", "mythicmobs",
                    "mi", "mmoitems",
                    "hdb", "headdatabase",
                    "cf", "customfishing",
                    "ei", "executableitems",
                    "eb", "executableblocks");

    private static BukkitItemBridge current = null;
    private static List<String> reportedProviders = null;

    private ItemBridgeSources() {}

    public static Map<String, String> plugins() {
        return plugins;
    }

    private static Map<String, String> readPlugins() {
        Properties properties = new Properties();
        try (InputStream input =
                Kt.requireNotNull(
                        ItemBridgeSources.class.getResourceAsStream(
                                "/itembridge-providers.properties"))) {
            properties.load(input);
        } catch (IOException error) {
            throw Kt.sneaky(error);
        }
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return Collections.unmodifiableMap(result);
    }

    public static String provider(String name) {
        String lower = Kt.lower(name);
        String alias = aliases.get(lower);
        String id = alias != null ? alias : lower;
        if (plugins.containsKey(id)) {
            return id;
        }
        return null;
    }

    public static void reset() {
        current = null;
    }

    private static BukkitItemBridge bridge() {
        BukkitItemBridge existing = current;
        if (existing != null) {
            return existing;
        }
        Set<String> connected = new TreeSet<>();
        BukkitItemBridge next =
                BukkitItemBridge.builder()
                        .detectSupportedPlugins(
                                plugin -> connected.add(plugin),
                                (plugin, error) ->
                                        MenuLog.warning(
                                                "ItemBridge 无法接入 "
                                                        + plugin
                                                        + "："
                                                        + error.getClass().getSimpleName()),
                                plugin -> plugin.isEnabled())
                        .build();
        current = next;
        List<String> providers = new ArrayList<>(connected);
        if (!providers.equals(reportedProviders)) {
            reportedProviders = providers;
            MenuLog.info(
                    "ItemBridge：支持 "
                            + plugins.size()
                            + " 种物品源，当前已接入 "
                            + providers.size()
                            + " 种"
                            + (providers.isEmpty() ? "。" : "：" + String.join("、", providers)));
        }
        return next;
    }

    public static Map<String, MenuItemSource> sources() {
        return sources(ItemBridgeSources::bridge);
    }

    /** Candidate validation owns its bridge; it never resets or populates the live cache. */
    static Map<String, MenuItemSource> previewSources() {
        class Preview {
            BukkitItemBridge api;

            BukkitItemBridge get() {
                if (api == null) {
                    api =
                            BukkitItemBridge.builder()
                                    .detectSupportedPlugins(
                                            plugin -> {},
                                            (plugin, error) -> {},
                                            plugin -> plugin.isEnabled())
                                    .build();
                }
                return api;
            }
        }
        Preview preview = new Preview();
        return sources(preview::get);
    }

    private static Map<String, MenuItemSource> sources(
            java.util.function.Supplier<BukkitItemBridge> bridge) {
        Map<String, MenuItemSource> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : plugins.entrySet()) {
            String id = entry.getKey();
            String plugin = entry.getValue();
            result.put(
                    id,
                    new ItemBridgeSource(
                            id,
                            plugin,
                            bridge,
                            () -> Bukkit.getPluginManager().isPluginEnabled(plugin)));
        }
        return result;
    }
}
