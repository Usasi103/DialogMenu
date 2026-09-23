package online.toraka.playersettings

import java.util.Locale
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.OptionalEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

private val airMaterials = setOf(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR)

/** Keep the provider separate from its input: a CE input includes its own namespace. */
data class ItemReference(val provider: String, val input: String) {
    companion object {
        fun parse(value: String, path: String): ItemReference {
            require(value.isNotBlank() && value == value.trim() && value.none(Char::isISOControl)) {
                "$path: 需要非空物品名称"
            }
            val parts = value.split(':', limit = 3)
            val source = parts.first().equals("source", true)
            val provider =
                if (source) {
                    require(parts.size == 3 && parts[2].isNotBlank()) {
                        "$path: 使用 source:CE:命名空间:物品ID"
                    }
                    when (parts[1].lowercase(Locale.ROOT)) {
                        "ce",
                        "craftengine" -> "craftengine"
                        "oraxen" -> "oraxen"
                        "ia",
                        "itemsadder" -> "itemsadder"
                        "sx-item",
                        "sxitem",
                        "si" -> "sxitem"
                        "neigeitems",
                        "ni" -> "neigeitems"
                        "minecraft",
                        "vanilla" -> "minecraft"
                        else ->
                            error(
                                "$path: 不支持物品源 ${parts[1]}，可用 Oraxen / IA / SX-Item / NI / CE / MINECRAFT"
                            )
                    }
                } else "minecraft"
            val input = if (source) parts[2] else value
            if (provider in setOf("craftengine", "itemsadder")) {
                require(input.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) {
                    "$path: $provider 物品必须使用小写的 命名空间:物品ID"
                }
                return ItemReference(provider, input)
            }
            if (provider != "minecraft") {
                require(input.isNotBlank() && input == input.trim() && '%' !in input) {
                    "$path: 需要物品 ID，不支持 Material 占位符"
                }
                return ItemReference(provider, input)
            }
            val vanilla = input.lowercase(Locale.ROOT).removePrefix("minecraft:")
            require(vanilla.matches(Regex("[a-z0-9_]+"))) {
                "$path: 原版物品格式为 minecraft:diamond 或 DIAMOND"
            }
            val material = Material.matchMaterial(vanilla)
            require(
                material != null &&
                    material !in airMaterials &&
                    !material.name.startsWith("LEGACY_")
            ) {
                "$path: 未知或空物品 $input"
            }
            return ItemReference(provider, vanilla)
        }
    }
}

interface MenuItemSource {
    /** Null means available; no player-dependent item is constructed during validation. */
    fun problem(input: String): String?

    fun build(input: String, player: Player?): ItemStack?
}

data class ItemDisplay(
    val material: ItemReference,
    val fallback: ItemReference?,
    val amount: Int,
    val path: String,
)

data class ResolvedMenuItem(val item: ItemStack?, val problem: String?) {
    val available: Boolean
        get() = problem == null && item != null
}

/** Only display copies are modified. Nothing here gives or removes player inventory items. */
class MenuItemSources(private val sources: Map<String, MenuItemSource>) {
    fun problem(reference: ItemReference): String? =
        try {
            sources[reference.provider]?.problem(reference.input)
                ?: if (reference.provider !in sources) "物品源未安装：${reference.provider}" else null
        } catch (error: Exception) {
            "物品源 ${reference.provider} 无法读取 ${reference.input}：${error.javaClass.simpleName}"
        } catch (error: LinkageError) {
            "物品源 ${reference.provider} API 不兼容：${error.javaClass.simpleName}"
        }

    private fun build(reference: ItemReference, player: Player?, amount: Int): ItemStack? {
        val stack = sources[reference.provider]?.build(reference.input, player) ?: return null
        if (stack.type in airMaterials) return null
        return stack.clone().also { it.amount = amount }
    }

    fun resolve(display: ItemDisplay, player: Player?): ResolvedMenuItem {
        var failure = problem(display.material)
        if (failure == null) {
            try {
                val stack = build(display.material, player, display.amount)
                if (stack != null) return ResolvedMenuItem(stack, null)
                failure = "物品源返回空物品：${display.material.input}"
            } catch (error: Exception) {
                failure = "物品构建失败 ${display.material.input}：${error.javaClass.simpleName}"
            } catch (error: LinkageError) {
                failure = "物品源 ${display.material.provider} API 不兼容：${error.javaClass.simpleName}"
            }
        }
        val fallback =
            display.fallback?.let { reference ->
                runCatching { build(reference, player, display.amount) }.getOrNull()
            }
        return ResolvedMenuItem(fallback, failure)
    }
}

object ItemSources {
    val registry =
        MenuItemSources(
            mapOf(
                "minecraft" to
                    object : MenuItemSource {
                        override fun problem(input: String): String? {
                            val material = Material.matchMaterial(input)
                            return if (
                                material != null && material.isItem && material !in airMaterials
                            )
                                null
                            else "不是可展示的原版物品：$input"
                        }

                        override fun build(input: String, player: Player?): ItemStack? =
                            if (problem(input) == null) ItemStack(Material.matchMaterial(input)!!)
                            else null
                    }
            ) + ItemBridgeSources.sources()
        )

    fun validate(menu: MenuDefinition, sources: MenuItemSources = registry): List<String> {
        val warnings = mutableListOf<String>()
        menu.pages.values
            .flatMap { it.itemEntries }
            .forEach { entry ->
                val display = entry.display ?: return@forEach
                display.fallback?.let { fallback ->
                    require(sources.problem(fallback) == null) {
                        "${display.path}.Fallback: 无效回退物品"
                    }
                }
                sources.problem(display.material)?.let { problem ->
                    val detail = "${display.path}.Material: $problem"
                    require(display.fallback != null) { detail }
                    warnings += "$detail；使用回退显示，对应动作停用"
                }
            }
        return warnings.distinct()
    }

    fun changed() {
        ItemBridgeSources.reset()
        runCatching { validate(MenuRuntime.current) }
            .onSuccess {
                it.forEach { warning -> Bukkit.getLogger().warning("[PlayerSettings] $warning") }
            }
            .onFailure { Bukkit.getLogger().warning("[PlayerSettings] ${it.message}") }
        SettingsDialog.reloaded()
    }

    @SubscribeEvent(bind = "net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent")
    fun reloaded(event: OptionalEvent) {
        submit { changed() }
    }

    @SubscribeEvent
    fun disabled(event: PluginDisableEvent) {
        if (event.plugin.name in ItemBridgeSources.plugins.values) submit { changed() }
    }

    @SubscribeEvent
    fun enabled(event: PluginEnableEvent) {
        if (event.plugin.name in ItemBridgeSources.plugins.values) submit { changed() }
    }
}
