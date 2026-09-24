package online.toraka.dialogmenu

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/** Compile the operator-facing format into the same validated canvas and action model as v1. */
object SimpleMenuParser {
    val defaultPages =
        listOf("profile", "sound", "particles", "notices", "loot", "appearance", "help")

    private data class Binding(
        val state: String,
        val action: String = "",
        val choices: Map<String, String> = emptyMap(),
    )

    private val bindings =
        mapOf(
            "language" to
                Binding(
                    "language",
                    choices = mapOf("zh_cn" to "language_zh_cn", "en_us" to "language_en_us"),
                ),
            "theme" to
                Binding("theme", choices = mapOf("dark" to "theme_dark", "light" to "theme_light")),
            "menu-scale" to
                Binding(
                    "menu-scale",
                    choices = MenuScale.entries.associate { it.id to "menu_scale_${it.id}" },
                ),
            "particle-density" to
                Binding(
                    "density",
                    choices =
                        listOf("off", "low", "medium", "high").associateWith { "density_$it" },
                ),
            "particles" to Binding("particles", "particles"),
            "sounds" to Binding("sounds", "sounds"),
            "leaves" to Binding("leaves", "leaves"),
            "firefly" to Binding("firefly", "firefly"),
            "biome" to Binding("biome", "biome"),
            "pickup" to Binding("pickup", "pickup"),
            "loot-beams" to Binding("beams", "beam"),
            "loot-sounds" to Binding("beam-sounds", "beam_sounds"),
        )

