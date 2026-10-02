package online.toraka.dialogmenu;

import dev.keystone.update.UpdateChecker;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Validate the settings actually used by the checker; the old interval/token keys stay unused. */
final class StartupUpdates {
    private StartupUpdates() {}

    static void validate(String source) {
        YamlConfiguration yaml = MenuConfigParser.yaml(source, "update-check.yml");
        for (String key :
                Kt.listOf("enabled", "include-prereleases", "notify-console", "notify-admins")) {
            Kt.require(
                    !yaml.contains(key) || yaml.isBoolean(key),
                    () -> "update-check.yml." + key + ": 需要 true/false");
        }
        Object delay = yaml.get("startup-delay-seconds");
        Kt.require(
                delay == null
                        || (delay instanceof Integer || delay instanceof Long)
                                && ((Number) delay).longValue() >= 0
                                && ((Number) delay).longValue() <= 3600,
                () -> "update-check.yml.startup-delay-seconds: 需要 0..3600 范围内整数");
    }

    /**
     * TODO(keystone): a prepared-settings start overload. Only backup-failed startup reaches this
     * adapter: use the existing checker with defaults without reading/unblocking the bad file.
     * Network polling, notifications and shutdown remain the common checker's implementation.
     */
    static void startDefaults(Plugin plugin) {
        try {
            Class<?> settings =
                    Arrays.stream(UpdateChecker.class.getDeclaredClasses())
                            .filter(type -> type.getSimpleName().equals("Settings"))
                            .findFirst()
                            .orElseThrow();
            Constructor<?> settingsConstructor =
                    settings.getDeclaredConstructor(
                            boolean.class, long.class, long.class, boolean.class, boolean.class);
            settingsConstructor.setAccessible(true);
            String namespace = UpdateChecker.class.getPackageName();
            Class<?> releases =
                    Class.forName(
                            namespace + ".GitHubReleases",
                            true,
                            UpdateChecker.class.getClassLoader());
            Constructor<?> releaseConstructor =
                    releases.getDeclaredConstructor(String.class, boolean.class);
            releaseConstructor.setAccessible(true);
            String repository = "Usasi103/DialogMenu";
            Constructor<?> constructor =
                    UpdateChecker.class.getDeclaredConstructor(Plugin.class, releases, settings);
            constructor.setAccessible(true);
            Object checker =
                    constructor.newInstance(
                            plugin,
                            releaseConstructor.newInstance(repository, false),
                            settingsConstructor.newInstance(
                                    true, DialogMenu.UPDATE_CHECK_HOURS, 60L, true, true));
            Method poll = UpdateChecker.class.getDeclaredMethod("poll");
            poll.setAccessible(true);
            plugin.getServer().getPluginManager().registerEvents((Listener) checker, plugin);
            long delay = (60L + Math.floorMod(repository.hashCode(), 120)) * 20L;
            var task =
                    plugin.getServer()
                            .getScheduler()
                            .runTaskTimerAsynchronously(
                                    plugin,
                                    () -> {
                                        try {
                                            poll.invoke(checker);
                                        } catch (ReflectiveOperationException error) {
                                            plugin.getLogger()
                                                    .warning("内存默认更新检测调用失败：" + error.getMessage());
                                        }
                                    },
                                    delay,
                                    DialogMenu.UPDATE_CHECK_HOURS * 72000L);
            Field polling = UpdateChecker.class.getDeclaredField("polling");
            polling.setAccessible(true);
            polling.set(checker, task);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("无法启用内存默认更新检测", error);
        }
    }
}
