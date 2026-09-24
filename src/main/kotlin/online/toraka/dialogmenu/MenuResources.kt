package online.toraka.dialogmenu

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
) {
    companion object {
        val legacy = MenuResourcePack("服务器资源包", "legacy", "", "", null, "", true)

        fun parse(section: ConfigurationSection?): MenuResourcePack {
            // Old configurations retain their previous gate until explicitly configured.
            if (section == null) return legacy
            val path = "config.yml.ResourcePack"
            val allowed = setOf("Name", "Provider", "Pack", "URL", "UUID", "SHA1", "RequireLoaded")
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
            require(provider in setOf("craftengine", "url", "external")) {
                "$path.Provider: CraftEngine / URL / External"
            }
            val name = text("Name", "DialogMenu 菜单资源")
            require(name.isNotBlank()) { "$path.Name: 不能为空" }
            val pack = text("Pack", "default")
            require(provider != "craftengine" || pack.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) {
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
            return MenuResourcePack(
                name,
                provider,
                pack,
                url,
                id,
                hash,
                section.getBoolean("RequireLoaded", true),
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
    @Volatile private var running = true

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
    }

    fun open(player: Player, action: () -> Unit) {
        val request = UUID.randomUUID()
        requests[player.uniqueId] = request
        val config = current
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
                        current != config ||
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
        val config = current
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
    }
}
