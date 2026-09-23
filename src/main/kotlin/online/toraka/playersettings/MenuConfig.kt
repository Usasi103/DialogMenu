package online.toraka.playersettings

import java.io.File
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

enum class WidgetKind {
    TEXT,
    HEADING,
    BUTTON,
    TOGGLE,
    SLIDER,
    DROPDOWN,
    SPRITE,
}

data class MenuOption(val value: String, val label: String, val action: String)

data class MenuWidget(
    val kind: WidgetKind,
    val x: Int,
    val row: Int,
    val text: String,
    val label: String,
    val labelX: Int,
    val width: Int,
    val color: String,
    val skin: String,
    val action: String,
    val state: String,
    val selected: String,
    val options: List<MenuOption>,
)

data class MenuAction(
    val type: String,
    val command: String,
    val whenTrue: String,
    val whenFalse: String,
    val state: String,
    val value: String,
    val permission: String,
    val plugin: String,
    val close: Boolean,
)

data class MenuPage(
    val id: String,
    val label: String,
    val icon: String,
    val keywords: List<String>,
    val widgets: List<MenuWidget>,
)

data class MenuDefinition(
    val title: String,
    val defaultPage: String,
    val language: MenuLanguage,
    val theme: MenuTheme,
    val hideFocus: Boolean,
    val navX: Int,
    val navRow: Int,
    val navStep: Int,
    val footerLabel: String,
    val footerAction: String,
    val states: Map<String, String>,
    val actions: Map<String, MenuAction>,
    val common: List<MenuWidget>,
    val pages: Map<String, MenuPage>,
    val translations: Map<MenuLanguage, Map<String, String>>,
) {
    fun text(language: MenuLanguage, value: String): String =
        if (value.startsWith("$")) translations.getValue(language).getValue(value.drop(1))
        else value

    fun search(query: String): String? {
        if (query.isBlank()) return null
        // Longer matches win: "掉落音效" must resolve to loot, not the general sound page.
        return pages.values
            .mapNotNull { page ->
                val terms = page.keywords + MenuLanguage.entries.map { text(it, page.label) }
                terms
                    .filter { query.contains(it, true) }
                    .maxOfOrNull { it.length }
                    ?.let { page.id to it }
            }
            .maxByOrNull { it.second }
            ?.first
    }
}

/**
 * Parse completely before publishing a new immutable snapshot. Never save over the operator's YAML.
 */
object MenuConfigParser {
    val skins =
        linkedMapOf(
            "panel-top" to DialogCanvas.PANEL_TOP,
            "panel-bottom" to DialogCanvas.PANEL_BOTTOM,
            "nav" to DialogCanvas.NAV,
            "control" to DialogCanvas.CONTROL,
            "search" to DialogCanvas.SEARCH,
            "search-icon" to DialogCanvas.SEARCH_ICON,
            "refresh-icon" to DialogCanvas.PANEL_ACTION,
            "profile-icon" to DialogCanvas.Skin(0xE090, 9, 1),
            "sound-icon" to DialogCanvas.Skin(0xE091, 9, 1),
            "particles-icon" to DialogCanvas.Skin(0xE092, 9, 1),
            "notices-icon" to DialogCanvas.Skin(0xE093, 9, 1),
            "loot-icon" to DialogCanvas.Skin(0xE094, 9, 1),
            "appearance-icon" to DialogCanvas.Skin(0xE095, 9, 1),
        )
    private val requiredMessages =
        setOf(
            "menu.title",
            "search.title",
            "search.keyword",
            "search.submit",
            "back",
            "on",
            "off",
            "unavailable",
            "pack.required",
            "search.empty",
            "setting.failed",
            "setting.denied",
        )
    private val builtinActions =
        setOf(
            "close",
            "refresh",
            "search",
            "language:zh_cn",
            "language:en_us",
            "theme:dark",
            "theme:light",
        )
    private val builtinStates = setOf("pickup", "loot-beams", "loot-sounds", "language", "theme")