    fun parse(source: String, readPage: (String) -> String): MenuDefinition {
        val config = MenuConfigParser.yaml(source, "config.yml")
        keys(
            config,
            setOf(
                "Version",
                "Title",
                "DefaultPage",
                "Language",
                "Theme",
                "HideFocusOutline",
                "ShowFooter",
                "Navigation",
                "Pages",
                "MainMenu",
            ),
            "config.yml",
        )
        require(config.isInt("Version") && config.getInt("Version") == 2) {
            "config.yml.Version: 必须为 2"
        }
        val ids = strings(config.get("Pages"), "config.yml.Pages")
        require(ids.isNotEmpty() && ids.size <= 8 && ids.distinct().size == ids.size) {
            "config.yml.Pages: 需要 1–8 个不同的页面 ID"
        }
        ids.forEach {
            require(it.matches(Regex("[a-z][a-z0-9_-]{0,47}"))) { "config.yml.Pages: 无效页面 ID $it" }
        }
        val compiled =
            MenuConfigParser.yaml(MenuRepository.resource("menu.yml"), "bundled/menu.yml")
        val languages =
            MenuLanguage.entries.associateWith {
                MenuConfigParser.yaml(
                    MenuRepository.resource("languages/${it.id}.yml"),
                    "bundled/${it.id}",
                )
            }
        val sequences = mutableMapOf<String, List<String>>()
        val itemPages = mutableMapOf<String, List<ItemMenuEntry>>()
        var serial = 0
        fun next() = "simple-${serial++}"
        fun label(raw: Any?, path: String): String {
            if (raw is String) {
                require(raw.isNotBlank() && raw.none { it.isISOControl() }) { "$path: 需要非空单行文字" }
                // A leading dollar is literal in the simple format, just like any other text.
                if (!raw.startsWith('$')) return raw
            }
            val values =
                when (raw) {
                    is String -> MenuLanguage.entries.associate { it.id to raw }
                    is ConfigurationSection -> raw.getValues(false)
                    is Map<*, *> -> raw
                    else -> error("$path: 填写文字，或 {zh_cn: 中文, en_us: English}")
                }
            require(values.keys == setOf("zh_cn", "en_us")) { "$path: 双语文字需要 zh_cn 和 en_us" }
            val key = next()
            MenuLanguage.entries.forEach {
                val value = values[it.id]
                require(
                    value is String && value.isNotBlank() && value.none { c -> c.isISOControl() }
                ) {
                    "$path.${it.id}: 需要非空单行文字"
                }
                languages.getValue(it).set(key, value)
            }
            return "$$key"
        }
        fun register(definition: Map<String, Any>): String {
            val id = next()
            compiled.set("actions.$id", definition)
            return id
        }
        fun actions(raw: Any?, path: String, permission: String = "", plugin: String = ""): String {
            val entries = strings(raw, path)
            require(entries.size in 1..16) { "$path: 需要 1–16 条动作" }
            val close = entries.first() == "close"
            val remaining = if (close) entries.drop(1) else entries
            val steps = remaining.mapIndexed { index, entry ->
                val verb = entry.substringBefore(':').trim()
                val argument = entry.substringAfter(':', "").trim()
                val definition: Map<String, Any> =
                    when (verb) {
                        "command",
                        "console" -> {
                            require(argument.isNotEmpty()) { "$path[$index]: 指令不能为空" }
                            require(!argument.startsWith('/') && argument.length <= 512) {
                                "$path[$index]: 指令不加 /，最长 512 字符"
                            }
                            require(
                                !argument.contains('%') &&
                                    Regex("\\{[^}]*}").findAll(argument).all {
                                        it.value in setOf("{player}", "{uuid}")
                                    }
                            ) {
                                "$path[$index]: 指令仅支持 {player}、{uuid} 占位符"
                            }
                            mapOf(
                                "type" to
                                    if (verb == "console") "console-command" else "player-command",
                                "command" to argument,
                            )
                        }
                        "page" -> {
                            require(argument in ids) { "$path[$index]: 页面不存在 $argument" }
                            mapOf("type" to "page", "value" to argument)
                        }
                        "refresh",
                        "search" -> {
                            require(entry == verb) { "$path[$index]: $verb 不接受参数" }
                            mapOf("type" to "builtin", "value" to verb)
                        }
                        else ->
                            error(
                                "$path[$index]: 支持 close、command: 指令、console: 指令、page: 页面、refresh、search；close 仅放在第一项"
                            )
                    }
                require(verb in setOf("command", "console") || index == remaining.lastIndex) {
                    "$path: 页面跳转、刷新或搜索只能作为最后一项"
                }
                register(definition)
            }
            if (steps.isEmpty())
                return register(
                    mapOf(
                        "type" to "builtin",
                        "value" to "close",
                        "permission" to permission,
                        "requires-plugin" to plugin,
                    )
                )
            val id =
                register(
                    mapOf(
                        "type" to "builtin",
                        "value" to "refresh",
                        "permission" to permission,
                        "requires-plugin" to plugin,
                        "close" to close,
                    )
                )
            sequences[id] = steps
            return id
        }
        fun protectedAction(id: String, permission: String, plugin: String): String {
            val definition =
                compiled.getConfigurationSection("actions.$id")!!.getValues(false).toMutableMap()
            if (permission.isNotEmpty()) definition["permission"] = permission
            if (plugin.isNotEmpty()) {
                val original = definition["requires-plugin"]
                require(original == null || original == plugin) { "Bind 的插件依赖不能替换为 $plugin" }
                definition["requires-plugin"] = plugin
            }
            return register(definition)
        }
        if (config.contains("Title"))
            compiled.set("title", label(config.get("Title"), "config.yml.Title"))
        compiled.set(
            "default-page",
            optionalString(config, "DefaultPage", "config.yml").ifEmpty { ids.first() },
        )
        compiled.set(
            "defaults.language",
            optionalString(config, "Language", "config.yml").ifEmpty { "zh_cn" },
        )
        compiled.set(
            "defaults.theme",
            optionalString(config, "Theme", "config.yml").ifEmpty { "dark" },
        )
        require(compiled.getString("default-page") in ids) {
            "config.yml.DefaultPage: 必须为 Pages 中的页面"
        }
        require(MenuLanguage.entries.any { it.id == compiled.getString("defaults.language") }) {
            "config.yml.Language: 使用 zh_cn / en_us"
        }
        require(MenuTheme.entries.any { it.id == compiled.getString("defaults.theme") }) {
            "config.yml.Theme: 使用 dark / light"
        }
        if (config.contains("HideFocusOutline")) {
            require(config.isBoolean("HideFocusOutline")) {
                "config.yml.HideFocusOutline: 必须为 true / false"
            }
            compiled.set("hide-focus-outline", config.getBoolean("HideFocusOutline"))
        }
        if (config.contains("ShowFooter")) {
            require(config.isBoolean("ShowFooter")) {
                "config.yml.ShowFooter: 必须为 true / false"
            }
            compiled.set("footer.enabled", config.getBoolean("ShowFooter"))
        }
        if (config.contains("MainMenu")) {
            val action = actions(config.get("MainMenu"), "config.yml.MainMenu")
            val common =
                compiled.getMapList("common").map { value ->
                    value.entries
                        .associate { it.key.toString() to requireNotNull(it.value) }
                        .toMutableMap()
                }
            common.first { it["action"] == "main" }["action"] = action
            compiled.set("common", common)
        }
        if (config.contains("Navigation")) {
            val navigation = section(config, "Navigation", "config.yml")
            keys(navigation, setOf("Position", "Step", "FontSize", "Bold"), "Navigation")
            position(navigation, "Position", "Navigation")?.let {
                compiled.set("navigation.x", it.first)
                compiled.set("navigation.row", it.second)
            }
            for ((from, to) in
                mapOf("Step" to "step", "FontSize" to "font-size", "Bold" to "bold")) {
                if (navigation.contains(from)) compiled.set("navigation.$to", navigation.get(from))
            }
        }
        compiled.set("pages", null)
        ids.forEach { id ->
            val file = "menus/$id.yml"
            val page = MenuConfigParser.yaml(readPage(id), file)
            try {
                keys(
                    page,
                    setOf("Title", "TitleStyle", "Icon", "Keywords", "Layout", "Icons", "Renderer"),
                    file,
                )
                val title = label(page.get("Title"), "$file.Title")
                val layout = strings(page.get("Layout"), "$file.Layout")
                require(layout.size in 1..30 && layout.distinct().size == layout.size) {
                    "$file.Layout: 需要 1–30 个不同的控件名称"
                }
                val icons = section(page, "Icons", file)
                layout.forEach { name ->
                    require(
                        name.isNotBlank() && !name.contains('.') && name.none(Char::isISOControl)
                    ) {
                        "$file.Layout: 控件名不能含点或控制字符"
                    }
                }
                if (ItemMenuPage.usesItems(page, layout, file)) {
                    require(!page.contains("TitleStyle")) { "$file.TitleStyle: 原生物品页不使用画布标题样式" }
                    itemPages[id] = ItemMenuPage.parse(icons, layout, file, ::label, ::actions)
                    compiled.set(
                        "pages.$id",
                        mapOf(
                            "label" to title,
                            "icon" to optionalString(page, "Icon", file),
                            "keywords" to
                                if (page.contains("Keywords"))
                                    strings(page.get("Keywords"), "$file.Keywords")
                                else emptyList<String>(),
                            "widgets" to arrayListOf<Map<String, Any>>(),
                        ),
                    )
                    return@forEach
                }
                val widgets =
                    mutableListOf<Map<String, Any>>(
                        mapOf("type" to "heading", "row" to 1, "text" to title)
                    )
                if (page.contains("TitleStyle")) {
                    val titleStyle = section(page, "TitleStyle", file)
                    keys(
                        titleStyle,
                        setOf("Position", "FontSize", "Bold", "Width", "Color"),
                        "$file.TitleStyle",
                    )
                    val heading = widgets[0].toMutableMap()
                    appearance(titleStyle, heading, "$file.TitleStyle")
                    widgets[0] = heading
                }
                var row =
                    maxOf(
                        3,
                        (widgets[0]["row"] as Int) +
                            TitleFont.lineRows((widgets[0]["font-size"] as? Int) ?: 8),
                    )
                var lower = false
                fun lowerPanel(heading: String) {
                    require(!lower) { "$file.Layout: 内容超出两个面板，请减少说明、拆分页面或调整顺序" }
                    lower = true
                    row = 13
                    widgets += mapOf("type" to "heading", "row" to 11, "text" to heading)
                }
                layout.forEach { name ->
                    require(
                        name.isNotBlank() && !name.contains('.') && name.none { it.isISOControl() }
                    ) {
                        "$file.Layout: 控件名不能含点或控制字符"
                    }
                    val at = "$file.Icons.$name"
                    val icon = section(icons, name, file)
                    keys(
                        icon,
                        setOf(
                            "Type",
                            "Style",
                            "Name",
                            "Description",
                            "Bind",
                            "State",
                            "Options",
                            "Actions",
                            "Permission",
                            "RequiresPlugin",
                            "Position",
                            "LabelPosition",
                            "FontSize",
                            "Bold",
                            "Width",
                            "Color",
                        ),
                        at,
                    )
                    val type = optionalString(icon, "Type", at).ifEmpty { "button" }
                    require(
                        type in setOf("button", "toggle", "slider", "dropdown", "heading", "text")
                    ) {
                        "$at.Type: 未知控件 $type"
                    }
                    val style = optionalString(icon, "Style", at)
                    if (icon.contains("Style")) {
                        require(type == "toggle" && style in setOf("button", "switch")) {
                            "$at.Style: 仅 toggle 可使用 button / switch"
                        }
                    }
                    val position = position(icon, "Position", at)
                    val fontSize =
                        if (icon.contains("FontSize")) {
                            require(icon.isInt("FontSize") && icon.getInt("FontSize") in 6..24) {
                                "$at.FontSize: 使用 6–24 的整数"
                            }
                            icon.getInt("FontSize")
                        } else 8
                    val textRows = TitleFont.lineRows(fontSize)
                    val text = label(icon.get("Name") ?: name, "$at.Name")
                    val descriptions =
                        when (val value = icon.get("Description")) {
                            null -> emptyList()
                            is List<*> ->
                                value.mapIndexed { index, item ->
                                    label(item, "$at.Description[$index]")
                                }
                            else -> listOf(label(value, "$at.Description"))
                        }
                    require(descriptions.size <= 3) { "$at.Description: 最多 3 行" }
                    if (type == "heading") {
                        require(
                            descriptions.isEmpty() &&
                                icon.getKeys(false).all {
                                    it in
                                        setOf(
                                            "Type",
                                            "Name",
                                            "Position",
                                            "FontSize",
                                            "Bold",
                                            "Width",
                                            "Color",
                                        )
                                }
                        ) {
                            "$at: heading 支持 Type、Name、Position、FontSize、Bold、Width、Color"
                        }
                        lowerPanel(text)
                        val heading = widgets.last().toMutableMap()
                        appearance(icon, heading, at)
                        widgets[widgets.lastIndex] = heading
                        row = maxOf(13, (heading["row"] as Int) + textRows)
                    } else {
                        val height = maxOf(2, textRows) + descriptions.size * textRows
                        // Popup rows align with the lower panel's first row so its
                        // whole bitmap never paints over half an expanded option.
                        if (position == null && type == "dropdown" && row % 2 != 0) row++
                        if (position == null && !lower && row + height > 9) lowerPanel(text)
                        if (position != null) row = position.second
                        if (position == null && type == "dropdown" && row % 2 != 0) row++
                        require(row + height <= if (position != null) DialogCanvas.ROWS else 24) {
                            "$at: 面板已放不下此控件，请减少说明或拆分页面"
                        }
                        val widget = linkedMapOf<String, Any>("type" to type, "row" to row)
                        widget[
                            if (type in setOf("dropdown", "slider", "toggle")) "label"
                            else "text"] = text
                        appearance(icon, widget, at)
                        val originalX =
                            when (type) {
                                "text" -> 123
                                "slider" -> 280
                                else -> 330
                            }
                        val deltaX = (position?.first ?: originalX) - originalX
                        if (type in setOf("toggle", "slider", "dropdown")) {
                            val labelPosition = position(icon, "LabelPosition", at)
                            widget["label-x"] = labelPosition?.first ?: (123 + deltaX)
                            widget["label-row"] =
                                labelPosition?.second ?: (row + if (fontSize == 8) 1 else 0)
                        } else
                            require(!icon.contains("LabelPosition")) {
                                "$at.LabelPosition: 仅用于开关、滑条、下拉框左侧的 Name"
                            }
                        require(type == "text" || !icon.contains("Color")) {
                            "$at.Color: 仅用于 text / heading"
                        }
                        if (style.isNotEmpty()) widget["toggle-style"] = style
                        val bind = optionalString(icon, "Bind", at)
                        val binding =
                            if (bind.isEmpty()) null
                            else bindings[bind] ?: error("$at.Bind: 未知绑定 $bind")
                        val permission = optionalString(icon, "Permission", at)
                        val plugin = optionalString(icon, "RequiresPlugin", at)
                        if (type == "text")
                            require(
                                icon.getKeys(false).all {
                                    it in
                                        setOf(
                                            "Type",
                                            "Name",
                                            "Description",
                                            "Position",
                                            "FontSize",
                                            "Bold",
                                            "Width",
                                            "Color",
                                        )
                                }
                            ) {
                                "$at: text 支持 Name、Description、Position、FontSize、Bold、Width、Color"
                            }
                        if (binding != null) {
                            require(
                                type in setOf("toggle", "slider", "dropdown") &&
                                    !icon.contains("State") &&
                                    !icon.contains("Actions")
                            ) {
                                "$at: Bind 用于开关/滑条/下拉框，不与 State 或 Actions 混用"
                            }
                            require((type == "toggle") == binding.choices.isEmpty()) {
                                "$at.Bind: $bind 与 $type 类型不匹配"
                            }
                            widget["state"] = binding.state
                        } else if (type in setOf("toggle", "slider", "dropdown")) {
                            val state = optionalString(icon, "State", at)
                            require(state.matches(Regex("%[a-zA-Z0-9_:.\\-]+%"))) {
                                "$at.State: 无 Bind 时需要完整的 %PAPI变量%"
                            }
                            val stateId = next()
                            compiled.set("states.$stateId", state)
                            widget["state"] = stateId
                        }
                        if (type in setOf("button", "toggle")) {
                            if (type == "button")
                                require(!icon.contains("State")) {
                                    "$at.State: button 不读取状态，使用 toggle / dropdown / slider"
                                }
                            require(!icon.contains("Options")) { "$at.Options: 只用于滑条或下拉框" }
                            widget["action"] =
                                if (binding != null)
                                    protectedAction(binding.action, permission, plugin)
                                else actions(icon.get("Actions"), "$at.Actions", permission, plugin)
                        }
                        if (type in setOf("slider", "dropdown")) {
                            require(!icon.contains("Actions")) {
                                "$at.Actions: 选择控件的动作写在 Options 每个选项内"
                            }
                            val options = section(icon, "Options", at)
                            require(options.getKeys(false).size in 2..8) {
                                "$at.Options: 需要 2–8 个选项（off 必须加引号）"
                            }
                            if (type == "dropdown")
                                require(
                                    row + 2 + options.getKeys(false).size * 2 <= DialogCanvas.ROWS
                                ) {
                                    "$at.Options: 展开列表超出画布，请在 Layout 中前移或减少选项"
                                }
                            widget["options"] =
                                options.getKeys(false).map { value ->
                                    require(!value.contains('.') && value.isNotBlank()) {
                                        "$at.Options: 无效选项值 $value"
                                    }
                                    val item = options.get(value)
                                    val optionLabel: String
                                    val action: String
                                    if (binding != null) {
                                        val builtin =
                                            binding.choices[value]
                                                ?: error(
                                                    "$at.Options.$value: $bind 不支持此值，可选 ${binding.choices.keys}"
                                                )
                                        optionLabel = label(item, "$at.Options.$value")
                                        action = protectedAction(builtin, permission, plugin)
                                    } else {
                                        val option = section(options, value, at)
                                        keys(option, setOf("Name", "Actions"), "$at.Options.$value")
                                        optionLabel =
                                            label(option.get("Name"), "$at.Options.$value.Name")
                                        action =
                                            actions(
                                                option.get("Actions"),
                                                "$at.Options.$value.Actions",
                                                permission,
                                                plugin,
                                            )
                                    }
                                    mapOf(
                                        "value" to value,
                                        "label" to optionLabel,
                                        "action" to action,
                                    )
                                }
                        }
                        widgets += widget
                        descriptions.forEachIndexed { index, description ->
                            widgets +=
                                mapOf(
                                    "type" to "text",
                                    "x" to 123 + deltaX,
                                    "row" to row + maxOf(2, textRows) + index * textRows,
                                    "width" to
                                        minOf(
                                            (widget["width"] as? Int) ?: 316,
                                            DialogCanvas.WIDTH - 123 - deltaX,
                                        ),
                                    "text" to description,
                                    "font-size" to fontSize,
                                    "bold" to icon.getBoolean("Bold", false),
                                )
                        }
                        row += height
                    }
                }
                compiled.set(
                    "pages.$id",
                    mapOf(
                        "label" to title,
                        "icon" to optionalString(page, "Icon", file),
                        "keywords" to
                            if (page.contains("Keywords"))
                                strings(page.get("Keywords"), "$file.Keywords")
                            else emptyList<String>(),
                        "widgets" to widgets,
                    ),
                )
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("$file: ${error.message}", error)
            } catch (error: IllegalStateException) {
                throw IllegalArgumentException("$file: ${error.message}", error)
            }
        }
        val parsed =
            MenuConfigParser.parse(
                compiled.saveToString(),
                languages.mapValues { it.value.saveToString() },
            )
        return parsed.copy(
            pages =
                parsed.pages.mapValues { (id, page) ->
                    itemPages[id]?.let { page.copy(itemLayout = true, itemEntries = it) } ?: page
                },
            actions =
                parsed.actions.mapValues { (id, action) ->
                    sequences[id]?.let {
                        action.copy(type = "sequence", steps = it.map(parsed.actions::getValue))
                    } ?: action
                },
        )
    }

