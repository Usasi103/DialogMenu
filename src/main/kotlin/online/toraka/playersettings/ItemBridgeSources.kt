package online.toraka.playersettings

import cn.gtemc.itembridge.api.context.BuildContext
import cn.gtemc.itembridge.core.BukkitItemBridge
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** The public configuration accepts only these integrations, even if ItemBridge supports more. */
object ItemBridgeSources {
    val plugins =
        linkedMapOf(
            "oraxen" to "Oraxen",
            "itemsadder" to "ItemsAdder",
            "sxitem" to "SX-Item",
            "neigeitems" to "NeigeItems",
            "craftengine" to "CraftEngine",
        )
    private var current: BukkitItemBridge? = null

    fun reset() {
        current = null
    }

    private fun bridge(): BukkitItemBridge =
        current
            ?: BukkitItemBridge.builder()
                .detectSupportedPlugins(
                    { plugin ->
                        Bukkit.getLogger().info("[PlayerSettings] ItemBridge 已接入 $plugin")
                    },
                    { plugin, error ->
                        Bukkit.getLogger()
                            .warning(
                                "[PlayerSettings] ItemBridge 无法接入 $plugin：${error.javaClass.simpleName}"
                            )
                    },
                    { plugin -> plugin.isEnabled && plugin.name in plugins.values },
                )
                .build()
                .also { current = it }

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
