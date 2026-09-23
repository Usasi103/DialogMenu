package online.toraka.dialogmenu

import org.bukkit.configuration.ConfigurationSection

data class ItemMenuEntry(
    val kind: String,
    val name: String,
    val description: List<String>,
    val display: ItemDisplay?,
    val action: String,
)

/** Native item bodies are independent of the font canvas; never silently flatten a CE model. */
object ItemMenuPage {
    fun usesItems(page: ConfigurationSection, layout: List<String>, path: String): Boolean {
        val renderer = page.get("Renderer")
        require(renderer == null || renderer in setOf("canvas", "items")) {
            "$path.Renderer: 使用 canvas / items"
        }
        val items = layout.any {
            page.contains("Icons.$it.Display.Material") ||
                page.getString("Icons.$it.Type") == "item"
        }
        require(renderer != "canvas" || !items) {
            "$path.Renderer: Display.Material 需要 items 布局，不能放进字体画布"
        }
        return renderer == "items" || items
    }

    fun parse(
        icons: ConfigurationSection,
        layout: List<String>,
        file: String,
        label: (Any?, String) -> String,
        actions: (Any?, String, String, String) -> String,
    ): List<ItemMenuEntry> = layout.map { id ->
        val at = "$file.Icons.$id"
        val icon = icons.getConfigurationSection(id) ?: error("$at: 缺少控件定义")
        keys(
            icon,
            setOf(
                "Type",
                "Name",
                "Description",
                "Display",
                "Actions",
                "Permission",
                "RequiresPlugin",
            ),
            at,
        )
        val type = string(icon, "Type", "button", at)
        require(type in setOf("button", "item", "text", "heading")) {
            "$at.Type: 物品页面支持 button / item / text / heading；开关、滑条、下拉框请保留在 canvas 页面"
        }
        val display =
            if (icon.contains("Display")) {
                icon.getConfigurationSection("Display") ?: error("$at.Display: 需要配置段")
            } else null
        display?.let {
            keys(it, setOf("Material", "Name", "Lore", "Amount", "Fallback"), "$at.Display")
        }
        require(!(icon.contains("Name") && display?.contains("Name") == true)) {
            "$at: Name 与 Display.Name 只填写一个"
        }
        require(!(icon.contains("Description") && display?.contains("Lore") == true)) {
            "$at: Description 与 Display.Lore 只填写一个"
        }
        val name = label(display?.get("Name") ?: icon.get("Name") ?: id, "$at.Name")
        val rawDescription = display?.get("Lore") ?: icon.get("Description")
        val description =
            when (rawDescription) {
                null -> emptyList()
                is List<*> ->
                    rawDescription.mapIndexed { index, value ->
                        label(value, "$at.Description[$index]")
                    }
                else -> listOf(label(rawDescription, "$at.Description"))
            }
        require(description.size <= 6) { "$at.Description: 最多 6 行" }
        val item =
            if (display?.contains("Material") == true) {
                require(type in setOf("button", "item")) {
                    "$at.Display.Material: 只用于 button / item"
                }
                val material =
                    ItemReference.parse(
                        string(display, "Material", null, "$at.Display"),
                        "$at.Display.Material",
                    )
                val fallback =
                    if (display.contains("Fallback")) {
                        ItemReference.parse(
                                string(display, "Fallback", null, "$at.Display"),
                                "$at.Display.Fallback",
                            )
                            .also {
                                require(it.provider == "minecraft") {
                                    "$at.Display.Fallback: 回退物品必须为原版物品"
                                }
                            }
                    } else null
                require(!display.contains("Amount") || display.isInt("Amount")) {
                    "$at.Display.Amount: 需要整数"
                }
                val amount = display.getInt("Amount", 1)
                require(amount in 1..99) { "$at.Display.Amount: 使用 1–99" }
                ItemDisplay(material, fallback, amount, "$at.Display")
            } else {
                require(type != "item") { "$at.Display.Material: item 控件需要物品源" }
                require(
                    display?.contains("Amount") != true && display?.contains("Fallback") != true
                ) {
                    "$at.Display: Amount / Fallback 需要 Material"
                }
                null
            }
        val action =
            if (type == "button")
                actions(
                    icon.get("Actions"),
                    "$at.Actions",
                    string(icon, "Permission", "", at),
                    string(icon, "RequiresPlugin", "", at),
                )
            else {
                require(listOf("Actions", "Permission", "RequiresPlugin").none(icon::contains)) {
                    "$at: 交互动作和权限只用于 button"
                }
                ""
            }
        ItemMenuEntry(type, name, description, item, action)
    }

    private fun keys(section: ConfigurationSection, allowed: Set<String>, path: String) {
        require((section.getKeys(false) - allowed).isEmpty()) {
            "$path: 未知字段 ${section.getKeys(false) - allowed}"
        }
    }

    private fun string(
        section: ConfigurationSection,
        key: String,
        default: String?,
        path: String,
    ): String {
        if (!section.contains(key) && default != null) return default
        val value = section.get(key)
        require(value is String && value.isNotBlank() && value.none(Char::isISOControl)) {
            "$path.$key: 需要非空字符串"
        }
        return value
    }
}
