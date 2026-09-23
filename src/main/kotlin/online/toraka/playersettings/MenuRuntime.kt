package online.toraka.playersettings

import java.io.File
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

object MenuRuntime {
    private val fallback by lazy { MenuRepository.bundled() }
    private var repository: MenuRepository? = null
    val current: MenuDefinition
        get() = repository?.current ?: fallback

    fun initialize(directory: File) {
        val next = MenuRepository(directory)
        repository = next
        try {
            next.initialize()
            Bukkit.getLogger().info("[PlayerSettings] 已加载外置菜单：${next.current.pages.size} 个页面。")
        } catch (error: Exception) {
            Bukkit.getLogger().severe("[PlayerSettings] 配置加载失败，暂用内置默认菜单，原文件未覆盖：${error.message}")
        }
    }

    fun reload(sender: CommandSender, checkOnly: Boolean) {
        if (!sender.hasPermission("playersettings.admin")) {
            sender.sendMessage("你没有权限使用此命令：playersettings.admin")
            return
        }
        val store = requireNotNull(repository) { "菜单尚未初始化" }
        val next =
            try {
                store.readDefinition()
            } catch (error: Exception) {
                sender.sendMessage("PlayerSettings 配置错误，保留原菜单：${error.message}")
                Bukkit.getLogger().warning("[PlayerSettings] 配置校验失败：${error.message}")
                return
            }
        if (checkOnly) {
            sender.sendMessage("PlayerSettings 配置检查通过：${next.pages.size} 个页面，未应用修改。")
            return
        }
        store.install(next)
        SettingsDialog.reloaded()
        sender.sendMessage("PlayerSettings 重载成功：${next.pages.size} 个页面，已刷新打开的菜单。")
    }
}