    private fun position(
        section: ConfigurationSection,
        key: String,
        path: String,
    ): Pair<Int, Int>? {
        if (!section.contains(key)) return null
        val values = section.getList(key)
        require(values != null && values.size == 2 && values.all { it is Int && it >= 0 }) {
            "$path.$key: 使用 [X 像素, Y 行号]，每行 9 像素"
        }
        return (values[0] as Int) to (values[1] as Int)
    }

    private fun appearance(
        source: ConfigurationSection,
        target: MutableMap<String, Any>,
        path: String,
    ) {
        position(source, "Position", path)?.let {
            target["x"] = it.first
            target["row"] = it.second
        }
        for ((from, to) in
            mapOf(
                "FontSize" to "font-size",
                "Bold" to "bold",
                "Width" to "width",
                "Color" to "color",
            )) {
            if (source.contains(from)) target[to] = requireNotNull(source.get(from))
        }
    }

    private fun keys(section: ConfigurationSection, allowed: Set<String>, path: String) {
        require((section.getKeys(false) - allowed).isEmpty()) {
            "$path: 未知字段 ${section.getKeys(false) - allowed}"
        }
    }

    private fun strings(value: Any?, path: String): List<String> {
        require(
            value is List<*> &&
                value.all { it is String && it.isNotBlank() && it.none(Char::isISOControl) }
        ) {
            "$path: 需要字符串列表"
        }
        return value.map { it as String }
    }

    private fun section(
        parent: ConfigurationSection,
        key: String,
        path: String,
    ): ConfigurationSection = parent.getConfigurationSection(key) ?: error("$path.$key: 缺少配置段")

    private fun optionalString(section: ConfigurationSection, key: String, path: String): String {
        if (!section.contains(key)) return ""
        val value = section.get(key)
        require(value is String && value.isNotBlank() && value.none { it.isISOControl() }) {
            "$path.$key: 需要非空字符串"
        }
        return value
    }
}
