package online.toraka.dialogmenu

import cn.gtemc.itembridge.api.context.BuildContext
import cn.gtemc.itembridge.core.BukkitItemBridge
import java.util.Locale
import java.util.Properties
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Metadata shared with the build's soft-dependency list; provider classes stay lazily loaded. */
object ItemBridgeSources {
    val plugins: Map<String, String> =
        Properties()
            .apply {
                requireNotNull(
                        ItemBridgeSources::class
                            .java
                            .getResourceAsStream("/itembridge-providers.properties")
                    )
                    .use { load(it) }
            }
            .entries
            .associate { it.key.toString() to it.value.toString() }
            .toSortedMap()
    private val aliases =
        mapOf(
            "ce" to "craftengine",
            "ia" to "itemsadder",
            "si" to "sxitem",
            "sx-item" to "sxitem",
            "ni" to "neigeitems",
            "mm" to "mythicmobs",
            "mi" to "mmoitems",
            "hdb" to "headdatabase",
            "cf" to "customfishing",
            "ei" to "executableitems",
            "eb" to "executableblocks",
        )

    fun provider(name: String): String? {
        val lower = name.lowercase(Locale.ROOT)
        return (aliases[lower] ?: lower).takeIf { it in plugins }
    }

    private var current: BukkitItemBridge? = null
    private var reportedProviders: List<String>? = null

    fun reset() {
        current = null
    }

    private fun bridge(): BukkitItemBridge {
        current?.let {
            return it
        }
        val connected = sortedSetOf<String>()
        val next =
            BukkitItemBridge.builder()
                .detectSupportedPlugins(
                    { plugin -> connected += plugin },
                    { plugin, error ->
                        MenuLog.warning("ItemBridge 无法接入 $plugin：${error.javaClass.simpleName}")
                    },
                    { plugin -> plugin.isEnabled },
                )
                .build()
        current = next
        val providers = connected.toList()
        if (providers != reportedProviders) {
            reportedProviders = providers
            MenuLog.info(
                "ItemBridge：支持 ${plugins.size} 种物品源，当前已接入 ${providers.size} 种" +
                    if (providers.isEmpty()) "。" else "：${providers.joinToString("、")}"
            )
        }
        return next
    }

    fun sources(): Map<String, MenuItemSource> = plugins.mapValues { (id, plugin) ->
        ItemBridgeSource(id, plugin, ::bridge) { Bukkit.getPluginManager().isPluginEnabled(plugin) }
    }
}

/** Validation queries the registry only; player-dependent generation happens when displaying. */
class ItemBridgeSource(
    private val provider: String,
    private val plugin: String,
    private val bridge: () -> BukkitItemBridge,
    private val enabled: () -> Boolean,
) : MenuItemSource {
    override fun problem(input: String): String? {
        if (!enabled()) return "$plugin 未安装或未启用"
        val api = bridge()
        if (!api.hasProvider(provider)) return "ItemBridge 未能接入 $plugin，请检查该插件版本和服务端日志"
        return if (api.has(provider, input)) null else "$plugin 尚未加载或不存在物品：$input"
    }

    override fun build(input: String, player: Player?): ItemStack? {
        if (!enabled()) return null
        return bridge().buildOrNull(provider, input, player, BuildContext.empty())
    }
}