    fun parse(menu: String, languages: Map<MenuLanguage, String>): MenuDefinition {
        val root = yaml(menu, "menu.yml")
        keys(
            root,
            setOf(
                "version",
                "title",
                "default-page",
                "defaults",
                "hide-focus-outline",
                "navigation",
                "footer",
                "states",
                "actions",
                "common",
                "pages",
            ),
            "menu.yml",
        )
        require(root.getInt("version") == 1) { "menu.yml: version 必须为 1" }
        if (root.contains("hide-focus-outline"))
            require(root.isBoolean("hide-focus-outline")) { "hide-focus-outline: 必须为 true / false" }
        val translations = languages.mapValues { (language, source) ->
            val file = "languages/${language.id}.yml"
            val values =
                yaml(source, file).getValues(true).filterValues { it !is ConfigurationSection }
            require(values.values.all { it is String }) { "$file: 文案必须加引号并使用字符串" }
            val messages = values.mapValues { it.value as String }
            require(messages.keys.containsAll(requiredMessages)) {
                "$file: 缺少文案 ${requiredMessages - messages.keys}"
            }
            messages.forEach { (key, value) -> line(value, "$file:$key") }
            messages
        }
        require(translations.keys == MenuLanguage.entries.toSet()) { "缺少语言文件" }
        fun text(value: String, path: String): String {
            line(value, path)
            if (value.startsWith("$"))
                translations.forEach { (language, messages) ->
                    require(value.drop(1) in messages) {
                        "$path: languages/${language.id}.yml 缺少 $value"
                    }
                }
            return value
        }
        val stateSection = section(root, "states")
        val states =
            stateSection.getKeys(false).associateWith { id ->
                identifier(id, "states")
                val binding = string(stateSection, id)
                require(
                    binding in builtinStates || binding.matches(Regex("%[a-zA-Z0-9_:.\\-]+%"))
                ) {
                    "states.$id: 未知状态 $binding"
                }
                binding
            }
        fun state(id: String, path: String): String {
            require(id in states) { "$path: 未定义状态 $id" }
            return id
        }
        val actionSection = section(root, "actions")
        val actions =
            actionSection.getKeys(false).associateWith { id ->
                identifier(id, "actions")
                val at = "actions.$id"
                val conf = section(actionSection, id)
                keys(
                    conf,
                    setOf(
                        "type",
                        "command",
                        "when-true",
                        "when-false",
                        "state",
                        "value",
                        "permission",
                        "requires-plugin",
                        "close",
                    ),
                    at,
                )
                val type = string(conf, "type")
                if (conf.contains("close"))
                    require(conf.isBoolean("close")) { "$at.close: 必须为 true / false" }
                listOf("permission", "requires-plugin").forEach { key ->
                    if (conf.contains(key)) require(conf.get(key) is String) { "$at.$key: 必须为字符串" }
                }
                require(
                    type in
                        setOf(
                            "builtin",
                            "page",
                            "player-command",
                            "console-command",
                            "toggle-command",
                        )
                ) {
                    "$at.type: 未知动作 $type"
                }
                fun command(key: String): String {
                    val value = string(conf, key)
                    line(value, "$at.$key")
                    require(!value.startsWith("/") && value.length <= 512) {
                        "$at.$key: 指令不加 /，最长 512 字符"
                    }
                    require(
                        !value.contains('%') &&
                            Regex("\\{[^}]*}").findAll(value).all {
                                it.value in setOf("{player}", "{uuid}")
                            }
                    ) {
                        "$at.$key: 指令仅支持 {player}、{uuid} 占位符"
                    }
                    return value
                }
                val value = if (type in setOf("builtin", "page")) string(conf, "value") else ""
                if (type == "builtin")
                    require(value in builtinActions) { "$at.value: 未知内置动作 $value" }
                MenuAction(
                    type,
                    if (type.endsWith("-command") && type != "toggle-command") command("command")
                    else "",
                    if (type == "toggle-command") command("when-true") else "",
                    if (type == "toggle-command") command("when-false") else "",
                    if (type == "toggle-command") state(string(conf, "state"), at) else "",
                    value,
                    conf.getString("permission", "")!!,
                    conf.getString("requires-plugin", "")!!,
                    conf.getBoolean("close", false),
                )
            }
        fun action(id: String, path: String): String {
            require(id in actions) { "$path: 未定义动作 $id" }
            return id
        }
        fun widgets(parent: ConfigurationSection, key: String): List<MenuWidget> {
            require(parent.isList(key)) { "$key: 必须是 YAML 列表" }
            val entries = parent.getList(key)!!
            require(entries.size <= 100) { "$key: 最多 100 个控件" }
            return entries.mapIndexed { index, raw ->
                val at = "${parent.currentPath}.$key[$index]"
                require(raw is Map<*, *>) { "$at: 控件必须是对象" }
                val conf = YamlConfiguration().createSection("widget", raw)
                keys(
                    conf,
                    setOf(
                        "type",
                        "x",
                        "row",
                        "text",
                        "label",
                        "label-x",
                        "width",
                        "color",
                        "skin",
                        "action",
                        "state",
                        "selected",
                        "options",
                    ),
                    at,
                )
                val kind =
                    WidgetKind.entries.firstOrNull { it.name.equals(string(conf, "type"), true) }
                        ?: error("$at.type: 未知控件类型")
                val defaultX =
                    when (kind) {
                        WidgetKind.HEADING -> 122
                        WidgetKind.TEXT -> 123
                        WidgetKind.SLIDER -> 280
                        WidgetKind.SPRITE -> 114
                        else -> 330
                    }
                listOf("text", "label", "color", "skin", "action", "state", "selected").forEach {
                    name ->
                    if (conf.contains(name))
                        require(conf.get(name) is String) { "$at.$name: 必须为字符串（off/on 请加引号）" }
                }
                if (conf.contains("width"))
                    require(kind in setOf(WidgetKind.TEXT, WidgetKind.HEADING)) {
                        "$at.width: 只有 text/heading 可设置宽度；贴图控件使用固定尺寸"
                    }
                val x = number(conf, "x", defaultX, at)
                val row = number(conf, "row", null, at)
                val labelX = number(conf, "label-x", 123, at)
                val skin =
                    conf.getString(
                        "skin",
                        if (kind == WidgetKind.SPRITE) "panel-top" else "control",
                    )!!
                val sprite = skins[skin] ?: error("$at.skin: 未知贴图 $skin")
                if (kind == WidgetKind.BUTTON)
                    require(skin in setOf("nav", "search", "control")) {
                        "$at.skin: 按钮贴图必须为 nav / search / control"
                    }
                val width =
                    when (kind) {
                        WidgetKind.TEXT,
                        WidgetKind.HEADING -> number(conf, "width", 316, at)
                        WidgetKind.SLIDER -> 164
                        WidgetKind.TOGGLE,
                        WidgetKind.DROPDOWN -> 114
                        else -> sprite.width
                    }
                val height =
                    when (kind) {
                        WidgetKind.TEXT,
                        WidgetKind.HEADING -> 1
                        WidgetKind.SPRITE -> sprite.rows
                        else -> 2
                    }
                require(
                    x >= 0 &&
                        row >= 0 &&
                        width > 0 &&
                        x + width <= DialogCanvas.WIDTH &&
                        row + height <= DialogCanvas.ROWS
                ) {
                    "$at: 控件超出 450 像素 × 29 行画布"
                }
                val label = text(conf.getString("label", "")!!, at)
                if (label.isNotEmpty())
                    require(labelX >= 0 && labelX < x - if (kind == WidgetKind.SLIDER) 60 else 6) {
                        "$at.label-x: 左侧标签空间不足"
                    }
                if (kind == WidgetKind.SLIDER) require(x >= 60) { "$at.x: 滑条左侧需要 60 像素显示当前值" }
                val options =
                    if (kind in setOf(WidgetKind.SLIDER, WidgetKind.DROPDOWN))
                        conf.getMapList("options").mapIndexed { n, option ->
                            fun field(name: String) =
                                (option[name] as? String)?.takeIf { it.isNotBlank() }
                                    ?: error("$at.options[$n].$name: 必须是字符串（off/on 请加引号）")
                            require(option.keys.all { it in setOf("value", "label", "action") }) {
                                "$at.options[$n]: 未知字段"
                            }
                            MenuOption(
                                field("value"),
                                text(field("label"), at),
                                action(field("action"), at),
                            )
                        }
                    else emptyList()
                if (kind in setOf(WidgetKind.SLIDER, WidgetKind.DROPDOWN)) {
                    require(
                        options.size in 2..8 &&
                            options.map { it.value.lowercase() }.distinct().size == options.size
                    ) {
                        "$at.options: 需要 2–8 个不同档位"
                    }
                }
                if (kind == WidgetKind.DROPDOWN)
                    require(row + 2 + options.size * 2 <= DialogCanvas.ROWS) {
                        "$at.options: 展开后的下拉列表超出画布，请上移 row 或减少选项"
                    }
                val binding = conf.getString("state", "")!!
                if (
                    kind in setOf(WidgetKind.TOGGLE, WidgetKind.SLIDER, WidgetKind.DROPDOWN) ||
                        conf.contains("selected")
                )
                    state(binding, at)
                else if (binding.isNotEmpty()) state(binding, at)
                val click = conf.getString("action", "")!!
                if (kind in setOf(WidgetKind.BUTTON, WidgetKind.TOGGLE) || click.isNotEmpty())
                    action(click, at)
                val color =
                    conf.getString(
                        "color",
                        if (kind == WidgetKind.HEADING) "heading" else "muted",
                    )!!
                require(
                    color in setOf("text", "muted", "heading") ||
                        color.matches(Regex("#[0-9a-fA-F]{6}"))
                ) {
                    "$at.color: 使用 text/muted/heading 或 '#RRGGBB'"
                }
                MenuWidget(
                    kind,
                    x,
                    row,
                    text(conf.getString("text", "")!!, at),
                    label,
                    labelX,
                    width,
                    color,
                    skin,
                    click,
                    binding,
                    conf.getString("selected", "")!!,
                    options,
                )
            }
        }
        val pagesSection = section(root, "pages")
        val pages =
            pagesSection.getKeys(false).associateWith { id ->
                identifier(id, "pages")
                val conf = section(pagesSection, id)
                keys(conf, setOf("label", "icon", "keywords", "widgets"), "pages.$id")
                val icon = conf.getString("icon", "")!!
                if (conf.contains("keywords"))
                    require(
                        conf.isList("keywords") && conf.getList("keywords")!!.all { it is String }
                    ) {
                        "pages.$id.keywords: 必须为字符串列表"
                    }
                require(icon.isEmpty() || (skins[icon]?.width == 9 && skins[icon]?.rows == 1)) {
                    "pages.$id.icon: 必须使用 9 像素图标"
                }
                MenuPage(
                    id,
                    text(string(conf, "label"), "pages.$id.label"),
                    icon,
                    conf.getStringList("keywords").filter { it.isNotBlank() },
                    widgets(conf, "widgets"),
                )
            }
        require(pages.isNotEmpty() && pages.size <= 12) { "pages: 需要 1–12 个页面，导航必须能放入画布" }
        actions.forEach { (id, definition) ->
            if (definition.type == "page")
                require(definition.value in pages) { "actions.$id.value: 页面不存在" }
        }
        val common = widgets(root, "common")
        val navigation = section(root, "navigation")
        keys(navigation, setOf("x", "row", "step"), "navigation")
        val navX = number(navigation, "x", 0, "navigation")
        val navRow = number(navigation, "row", 3, "navigation")
        val navStep = number(navigation, "step", 2, "navigation")
        require(
            navX >= 0 &&
                navX + 102 <= DialogCanvas.WIDTH &&
                navRow >= 0 &&
                navStep >= 2 &&
                navRow + (pages.size - 1) * navStep + 2 <= DialogCanvas.ROWS
        ) {
            "navigation: 导航超出画布或按钮重叠"
        }
        // Prevent conflicting hit areas instead of silently routing a click to an obscured control.
        data class Area(val x: Int, val row: Int, val width: Int, val rows: Int)
        val navigationAreas =
            pages.keys.mapIndexed { index, _ -> Area(navX, navRow + index * navStep, 102, 2) }
        pages.values.forEach { page ->
            val areas =
                navigationAreas +
                    (common + page.widgets)
                        .filter {
                            it.action.isNotEmpty() ||
                                it.kind in setOf(WidgetKind.SLIDER, WidgetKind.DROPDOWN)
                        }
                        .map {
                            Area(
                                it.x,
                                it.row,
                                it.width,
                                if (it.kind == WidgetKind.SPRITE) skins.getValue(it.skin).rows
                                else if (it.kind in setOf(WidgetKind.TEXT, WidgetKind.HEADING)) 1
                                else 2,
                            )
                        }
            areas.forEachIndexed { i, a ->
                areas.drop(i + 1).forEach { b ->
                    require(
                        a.x + a.width <= b.x ||
                            b.x + b.width <= a.x ||
                            a.row + a.rows <= b.row ||
                            b.row + b.rows <= a.row
                    ) {
                        "pages.${page.id}: 点击区域重叠，请检查 x / row"
                    }
                }
            }
        }
        val defaults = section(root, "defaults")
        keys(defaults, setOf("language", "theme"), "defaults")
        val language = string(defaults, "language")
        val theme = string(defaults, "theme")
        require(
            MenuLanguage.entries.any { it.id == language } &&
                MenuTheme.entries.any { it.id == theme }
        ) {
            "defaults: 语言或主题无效"
        }
        val defaultPage = string(root, "default-page")
        require(defaultPage in pages) { "default-page: 页面不存在 $defaultPage" }
        val footer = section(root, "footer")
        keys(footer, setOf("label", "action"), "footer")
        return MenuDefinition(
            text(string(root, "title"), "title"),
            defaultPage,
            MenuLanguage.parse(language),
            MenuTheme.parse(theme),
            root.getBoolean("hide-focus-outline", true),
            navX,
            navRow,
            navStep,
            text(string(footer, "label"), "footer.label"),
            action(string(footer, "action"), "footer.action"),
            states,
            actions,
            common,
            pages,
            translations,
        )
    }

