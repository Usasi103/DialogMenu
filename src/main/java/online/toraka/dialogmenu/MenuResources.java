package online.toraka.dialogmenu;

import dev.keystone.task.Tasks;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

public final class MenuResources {

    private static MenuResourcePack current = MenuResourcePack.legacy();
    private static final PackLoadTracker TRACKER = new PackLoadTracker();
    private static final Map<UUID, UUID> REQUESTS = new HashMap<>();
    private static BundledResourcePack bundled;
    private static ResourcePackInstallResult installation;
    private static ResourcePackProvider detectedProvider;
    private static volatile boolean running = true;

    private MenuResources() {}

    public static MenuResourcePack current() {
        return current;
    }

    static PackLoadTracker tracker() {
        return TRACKER;
    }

    public static void initializeResources(File directory) {
        try {
            byte[] bytes;
            try (InputStream input =
                    Kt.requireNotNull(
                            MenuResources.class
                                    .getClassLoader()
                                    .getResourceAsStream(BundledResourcePack.RESOURCE),
                            () -> "JAR 缺少内置资源包，请使用完整构建的 DialogMenu JAR")) {
                bytes = input.readAllBytes();
            }
            bundled = new BundledResourcePack(directory.toPath(), bytes);
            prepareResources();
        } catch (Exception error) {
            MenuLog.warning("内置资源包初始化失败：" + error.getMessage());
        }
    }

    private static void prepareResources() {
        BundledResourcePack installer = bundled;
        if (installer == null) {
            return;
        }
        try {
            List<AvailableResourceProvider> available = new ArrayList<>();
            for (ResourcePackProvider provider : ResourcePackProvider.values()) {
                Plugin plugin = Bukkit.getPluginManager().getPlugin(provider.pluginName());
                if (plugin != null && plugin.isEnabled()) {
                    available.add(
                            new AvailableResourceProvider(
                                    provider,
                                    plugin.getDataFolder().toPath().toAbsolutePath().normalize()));
                }
            }
            AvailableResourceProvider selected = BundledResourcePack.select(current, available);
            AvailableResourceProvider detected = BundledResourcePack.detect(current, available);
            detectedProvider = detected != null ? detected.provider() : null;
            AvailableResourceProvider prepared =
                    selected != null
                            ? AvailableResourceProvider.read(
                                    selected.provider(), selected.directory())
                            : null;
            installation =
                    installer.install(
                            current,
                            prepared != null
                                    ? Kt.listOf(prepared)
                                    : Kt.<AvailableResourceProvider>listOf());
            ResourcePackInstallResult result = Kt.requireNotNull(installation);
            AvailableResourceProvider provider = result.provider();
            if (provider != null) {
                MenuLog.info(
                        "资源包接入 "
                                + provider.provider().pluginName()
                                + "："
                                + result.changed()
                                + " 个文件已更新；原有自定义文件保留。");
                if (result.changed() > 0 || !result.conflicts().isEmpty()) {
                    MenuLog.info(provider.provider().instructions());
                }
            } else {
                MenuLog.info("内置资源包导出位置：" + result.exportedZip() + "。");
                if (current.provider().equals("auto")) {
                    if (!current.autoInstall() && detectedProvider != null) {
                        MenuLog.info(
                                "检测到 "
                                        + detectedProvider.pluginName()
                                        + "，AutoInstall: false 已停止自动写入；请自行合并并重新生成、发送。");
                    } else {
                        MenuLog.info(
                                "未检测到受支持的资源包插件。请把 ZIP 合并到 BetterHud 等发送方，或配置 Provider: URL 后使用 /dmenu pack。导出文件不代表已发送。");
                    }
                }
            }
            if (!result.conflicts().isEmpty()) {
                MenuLog.warning("资源包有 " + result.conflicts().size() + " 处冲突，未覆盖用户资源：");
                for (String conflict : result.conflicts()) {
                    MenuLog.warning(conflict);
                }
            }
            if (current.provider().equals("auto")
                    && current.requireLoaded()
                    && effective().provider().equals("auto")) {
                MenuLog.warning(
                        "Auto + RequireLoaded: true 无法确认菜单资源包：请填写发送方实际 UUID，配置可用 CraftEngine 包，或使用 RequireLoaded: false。");
            }
        } catch (Exception error) {
            installation = null;
            MenuLog.warning("内置资源包安装未完成：" + error.getMessage() + "；菜单发送配置保持不变。");
        }
    }

