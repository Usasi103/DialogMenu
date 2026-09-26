package online.toraka.dialogmenu

import java.io.File
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object MenuRuntime {
    private val fallback by lazy { MenuRepository.bundled() }
    private var repository: MenuRepository? = null
    private var templateRepository: TemplateRepository? = null
    private var catalogRepository: CatalogRepository? = null
    private lateinit var directory: File
    var translations: MenuTranslations = MenuTranslations()
        private set

    private val catalog: MenuCatalog?
        get() = catalogRepository?.current

    val templates: Map<String, DialogTemplate>
        get() = catalog?.templates ?: templateRepository?.current.orEmpty()

    val current: MenuDefinition
        get() =
            catalog?.let {
                it.menus[it.defaultMenu]?.settings
                    ?: it.menus.values.firstNotNullOfOrNull { menu -> menu.settings }
            } ?: repository?.current ?: fallback

    val definitions: List<MenuDefinition>
        get() = catalog?.menus?.values?.mapNotNull { it.settings } ?: listOf(current)

    val menuIds: List<String>
        get() = catalog?.menus?.keys?.toList() ?: listOf("settings") + templates.keys

    fun settings(id: String): MenuDefinition? =
        catalog?.menus?.get(id)?.settings
            ?: if (catalog == null && id == "settings") current else null

    fun pages(id: String): List<String> =
        catalog?.menus?.get(id)?.pages?.toList()
            ?: if (id == "settings") current.pages.keys.toList() else emptyList()

    fun resolveTemplate(id: String): String =
        if (id in templates) id
        else
            when (id) {
                "npc-dialogue" -> "demo-dialogue/main"
                "boss-intro" -> "demo-boss/intro"
                "boss-confirm" -> "demo-boss/confirm"
                else -> id
            }

    fun open(player: Player, id: String? = null, page: String? = null) {
        val requested = id ?: catalog?.defaultMenu ?: "settings"
        val menu = catalog?.menus?.get(requested)
        when {
            menu?.settings != null -> MenuDialog.open(player, page ?: menu.defaultPage, requested)
            menu != null -> TemplateDialog.open(player, "$requested/${page ?: menu.defaultPage}")
            requested == "settings" && catalog == null ->
                MenuDialog.open(player, page ?: current.defaultPage)
            page == null && requested in current.pages ->
                MenuDialog.open(
                    player,
                    requested,
                    catalog?.menus?.values?.firstOrNull { it.settings === current }?.id
                        ?: "settings",
                )
            page == null && resolveTemplate(requested) in templates ->
                TemplateDialog.open(player, requested)
            else ->
                player.sendMessage(
                    "DialogMenu：菜单或页面不存在 $requested${page?.let { "/$it" }.orEmpty()}"
                )
        }
    }

    fun initialize(directory: File) {
        this.directory = directory
        val next = MenuRepository(directory)
        val nextTemplates = TemplateRepository(directory)
        repository = next
        templateRepository = nextTemplates
        try {
            MenuTranslations.initialize(directory)
            translations = MenuTranslations.read(directory)
            CatalogRepository.exportIfNew(directory)
            if (CatalogRepository.selected(directory)) {
                val store = CatalogRepository(directory)
                store.install(store.read())
                MenuResources.install(store.current!!.resourcePack)
                catalogRepository = store
                for (name in listOf("配置说明.md", "任务菜单说明.md", "examples/items.yml")) {
                    val file = File(directory, name)
                    if (!file.exists()) {
                        file.parentFile.mkdirs()
                        file.writeText(MenuRepository.resource(name), Charsets.UTF_8)
                    }
                }
                MenuLog.loaded("已加载 ${store.current!!.menus.size} 个菜单（一个文件一个菜单）。")
                return
            }
            next.initialize()
            nextTemplates.initialize()
            MenuLog.loaded("对话模板：" + nextTemplates.current.size + " 个。")
            MenuLog.loaded("已加载外置菜单：${next.current.pages.size} 个页面。")
        } catch (error: Exception) {
            MenuLog.severe("配置加载失败，暂用内置默认菜单，原文件未覆盖：${error.message}")
        }
    }

    fun reload(sender: CommandSender, checkOnly: Boolean) {
        if (!sender.hasPermission("playersettings.admin")) {
            MenuLog.reply(sender, "你没有权限使用此命令：playersettings.admin")
            return
        }
        val store = requireNotNull(repository) { "菜单尚未初始化" }
        ItemBridgeSources.reset()
        val nextTranslations =
            try {
                MenuTranslations.read(directory)
            } catch (error: Exception) {
                MenuLog.reply(sender, "DialogMenu 翻译配置错误，保留原菜单：${error.message}")
                return
            }
        try {
            if (CatalogRepository.selected(directory)) {
                val nextStore = catalogRepository ?: CatalogRepository(directory)
                val next = nextStore.read()
                next.menus.values
                    .mapNotNull { it.settings }
                    .flatMap { ItemSources.validate(it) }
                    .forEach {
                        MenuLog.reply(sender, "DialogMenu 警告：$it")
                    }
                if (checkOnly) {
                    MenuLog.reply(sender, "DialogMenu 配置检查通过：${next.menus.size} 个菜单，未应用修改。")
                    return
                }
                nextStore.install(next)
                translations = nextTranslations
                MenuImages.reset()
                MenuResources.install(next.resourcePack)
                catalogRepository = nextStore
                MenuDialog.reloaded()
                TemplateDialog.reloaded()
                MenuLog.reply(sender, "DialogMenu 重载成功：${next.menus.size} 个菜单，已刷新打开的菜单。")
                return
            }
        } catch (error: Exception) {
            MenuLog.reply(sender, "DialogMenu 配置错误，保留原菜单：${error.message}")
            MenuLog.warning("配置校验失败：${error.message}")
            return
        }
        val templateStore = requireNotNull(templateRepository)
        val nextTemplates: Map<String, DialogTemplate>
        val next =
            try {
                nextTemplates = templateStore.read()
                store.readDefinition().also { definition ->
                    ItemSources.validate(definition).forEach {
                        MenuLog.reply(sender, "DialogMenu 警告：$it")
                    }
                }
            } catch (error: Exception) {
                MenuLog.reply(sender, "DialogMenu 配置错误，保留原菜单：${error.message}")
                MenuLog.warning("配置校验失败：${error.message}")
                return
            }
        if (checkOnly) {
            MenuLog.reply(sender, "DialogMenu 对话模板检查通过：" + nextTemplates.size + " 个。")
            MenuLog.reply(sender, "DialogMenu 配置检查通过：${next.pages.size} 个页面，未应用修改。")
            return
        }
        store.install(next)
        translations = nextTranslations
        MenuImages.reset()
        templateStore.install(nextTemplates)
        catalogRepository = null
        MenuResources.install(MenuResourcePack.legacy)
        MenuDialog.reloaded()
        TemplateDialog.reloaded()
        MenuLog.reply(sender, "DialogMenu 对话模板已刷新：" + nextTemplates.size + " 个。")
        MenuLog.reply(sender, "DialogMenu 重载成功：${next.pages.size} 个页面，已刷新打开的菜单。")
    }
}
