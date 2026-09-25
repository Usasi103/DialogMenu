package online.toraka.dialogmenu

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerResourcePackStatusEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

data class MenuResourcePack(
    val name: String,
    val provider: String,
    val pack: String,
    val url: String,
    val uuid: UUID?,
    val sha1: String,
    val requireLoaded: Boolean,
    val autoInstall: Boolean = provider in setOf("auto", "craftengine"),
) {
    companion object {
        val legacy = MenuResourcePack("服务器资源包", "legacy", "", "", null, "", true)

        fun parse(section: ConfigurationSection?): MenuResourcePack {
            // Old configurations retain their previous gate until explicitly configured.
            if (section == null) return legacy
            val path = "config.yml.ResourcePack"
            val allowed =
                setOf(
                    "Name",
                    "Provider",
                    "Pack",
                    "URL",
                    "UUID",
                    "SHA1",
                    "RequireLoaded",
                    "AutoInstall",
                )
            require((section.getKeys(false) - allowed).isEmpty()) {
                "$path: 未知字段 ${section.getKeys(false) - allowed}"
            }
            fun text(key: String, default: String = ""): String {
                val value = section.get(key) ?: default
                require(
                    value is String && value.none(Char::isISOControl) && value == value.trim()
                ) {
                    "$path.$key: 需要单行字符串"
                }
                return value
            }
            val provider = text("Provider", "CraftEngine").lowercase()
            require(provider in setOf("auto", "craftengine", "url", "external")) {
                "$path.Provider: Auto / CraftEngine / URL / External"
            }
            val name = text("Name", "DialogMenu 菜单资源")
            require(name.isNotBlank()) { "$path.Name: 不能为空" }
            val pack = text("Pack", "default")
            require(
                provider !in setOf("auto", "craftengine") ||
                    pack.matches(Regex("[a-zA-Z0-9_-]{1,64}"))
            ) {
                "$path.Pack: 填写 CraftEngine 的包 ID"
            }
            val url = text("URL")
            if (url.isNotEmpty()) {
                val uri = runCatching { URI(url) }.getOrNull()
                require(
                    uri != null &&
                        uri.scheme in setOf("http", "https") &&
                        !uri.host.isNullOrEmpty() &&
                        uri.userInfo == null
                ) {
                    "$path.URL: 需要 http/https 资源包直链"
                }
            }
            require(provider != "url" || url.isNotEmpty()) { "$path.URL: URL 模式必须指定资源包直链" }
            val rawId = text("UUID")
            val id =
                if (rawId.isNotEmpty()) {
                    require(
                        rawId.matches(
                            Regex(
                                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                            )
                        )
                    ) {
                        "$path.UUID: 无效 UUID"
                    }
                    UUID.fromString(rawId)
                } else if (provider == "url")
                    UUID.nameUUIDFromBytes(url.toByteArray(StandardCharsets.UTF_8))
                else null
            require(provider != "external" || id != null) {
                "$path.UUID: External 模式必须填写发送方使用的包 UUID"
            }
            val hash = text("SHA1").lowercase()
            require(hash.isEmpty() || hash.matches(Regex("[0-9a-f]{40}"))) {
                "$path.SHA1: 留空或填写 40 位 SHA-1"
            }
            require(!section.contains("RequireLoaded") || section.isBoolean("RequireLoaded")) {
                "$path.RequireLoaded: true/false"
            }
            require(!section.contains("AutoInstall") || section.isBoolean("AutoInstall")) {
                "$path.AutoInstall: true/false"
            }
            return MenuResourcePack(
                name,
                provider,
                pack,
                url,
                id,
                hash,
                section.getBoolean("RequireLoaded", true),
                section.getBoolean("AutoInstall", provider in setOf("auto", "craftengine")),
            )
        }
    }
}

/** A successful response for a different pack must never unlock the menu. */
class PackLoadTracker {
    private val loaded = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    fun record(player: UUID, pack: UUID, success: Boolean) {
        if (success) loaded.getOrPut(player) { ConcurrentHashMap.newKeySet() }.add(pack)
        else loaded[player]?.remove(pack)
    }

    fun contains(player: UUID, required: Set<UUID>) =
        required.isNotEmpty() && loaded[player].orEmpty().containsAll(required)

    fun forget(player: UUID) {
        loaded.remove(player)
    }

    fun clear() {
        loaded.clear()
    }

    fun invalidate(pack: UUID) {
        loaded.values.forEach { it.remove(pack) }
    }
}

object MenuResources {
    var current = MenuResourcePack.legacy
        private set

    internal val tracker = PackLoadTracker()
    private val requests = mutableMapOf<UUID, UUID>()
    private var bundled: BundledResourcePack? = null
    private var installation: ResourcePackInstallResult? = null
    private var detectedProvider: ResourcePackProvider? = null
    @Volatile private var running = true