    private fun yaml(source: String, path: String): YamlConfiguration {
        require(source.length <= 1_048_576) { "$path: 文件超过 1 MiB" }
        return try {
            // Bukkit accepts duplicate keys by default. Reject them before
            // converting into sections so an accidental second pages/actions
            // block cannot silently replace the administrator's first block.
            val options = LoaderOptions()
            options.setAllowDuplicateKeys(false)
            options.setMaxAliasesForCollections(0)
            options.setNestingDepthLimit(40)
            options.setCodePointLimit(1_048_576)
            Yaml(SafeConstructor(options)).load<Any?>(source)
            YamlConfiguration().apply { loadFromString(source) }
        } catch (error: Exception) {
            throw IllegalArgumentException("$path: ${error.message}", error)
        }
    }

    private fun keys(conf: ConfigurationSection, allowed: Set<String>, path: String) {
        val unknown = conf.getKeys(false) - allowed
        require(unknown.isEmpty()) { "$path: 未知字段 $unknown" }
    }

    private fun identifier(value: String, path: String) {
        require(value.matches(Regex("[a-z][a-z0-9_-]{0,47}"))) {
            "$path: ID 只能使用小写字母、数字、-、_（以字母开头）"
        }
    }

    private fun line(value: String, path: String) {
        require(value.none { it.isISOControl() }) { "$path: 不支持换行或控制字符" }
    }

