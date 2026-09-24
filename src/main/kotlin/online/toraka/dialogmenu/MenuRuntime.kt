package online.toraka.dialogmenu

import java.io.File
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

object MenuRuntime {
    private val fallback by lazy { MenuRepository.bundled() }
    private var repository: MenuRepository? = null
    private var templateRepository: TemplateRepository? = null
    val templates: Map<String, DialogTemplate>
        get() = templateRepository?.current.orEmpty()

    val current: MenuDefinition
        get() = repository?.current ?: fallback

    fun initialize(directory: File) {
        val next = MenuRepository(directory)
        val nextTemplates = TemplateRepository(directory)
        repository = next
        templateRepository = nextTemplates
        try {
            next.initialize()
            nextTemplates.initialize()
            Bukkit.getLogger().info("[DialogMenu] 对话模板：" + nextTemplates.current.size + " 个。")
            Bukkit.getLogger().info("[DialogMenu] 已加载外置菜单：${next.current.pages.size} 个页面。")
        } catch (error: Exception) {
            Bukkit.getLogger().severe("[DialogMenu] 配置加载失败，暂用内置默认菜单，原文件未覆盖：${error.message}")
        }
    }

    fun reload(sender: CommandSender, checkOnly: Boolean) {
        if (!sender.hasPermission("playersettings.admin")) {
            sender.sendMessage("你没有权限使用此命令：playersettings.admin")
            return
        }
        val store = requireNotNull(repository) { "菜单尚未初始化" }
        ItemBridgeSources.reset()
        val templateStore = requireNotNull(templateRepository)
        val nextTemplates: Map<String, DialogTemplate>
        val next =
            try {
                nextTemplates = templateStore.read()
                store.readDefinition().also { definition ->
                    ItemSources.validate(definition).forEach {
                        sender.sendMessage("DialogMenu 警告：$it")
                    }
                }
            } catch (error: Exception) {
                sender.sendMessage("DialogMenu 配置错误，保留原菜单：${error.message}")
                Bukkit.getLogger().warning("[DialogMenu] 配置校验失败：${error.message}")
                return
            }
        if (checkOnly) {
            sender.sendMessage("DialogMenu 对话模板检查通过：" + nextTemplates.size + " 个。")
            sender.sendMessage("DialogMenu 配置检查通过：${next.pages.size} 个页面，未应用修改。")
            return
        }
        store.install(next)
        templateStore.install(nextTemplates)
        MenuDialog.reloaded()
        TemplateDialog.reloaded()
        sender.sendMessage("DialogMenu 对话模板已刷新：" + nextTemplates.size + " 个。")
        sender.sendMessage("DialogMenu 重载成功：${next.pages.size} 个页面，已刷新打开的菜单。")
    }
}
