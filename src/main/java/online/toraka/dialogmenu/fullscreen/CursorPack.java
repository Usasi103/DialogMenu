package online.toraka.dialogmenu.fullscreen;

import online.toraka.dialogmenu.MenuRuntime;

import com.sun.net.httpserver.HttpServer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Adds one separately identified pack; never removes the server's other resource packs. */
final class CursorPack implements Listener, AutoCloseable {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final Consumer<Player> closer;
    private final Consumer<Player> opener;
    private final UUID id;
    private final byte[] hash;
    private final String url;
    private final HttpServer server;
    private final ExecutorService executor;
    private final Set<UUID> loaded = new HashSet<>();
    private final Map<UUID, Long> waiting = new HashMap<>();
    private long requestSequence;
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> timeouts = new HashMap<>();

    CursorPack(
            org.bukkit.plugin.java.JavaPlugin plugin,
            Consumer<Player> opener,
            Consumer<Player> closer)
            throws Exception {
        this.plugin = plugin;
        this.closer = closer;
        this.opener = opener;
        byte[] bytes;
        try (var stream =
                java.util.Objects.requireNonNull(
                        plugin.getResource(
                                online.toraka.dialogmenu.BundledResourcePack.RESOURCE))) {
            bytes = stream.readAllBytes();
        }
        var config = MenuRuntime.fullscreenSettings();
        if (config.url().isEmpty())
            throw new IllegalArgumentException("fullscreen.yml.pack-url: 请填写玩家能访问的资源包基础 URL");
        hash = MessageDigest.getInstance("SHA-1").digest(bytes);
        id = UUID.nameUUIDFromBytes(hash);
        String path = "/" + HexFormat.of().formatHex(hash) + ".zip";
        url = config.url().replaceAll("/+$", "") + path;
        server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 16);
        executor =
                Executors.newFixedThreadPool(
                        2, Thread.ofPlatform().daemon().name("fullscreen-pack-", 0).factory());
        server.setExecutor(executor);
        server.createContext(
                path,
                exchange -> {
                    try (exchange) {
                        if (!exchange.getRequestURI().getPath().equals(path)
                                || !exchange.getRequestMethod().equals("GET")) {
                            exchange.sendResponseHeaders(404, -1);
                            return;
                        }
                        exchange.getResponseHeaders().set("Content-Type", "application/zip");
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    }
                });
        server.start();
        plugin.getLogger().info("DialogMenu 统一资源包：" + url + " (" + bytes.length + " bytes)");
    }

    void open(Player player) {
        if (loaded.contains(player.getUniqueId())) {
            opener.accept(player);
            return;
        }
        if (waiting.containsKey(player.getUniqueId())) {
            player.sendMessage("正在等待资源包加载；加载成功后会打开全屏试验。");
            return;
        }
        long request = ++requestSequence;
        waiting.put(player.getUniqueId(), request);
        player.addResourcePack(id, url, hash, "全屏菜单本地光标试验（无需 Mod）", false);
        timeouts.put(
                player.getUniqueId(),
                plugin.getServer()
                        .getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    timeouts.remove(player.getUniqueId());
                                    if (waiting.remove(player.getUniqueId(), request)
                                            && player.isOnline())
                                        player.sendMessage(
                                                "资源包加载超时，可重新 /dfullscreen；旧版对比用 /dfullscreen legacy。");
                                },
                                1200));
    }

    void cancel(Player player) {
        waiting.remove(player.getUniqueId());
        var timeout = timeouts.remove(player.getUniqueId());
        if (timeout != null) timeout.cancel();
    }

    @EventHandler
    public void status(PlayerResourcePackStatusEvent event) {
        if (!id.equals(event.getID())) return;
        UUID playerId = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                var timeout = timeouts.remove(playerId);
                if (timeout != null) timeout.cancel();
                loaded.add(playerId);
                if (waiting.remove(playerId) != null) opener.accept(event.getPlayer());
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                var timeout = timeouts.remove(playerId);
                if (timeout != null) timeout.cancel();
                loaded.remove(playerId);
                closer.accept(event.getPlayer());
                if (waiting.remove(playerId) != null)
                    event.getPlayer()
                            .sendMessage(
                                    "资源包未加载：" + event.getStatus() + "。旧版对比用 /dfullscreen legacy。");
            }
            default -> {}
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
        cancel(event.getPlayer());
    }

    @Override
    public void close() {
        for (Player player : plugin.getServer().getOnlinePlayers()) player.removeResourcePack(id);
        loaded.clear();
        waiting.clear();
        timeouts.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        timeouts.clear();
        server.stop(0);
        executor.shutdownNow();
    }
}
