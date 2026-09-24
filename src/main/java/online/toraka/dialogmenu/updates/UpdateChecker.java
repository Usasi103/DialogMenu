package online.toraka.dialogmenu.updates;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Each plugin owns its checker; network I/O is asynchronous and player access is synchronous. */
public final class UpdateChecker implements Listener {
    public static final String NOTIFY_PERMISSION = "toraka.update.notify";
    private static final long CHECK_INTERVAL_HOURS = 6;

    record Settings(
            boolean enabled,
            long intervalHours,
            long startupSeconds,
            boolean console,
            boolean admins) {}

    private final Plugin plugin;
    private final GitHubReleases releases;
    private final Settings settings;
    private final String current;
    private final Set<UUID> notified = new HashSet<>();
    private volatile boolean closed;
    private long retryAt;
    private BukkitTask polling;
    private GitHubReleases.Release available;
    private String lastConsoleTag;
    private String lastError;

    UpdateChecker(Plugin plugin, GitHubReleases releases, Settings settings) {
        this.plugin = plugin;
        this.releases = releases;
        this.settings = settings;
        this.current = plugin.getDescription().getVersion();
    }

    public static void start(Plugin plugin, String repository) {
        try {
            File file = new File(plugin.getDataFolder(), "update-check.yml");
            if (!file.isFile()) {
                plugin.saveResource("update-check.yml", false);
            }
            YamlConfiguration config = new YamlConfiguration();
            config.load(file);
            Settings settings =
                    new Settings(
                            config.getBoolean("enabled", true),
                            CHECK_INTERVAL_HOURS,
                            Math.max(
                                    0, Math.min(3600, config.getLong("startup-delay-seconds", 60))),
                            config.getBoolean("notify-console", true),
                            config.getBoolean("notify-admins", true));
            if (!settings.enabled()) {
                return;
            }
            UpdateChecker checker =
                    new UpdateChecker(
                            plugin,
                            new GitHubReleases(
                                    repository, config.getBoolean("include-prereleases", false)),
                            settings);
            plugin.getServer().getPluginManager().registerEvents(checker, plugin);
            long delay = settings.startupSeconds() + Math.floorMod(repository.hashCode(), 120);
            checker.polling =
                    plugin.getServer()
                            .getScheduler()
                            .runTaskTimerAsynchronously(
                                    plugin,
                                    checker::poll,
                                    delay * 20,
                                    settings.intervalHours() * 72000);
        } catch (Exception error) {
            plugin.getLogger().warning("更新检测未启动：请检查 update-check.yml 的格式和文件权限。");
        }
    }

    void poll() {
        if (closed || System.currentTimeMillis() < retryAt) {
            return;
        }
        GitHubReleases.Result result = releases.check();
        retryAt = System.currentTimeMillis() + result.retryAfterSeconds() * 1000;
        if (!closed && plugin.isEnabled()) {
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> accept(result));
            } catch (IllegalStateException ignored) {
                // The plugin may have been disabled between the check and scheduling.
            }
        }
    }

    void accept(GitHubReleases.Result result) {
        if (closed || !plugin.isEnabled()) {
            return;
        }
        if (result.error() != null) {
            if (!result.error().equals(lastError)) {
                plugin.getLogger().warning("更新检测暂不可用：" + result.error() + "。后续将自动重试。");
                lastError = result.error();
            }
            return;
        }
        lastError = null;
        if (result.release() == null) {
            available = null;
            notified.clear();
            lastConsoleTag = null;
            return;
        }
        var localVersion = ReleaseVersion.parse(current);
        var latestVersion = ReleaseVersion.parse(result.release().tag());
        if (localVersion.isEmpty() || latestVersion.isEmpty()) {
            plugin.getLogger().warning("更新检测跳过：当前版本或 Release 标签无法比较。");
            return;
        }
        if (latestVersion.get().compareTo(localVersion.get()) <= 0) {
            available = null;
            notified.clear();
            return;
        }
        if (available == null || !available.tag().equals(result.release().tag())) {
            notified.clear();
        }
        available = result.release();
        if (settings.console() && !available.tag().equals(lastConsoleTag)) {
            plugin.getLogger().warning(message());
            plugin.getLogger().warning("Release：" + available.url());
            lastConsoleTag = available.tag();
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            notifyPlayer(player);
        }
    }

    private String message() {
        return "发现新版本：当前 " + current + " → 最新 " + available.tag() + "。";
    }

    private void notifyPlayer(Player player) {
        if (closed
                || available == null
                || !settings.admins()
                || !player.isOnline()
                || !(player.isOp() || player.hasPermission(NOTIFY_PERMISSION))
                || !notified.add(player.getUniqueId())) {
            return;
        }
        player.sendMessage("§e[" + plugin.getName() + "] " + message());
        player.sendMessage("§7Release：§b" + available.url());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer()
                .getScheduler()
                .runTaskLater(plugin, () -> notifyPlayer(event.getPlayer()), 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        notified.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            closed = true;
            if (polling != null) {
                polling.cancel();
            }
            notified.clear();
            available = null;
            HandlerList.unregisterAll(this);
        }
    }
}
