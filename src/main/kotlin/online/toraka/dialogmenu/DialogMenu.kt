package online.toraka.dialogmenu

import org.bukkit.Bukkit
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import taboolib.common.platform.function.getDataFolder

object DialogMenu : taboolib.common.platform.Plugin() {
    private var migrationFailure: Exception? = null

    override fun onLoad() {
        try {
            val generatedLanguageFiles =
                listOf("lang/zh_CN.yml", "lang/en_US.yml").associateWith {
                    requireNotNull(javaClass.classLoader.getResourceAsStream(it)).use { stream ->
                        stream.readBytes()
                    }
                }
            if (LegacyDataMigration.migrate(getDataFolder().toPath(), generatedLanguageFiles)) {
                taboolib.module.lang.Language.reload()
                MenuLog.info("已导入 PlayerSettings 配置，旧目录保留。")
            }
        } catch (error: Exception) {
            migrationFailure = error
        }
    }

    override fun onEnable() {
        val plugin = taboolib.platform.BukkitPlugin.getInstance()
        if (
            Bukkit.getPluginManager().getPlugin("PlayerSettings") != null ||
                migrationFailure != null
        ) {
            MenuLog.severe(
                "无法启用 DialogMenu：" +
                    (migrationFailure?.message ?: "请先移除旧 PlayerSettings JAR，避免重复注册菜单。")
            )
            Bukkit.getPluginManager().disablePlugin(plugin)
            return
        }
        online.toraka.dialogmenu.updates.UpdateChecker.start(
            plugin,
            "Usasi103/DialogMenu",
        )
        if (Bukkit.getPluginManager().getPermission("playersettings.admin") == null) {
            Bukkit.getPluginManager()
                .addPermission(
                    Permission(
                        "playersettings.admin",
                        "Reload and validate the player menu",
                        PermissionDefault.OP,
                    )
                )
        }
        MenuRuntime.initialize(getDataFolder())
        MenuResources.initializeResources(getDataFolder())
        taboolib.common.platform.function.submit(delay = 1L) { ItemSources.changed() }
    }

    override fun onDisable() {
        MenuResources.shutdown()
        TemplateDialog.shutdown()
        MenuDialog.shutdown()
    }
}