    private static MenuResourcePack effective() {
        if (!current.provider().equals("auto")) {
            return current;
        }
        if (current.uuid() != null) {
            return current.withProvider("external");
        }
        if (detectedProvider == ResourcePackProvider.CRAFT_ENGINE) {
            return current.withProvider("craftengine");
        }
        return current;
    }

    public static void install(MenuResourcePack next) {
        running = true;
        if (!current.equals(next)) {
            REQUESTS.clear();
            if (current.provider().equals("url")
                    && next.provider().equals("url")
                    && Objects.equals(current.uuid(), next.uuid())
                    && (!current.url().equals(next.url()) || !current.sha1().equals(next.sha1()))) {
                TRACKER.invalidate(Kt.requireNotNull(next.uuid()));
            }
            MenuDialog.shutdown();
            TemplateDialog.shutdown();
        }
        current = next;
        prepareResources();
    }

    public static void open(Player player, Runnable action) {
        UUID request = UUID.randomUUID();
        REQUESTS.put(player.getUniqueId(), request);
        MenuResourcePack configured = current;
        MenuResourcePack config = effective();
        if (!config.requireLoaded()) {
            action.run();
            return;
        }
        if (config.provider().equals("legacy")) {
            if (player.getResourcePackStatus()
                    == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                action.run();
            } else {
                player.sendMessage("请先加载" + config.name() + "，再打开菜单。");
            }
            return;
        }
        if (config.provider().equals("auto")) {
            player.sendMessage(
                    "菜单资源包尚未配置加载校验。请联系服主设置发送方的 ResourcePack.UUID，或关闭 RequireLoaded；内置资源包仅导出并未自动发送。");
            return;
        }
        if (!config.provider().equals("craftengine")) {
            if (TRACKER.contains(
                    player.getUniqueId(), Kt.setOf(Kt.requireNotNull(config.uuid())))) {
                action.run();
            } else {
                missing(player, config);
            }
            return;
        }
        craftEngineIds(player, config)
                .whenComplete(
                        (ids, error) -> {
                            if (!running) {
                                return;
                            }
                            Tasks.run(
                                    () -> {
                                        if (!player.isOnline()
                                                || !current.equals(configured)
                                                || !request.equals(
                                                        REQUESTS.get(player.getUniqueId()))
                                                || !player.hasPermission("playersettings.use")) {
                                            return;
                                        }
                                        REQUESTS.remove(player.getUniqueId());
                                        if (error != null) {
                                            player.sendMessage(
                                                    "无法确认"
                                                            + config.name()
                                                            + "：请检查 CraftEngine 资源包 "
                                                            + config.pack()
                                                            + " 的配置和托管状态。");
                                            MenuLog.warning(
                                                    "CraftEngine 包 "
                                                            + config.pack()
                                                            + " 无法解析："
                                                            + error.getClass().getSimpleName());
                                        } else if (TRACKER.contains(player.getUniqueId(), ids)) {
                                            action.run();
                                        } else {
                                            missing(player, config);
                                        }
                                    });
                        });
    }

    private static void missing(Player player, MenuResourcePack config) {
        player.sendMessage(
                "请先加载指定资源包“"
                        + config.name()
                        + "”，再打开菜单。"
                        + (config.provider().equals("url") ? "可使用 /dmenu pack 下载。" : ""));
    }