    private fun section(conf: ConfigurationSection, key: String) =
        conf.getConfigurationSection(key) ?: error("${conf.currentPath}.$key: 缺少配置段")

    private fun string(conf: ConfigurationSection, key: String) =
        (conf.get(key) as? String)?.takeIf { it.isNotBlank() }
            ?: error("${conf.currentPath}.$key: 需要非空字符串（off/on 请加引号）")

    private fun number(conf: ConfigurationSection, key: String, default: Int?, path: String): Int {
        if (!conf.contains(key) && default != null) return default
        require(conf.isInt(key)) { "$path.$key: 需要整数" }
        return conf.getInt(key)
    }
}

class MenuRepository(private val directory: File) {
    var current: MenuDefinition = bundled()
        private set

    fun initialize() {
        listOf("menu.yml", "languages/zh_cn.yml", "languages/en_us.yml", "配置说明.md").forEach { name
            ->
            val file = File(directory, name)
            if (!file.exists()) {
                file.parentFile.mkdirs()
                file.writeText(resource(name), Charsets.UTF_8)
            }
        }
        reload()
    }

    fun reload() {
        current = readDefinition()
    }

    fun install(definition: MenuDefinition) {
        current = definition
    }

    fun readDefinition(): MenuDefinition {
        fun read(name: String): String {
            val file = File(directory, name)
            require(file.length() <= 1_048_576) { "$name: 文件超过 1 MiB" }
            return file.readText(Charsets.UTF_8)
        }
        return MenuConfigParser.parse(
            read("menu.yml"),
            MenuLanguage.entries.associateWith { read("languages/${it.id}.yml") },
        )
    }

    companion object {
        private fun resource(name: String) =
            requireNotNull(MenuRepository::class.java.getResourceAsStream("/$name")) {
                    "缺少默认配置 $name"
                }
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

        fun bundled() =
            MenuConfigParser.parse(
                resource("menu.yml"),
                MenuLanguage.entries.associateWith { resource("languages/${it.id}.yml") },
            )
    }
}