    fun initializeResources(directory: File) {
        try {
            val bytes =
                requireNotNull(
                        javaClass.classLoader.getResourceAsStream(BundledResourcePack.RESOURCE)
                    ) {
                        "JAR 缺少内置资源包，请使用完整构建的 DialogMenu JAR"
                    }
                    .use { it.readBytes() }
            bundled = BundledResourcePack(directory.toPath(), bytes)
            prepareResources()
        } catch (error: Exception) {
            MenuLog.warning("内置资源包初始化失败：${error.message}")
        }
    }

    private fun prepareResources() {
        val installer = bundled ?: return
        try {
            val available =
                ResourcePackProvider.entries.mapNotNull { provider ->
                    Bukkit.getPluginManager()
                        .getPlugin(provider.pluginName)
                        ?.takeIf { it.isEnabled }
                        ?.let {
                            AvailableResourceProvider(
                                provider,
                                it.dataFolder.toPath().toAbsolutePath().normalize(),
                            )
                        }
                }
            val selected = BundledResourcePack.select(current, available)
            detectedProvider = BundledResourcePack.detect(current, available)?.provider
            val prepared = selected?.let {
                AvailableResourceProvider.read(it.provider, it.directory)
            }
            installation = installer.install(current, listOfNotNull(prepared))
            val result = requireNotNull(installation)
            val provider = result.provider
            if (provider != null) {
                MenuLog.info(
                    "资源包接入 ${provider.provider.pluginName}：${result.changed} 个文件已更新；原有自定义文件保留。"
                )
                if (result.changed > 0 || result.conflicts.isNotEmpty())
                    MenuLog.info(provider.provider.instructions())
            } else {
                MenuLog.info("内置资源包导出位置：${result.exportedZip}。")
                if (current.provider == "auto") {
                    if (!current.autoInstall && detectedProvider != null) {
                        MenuLog.info(
                            "检测到 ${detectedProvider!!.pluginName}，AutoInstall: false 已停止自动写入；请自行合并并重新生成、发送。"
                        )
                    } else {
                        MenuLog.info(
                            "未检测到受支持的资源包插件。请把 ZIP 合并到 BetterHud 等发送方，或配置 Provider: URL 后使用 /dmenu pack。导出文件不代表已发送。"
                        )
                    }
                }
            }
            if (result.conflicts.isNotEmpty()) {
                MenuLog.warning("资源包有 ${result.conflicts.size} 处冲突，未覆盖用户资源：")
                result.conflicts.forEach { MenuLog.warning(it) }
            }
            if (
                current.provider == "auto" &&
                    current.requireLoaded &&
                    effective().provider == "auto"
            ) {
                MenuLog.warning(
                    "Auto + RequireLoaded: true 无法确认菜单资源包：请填写发送方实际 UUID，配置可用 CraftEngine 包，或使用 RequireLoaded: false。"
                )
            }
        } catch (error: Exception) {
            installation = null
            MenuLog.warning("内置资源包安装未完成：${error.message}；菜单发送配置保持不变。")
        }
    }

    private fun effective(): MenuResourcePack {
        if (current.provider != "auto") return current
        if (current.uuid != null) return current.copy(provider = "external")
        if (detectedProvider == ResourcePackProvider.CRAFT_ENGINE) {
            return current.copy(provider = "craftengine")
        }
        return current
    }

    fun install(next: MenuResourcePack) {
        running = true
        if (current != next) {
            requests.clear()
            if (
                current.provider == "url" &&
                    next.provider == "url" &&
                    current.uuid == next.uuid &&
                    (current.url != next.url || current.sha1 != next.sha1)
            ) {
                tracker.invalidate(requireNotNull(next.uuid))
            }
            MenuDialog.shutdown()
            TemplateDialog.shutdown()
        }
        current = next
        prepareResources()
    }

    fun open(player: Player, action: () -> Unit) {
        val request = UUID.randomUUID()
        requests[player.uniqueId] = request
        val configured = current
        val config = effective()
        if (!config.requireLoaded) {
            action()
            return
        }
        if (config.provider == "legacy") {
            if (
                player.resourcePackStatus ==
                    PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED
            )
                action()
            else player.sendMessage("请先加载${config.name}，再打开菜单。")
            return
        }
        if (config.provider == "auto") {
            player.sendMessage(
                "菜单资源包尚未配置加载校验。请联系服主设置发送方的 ResourcePack.UUID，或关闭 RequireLoaded；内置资源包仅导出并未自动发送。"
            )
            return
        }
        if (config.provider != "craftengine") {
            if (tracker.contains(player.uniqueId, setOf(requireNotNull(config.uuid)))) action()
            else missing(player, config)
            return
        }
        craftEngineIds(player, config).whenComplete { ids, error ->
            if (!running) return@whenComplete
            submit {
                if (
                    !player.isOnline ||
                        current != configured ||
                        requests[player.uniqueId] != request ||
                        !player.hasPermission("playersettings.use")
                )
                    return@submit
                requests.remove(player.uniqueId)
                if (error != null) {
                    player.sendMessage(
                        "无法确认${config.name}：请检查 CraftEngine 资源包 ${config.pack} 的配置和托管状态。"
                    )
                    MenuLog.warning(
                        "CraftEngine 包 ${config.pack} 无法解析：${error.javaClass.simpleName}"
                    )
                } else if (tracker.contains(player.uniqueId, ids)) action()
                else missing(player, config)
            }
        }
    }