    public static void send(Player player) {
        MenuResourcePack config = effective();
        if (config.provider().equals("auto")) {
            ResourcePackInstallResult result = installation;
            if (result == null || !Files.isRegularFile(result.exportedZip())) {
                player.sendMessage("菜单资源导出或安装尚未完成，请服主检查控制台错误；/dmenu pack 未发送资源包。");
                return;
            }
            ResourcePackProvider provider =
                    installation != null && installation.provider() != null
                            ? installation.provider().provider()
                            : null;
            player.sendMessage(
                    provider == null
                            ? "菜单资源已导出到 plugins/DialogMenu/resourcepack/DialogMenu-resourcepack.zip。请由服主合并并发送，或配置 URL 直链；/dmenu pack 尚未发送资源包。"
                            : "菜单资源已接入 "
                                    + provider.pluginName()
                                    + "。"
                                    + provider.instructions()
                                    + "资源包须由该插件发送。");
            return;
        }
        if (!config.provider().equals("url")) {
            player.sendMessage(
                    "所需资源包："
                            + config.name()
                            + "；来源 "
                            + config.provider()
                            + (config.provider().equals("craftengine")
                                    ? "，包 ID：" + config.pack() + "。由 CraftEngine 发送。"
                                    : "。由配置的发送方提供。"));
            return;
        }
        byte[] bytes = null;
        if (!config.sha1().isEmpty()) {
            // Kotlin chunked(2): every pair of hex digits is one byte.
            String hex = config.sha1();
            bytes = new byte[(hex.length() + 1) / 2];
            for (int i = 0; i < bytes.length; i++) {
                int end = Math.min(hex.length(), i * 2 + 2);
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, end), 16);
            }
        }
        player.addResourcePack(
                Kt.requireNotNull(config.uuid()), config.url(), bytes, config.name(), false);
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<Set<UUID>> craftEngineIds(
            Player player, MenuResourcePack config) {
        try {
            Plugin plugin = Kt.requireNotNull(Bukkit.getPluginManager().getPlugin("CraftEngine"));
            Kt.require(plugin.isEnabled());
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> engineClass =
                    loader.loadClass("net.momirealms.craftengine.core.plugin.CraftEngine");
            Object engine = engineClass.getMethod("instance").invoke(null);
            Object manager = engineClass.getMethod("packManager").invoke(engine);
            Class<?> managerClass =
                    loader.loadClass("net.momirealms.craftengine.core.pack.PackManager");
            Map<?, ?> hosts =
                    (Map<?, ?>) managerClass.getMethod("resourcePackHosts").invoke(manager);
            Object host = Kt.requireNotNull(hosts.get(config.pack()));
            Object network = engineClass.getMethod("networkManager").invoke(engine);
            Class<?> networkClass =
                    loader.loadClass(
                            "net.momirealms.craftengine.core.plugin.network.NetworkManager");
            Object user =
                    Kt.requireNotNull(
                            networkClass
                                    .getMethod("getOnlineUser", UUID.class)
                                    .invoke(network, player.getUniqueId()));
            Class<?> userClass =
                    loader.loadClass("net.momirealms.craftengine.core.plugin.network.NetWorkUser");
            Class<?> hostClass =
                    loader.loadClass("net.momirealms.craftengine.core.pack.host.ResourcePackHost");
            Class<?> dataClass =
                    loader.loadClass(
                            "net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData");
            CompletableFuture<Object> future =
                    (CompletableFuture<Object>)
                            hostClass
                                    .getMethod("requestResourcePackDownloadLink", userClass)
                                    .invoke(host, user);
            Method uuid = dataClass.getMethod("uuid");
            return future.orTimeout(10, TimeUnit.SECONDS)
                    .thenApply(
                            data -> {
                                Set<UUID> ids = new LinkedHashSet<>();
                                for (Object entry : (List<?>) data) {
                                    try {
                                        ids.add((UUID) uuid.invoke(entry));
                                    } catch (ReflectiveOperationException error) {
                                        throw Kt.sneaky(error);
                                    }
                                }
                                Kt.require(!ids.isEmpty());
                                return ids;
                            });
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        } catch (LinkageError error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    static void status(PlayerResourcePackStatusEvent event) {
        TRACKER.record(
                event.getPlayer().getUniqueId(),
                event.getID(),
                event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
    }

    static void quit(PlayerQuitEvent event) {
        TRACKER.forget(event.getPlayer().getUniqueId());
        REQUESTS.remove(event.getPlayer().getUniqueId());
    }

    public static void shutdown() {
        running = false;
        REQUESTS.clear();
        TRACKER.clear();
        bundled = null;
        installation = null;
        detectedProvider = null;
    }
}
