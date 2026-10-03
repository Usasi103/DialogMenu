package online.toraka.dialogmenu;

import dev.keystone.Keystone;
import dev.keystone.config.ConfigProblems;
import dev.keystone.event.Events;
import dev.keystone.lang.Lang;
import dev.keystone.task.Tasks;
import dev.keystone.update.UpdateChecker;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry. The start-up order of the TabooLib build, spelled out:
 *
 * <ul>
 *   <li>load: import an old PlayerSettings folder (never deleting it);
 *   <li>enable: refuse to run next to PlayerSettings or after a failed import; lang, listeners,
 *       the resource-pack observer and the command (TabooLib registered these before the old
 *       onEnable body); then the update check, the admin permission, menus and resources;
 *   <li>one tick later: validate item sources once every plugin is enabled;
 *   <li>disable: close dialogs and forget resource state, then remove the channel observers.
 * </ul>
 */
public final class DialogMenu extends JavaPlugin {

    /**
     * GitHub release re-check interval in hours, fixed since 0.1.21: {@code check-interval-hours}
     * in update-check.yml is not read (the file and docs/guides/UPDATE-CHECK.md say so).
     */
    static final long UPDATE_CHECK_HOURS = 6;

    private static volatile DialogMenu instance;

    private Exception migrationFailure;
    private online.toraka.dialogmenu.fullscreen.FullscreenMenus fullscreen;

    public static online.toraka.dialogmenu.fullscreen.FullscreenMenus fullscreen() {
        return instance != null ? instance.fullscreen : null;
    }

    /**
     * The running plugin. Other plugins look it up by name through {@code <main
     * class>.getPluginInstance()} (the entry point TabooLib's generated main class had), so the
     * name and the static form stay.
     */
    public static DialogMenu getPluginInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        Keystone.init(this);
        ConfigProblems.reloadCommand("/dmenu reload");
        ConfigProblems.adminPermission("playersettings.admin");
        try {
            Map<String, byte[]> generatedLanguageFiles = new LinkedHashMap<>();
            for (String name : Kt.listOf("lang/zh_CN.yml", "lang/en_US.yml")) {
                try (InputStream stream =
                        Kt.requireNotNull(getClass().getClassLoader().getResourceAsStream(name))) {
                    generatedLanguageFiles.put(name, stream.readAllBytes());
                }
            }
            if (LegacyDataMigration.migrate(getDataFolder().toPath(), generatedLanguageFiles)) {
                // Lang is first read in onEnable, so the imported lang files are used as they are.
                MenuLog.info("已导入 PlayerSettings 配置，旧目录保留。");
            }
        } catch (Exception error) {
            migrationFailure = error;
        }
    }

    @Override
    public void onEnable() {
        PluginManager manager = getServer().getPluginManager();
        if (manager.getPlugin("PlayerSettings") != null || migrationFailure != null) {
            String reason =
                    migrationFailure != null && migrationFailure.getMessage() != null
                            ? migrationFailure.getMessage()
                            : "请先移除旧 PlayerSettings JAR，避免重复注册菜单。";
            MenuLog.severe("无法启用 DialogMenu：" + reason);
            manager.disablePlugin(this);
            return;
        }
        MenuRuntime.Startup startup = MenuRuntime.prepareStartup(getDataFolder());
        MenuRuntime.applyStartup(startup);
        manager.registerEvents(new DialogMenuListener(), this);
        Events.listenOptional(
                "net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent",
                event -> Tasks.run(() -> ItemSources.changed()));
        Events.listenOptional(
                "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent",
                event -> Tasks.run(() -> MenuImages.itemsAdderReloaded()));
        ResourcePackPackets.start(MenuResources.tracker());
        MenuCommand.register();
        // For the connect action; Paper also maps it to bungeecord:main for Velocity.
        getServer()
                .getMessenger()
                .registerOutgoingPluginChannel(this, ReactionRunner.PROXY_CHANNEL);
        if (dev.keystone.storage.WriteGuard.isBlocked(
                new java.io.File(getDataFolder(), "update-check.yml"))) {
            StartupUpdates.startDefaults(this);
        } else {
            startUpdateChecks(this);
        }
        if (manager.getPermission("playersettings.admin") == null) {
            manager.addPermission(
                    new Permission(
                            "playersettings.admin",
                            "Reload and validate the player menu",
                            PermissionDefault.OP));
        }
        MenuRuntime.initialize(startup);
        if (getServer().getMinecraftVersion().equals("26.3")) {
            fullscreen = new online.toraka.dialogmenu.fullscreen.FullscreenMenus(this);
        } else {
            MenuLog.warning("全屏菜单需要 Paper 26.3；当前仅启用 Dialog 菜单。");
        }
        MenuResources.initializeResources(getDataFolder());
        Tasks.later(1L, () -> ItemSources.changed());
        ConfigProblems.notifyAdmins();
    }

    /**
     * Keystone's update checker (shaded and relocated with the rest of Keystone) with the interval
     * pinned to {@link #UPDATE_CHECK_HOURS}; the start-up delay and switches still come from
     * update-check.yml.
     */
    static void startUpdateChecks(Plugin plugin) {
        UpdateChecker.start(plugin, "Usasi103/DialogMenu", UPDATE_CHECK_HOURS);
    }

    @Override
    public void onDisable() {
        if (fullscreen != null) {
            fullscreen.close();
            fullscreen = null;
        }
        MenuResources.shutdown();
        TemplateDialog.shutdown();
        MenuDialog.shutdown();
        // TabooLib ran @Awake(DISABLE) after onDisable: the channel observers go last.
        ResourcePackPackets.shutdown();
    }
}