    private fun missing(player: Player, config: MenuResourcePack) {
        player.sendMessage(
            "请先加载指定资源包“${config.name}”，再打开菜单。" +
                if (config.provider == "url") "可使用 /dmenu pack 下载。" else ""
        )
    }

    fun send(player: Player) {
        val config = effective()
        if (config.provider == "auto") {
            val result = installation
            if (result == null || !java.nio.file.Files.isRegularFile(result.exportedZip)) {
                player.sendMessage("菜单资源导出或安装尚未完成，请服主检查控制台错误；/dmenu pack 未发送资源包。")
                return
            }
            val provider = installation?.provider?.provider
            player.sendMessage(
                if (provider == null)
                    "菜单资源已导出到 plugins/DialogMenu/resourcepack/DialogMenu-resourcepack.zip。请由服主合并并发送，或配置 URL 直链；/dmenu pack 尚未发送资源包。"
                else "菜单资源已接入 ${provider.pluginName}。${provider.instructions()}资源包须由该插件发送。"
            )
            return
        }
        if (config.provider != "url") {
            player.sendMessage(
                "所需资源包：${config.name}；来源 ${config.provider}" +
                    if (config.provider == "craftengine") "，包 ID：${config.pack}。由 CraftEngine 发送。"
                    else "。由配置的发送方提供。"
            )
            return
        }
        val bytes =
            if (config.sha1.isEmpty()) null
            else config.sha1.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        player.addResourcePack(requireNotNull(config.uuid), config.url, bytes, config.name, false)
    }

    private fun craftEngineIds(
        player: Player,
        config: MenuResourcePack,
    ): CompletableFuture<Set<UUID>> =
        try {
            val plugin = requireNotNull(Bukkit.getPluginManager().getPlugin("CraftEngine"))
            require(plugin.isEnabled)
            val loader = plugin.javaClass.classLoader
            val engineClass = loader.loadClass("net.momirealms.craftengine.core.plugin.CraftEngine")
            val engine = engineClass.getMethod("instance").invoke(null)
            val manager = engineClass.getMethod("packManager").invoke(engine)
            val managerClass = loader.loadClass("net.momirealms.craftengine.core.pack.PackManager")
            val hosts = managerClass.getMethod("resourcePackHosts").invoke(manager) as Map<*, *>
            val host = requireNotNull(hosts[config.pack])
            val network = engineClass.getMethod("networkManager").invoke(engine)
            val networkClass =
                loader.loadClass("net.momirealms.craftengine.core.plugin.network.NetworkManager")
            val user =
                requireNotNull(
                    networkClass
                        .getMethod("getOnlineUser", UUID::class.java)
                        .invoke(network, player.uniqueId)
                )
            val userClass =
                loader.loadClass("net.momirealms.craftengine.core.plugin.network.NetWorkUser")
            val hostClass =
                loader.loadClass("net.momirealms.craftengine.core.pack.host.ResourcePackHost")
            val dataClass =
                loader.loadClass(
                    "net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData"
                )
            val future =
                hostClass.getMethod("requestResourcePackDownloadLink", userClass).invoke(host, user)
                    as CompletableFuture<*>
            future.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).thenApply { data ->
                (data as List<*>)
                    .map { dataClass.getMethod("uuid").invoke(it) as UUID }
                    .toSet()
                    .also { require(it.isNotEmpty()) }
            }
        } catch (error: Exception) {
            CompletableFuture.failedFuture(error)
        } catch (error: LinkageError) {
            CompletableFuture.failedFuture(error)
        }

    @SubscribeEvent
    fun status(event: PlayerResourcePackStatusEvent) {
        tracker.record(
            event.player.uniqueId,
            event.id,
            event.status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED,
        )
    }

    @SubscribeEvent
    fun quit(event: PlayerQuitEvent) {
        tracker.forget(event.player.uniqueId)
        requests.remove(event.player.uniqueId)
    }

    fun shutdown() {
        running = false
        requests.clear()
        tracker.clear()
        bundled = null
        installation = null
        detectedProvider = null
    }
}
