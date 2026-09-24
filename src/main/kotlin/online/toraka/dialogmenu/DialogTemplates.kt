package online.toraka.dialogmenu

import java.io.File
import java.util.Properties
import net.kyori.adventure.key.Key
import org.bukkit.configuration.ConfigurationSection

data class TemplateElement(
    val id: String,
    val type: String,
    val x: Int,
    val row: Int,
    val width: Int,
    val rows: Int,
    val lines: List<String>,
    val sprite: DialogCanvas.Skin?,
    val selectedSprite: DialogCanvas.Skin?,
    val color: Int,
    val condition: Pair<String, String>?,
    val selected: Pair<String, String>?,
    val permission: String,
    val actions: List<String>,
)

data class DialogTemplate(
    val id: String,
    val title: String,
    val width: Int,
    val rows: Int,
    val hideFocus: Boolean,
    val background: DialogCanvas.Skin?,
    val variables: Map<String, List<String>>,
    val elements: List<TemplateElement>,
) {
    fun values(previous: Map<String, String>) = variables.mapValues { (key, options) ->
        previous[key]?.takeIf { it in options } ?: options.first()
    }
}

object TemplateSkins {
    private val questMetrics =
        Properties().apply {
            requireNotNull(TemplateSkins::class.java.getResourceAsStream("/quest-skins.properties"))
                .use { load(it) }
        }
    private val metrics =
        Properties().apply {
            requireNotNull(
                    TemplateSkins::class.java.getResourceAsStream("/template-skins.properties")
                )
                .use {
                    load(it)
                }
        }
    val font = Key.key("toraka_dialogue:ui")

    fun get(theme: String, name: String): DialogCanvas.Skin {
        val quest = name.startsWith("quest-")
        val parts =
            requireNotNull((if (quest) questMetrics else metrics).getProperty("$theme.$name")) {
                    "未知贴图 $theme.$name"
                }
                .split(',')
                .map(String::toInt)
        return DialogCanvas.Skin(
            parts[0],
            parts[1],
            parts[2],
            if (quest) Key.key("toraka_dialogue:quest_ui") else font,
            parts.drop(4),
            parts[3],
        )
    }
}

object TemplateParser {
    private val idPattern = Regex("[a-z][a-z0-9_-]{0,47}")
    private val valuePattern = Regex("[a-zA-Z0-9_-]{1,48}")

    fun parse(id: String, source: String, path: String = "templates/$id.yml"): DialogTemplate {
        require(id.split('/').size <= 2 && id.split('/').all { it.matches(idPattern) }) {
            "无效菜单页面 ID $id"
        }
        val root = MenuConfigParser.yaml(source, path)
        keys(root, setOf("Version", "Title", "Skin", "Canvas", "Variables", "Elements"), path)
        require(root.get("Version") == 1) { "$path.Version: 必须为 1" }
        val title = line(root.getString("Title") ?: id, path)
        val theme = root.getString("Skin", "amethyst")!!
        require(theme in setOf("amethyst", "parchment")) { "$path.Skin: amethyst 或 parchment" }
        val geometry = root.getConfigurationSection("Canvas")
        geometry?.let {
            keys(it, setOf("Width", "Rows", "Background", "HideFocusOutline"), "$path.Canvas")
        }
        val width = integer(geometry, "Width", 552, 180..960, path)
        val rows = integer(geometry, "Rows", 20, 8..28, path)
        require(
            geometry?.contains("HideFocusOutline") != true || geometry.isBoolean("HideFocusOutline")
        ) {
            "$path.Canvas.HideFocusOutline: 需要 true/false"
        }
        val hide = geometry?.getBoolean("HideFocusOutline", true) ?: true
        require(!hide || width == 552 && rows == 20) {
            "$path.Canvas: 隐藏焦点框只支持默认 552 × 20 行；修改尺寸时将 HideFocusOutline 设为 false"
        }
        val backgroundName = geometry?.getString("Background", "panel") ?: "panel"
        val background =
            if (backgroundName == "none") null else TemplateSkins.get(theme, backgroundName)
        require(background == null || background.width <= width && background.rows <= rows) {
            "$path: 背景超出画布"
        }
        val variables =
            root.getConfigurationSection("Variables")?.getKeys(false)?.associateWith { key ->
                require(key.matches(idPattern) && key !in setOf("player", "uuid")) {
                    "$path.Variables: 无效变量 $key"
                }
                strings(root.get("Variables.$key"), "$path.Variables.$key").also { values ->
                    require(
                        values.isNotEmpty() &&
                            values.size <= 16 &&
                            values.distinct().size == values.size &&
                            values.all { it.matches(valuePattern) }
                    ) {
                        "$path.Variables.$key: 需要不重复的字母/数字/-/_枚举值，首项为默认值"
                    }
                }
            } ?: emptyMap()
        fun condition(value: String?): Pair<String, String>? = value?.let {
            val pair = it.substringBefore('=').trim() to it.substringAfter('=', "").trim()
            require(pair.second in variables[pair.first].orEmpty()) { "$path: 无效变量条件 $it" }
            pair
        }
        val section =
            requireNotNull(root.getConfigurationSection("Elements")) { "$path: 缺少 Elements" }
        require(section.getKeys(false).size in 1..64) { "$path.Elements: 需要 1–64 个元素" }
        val elements =
            section.getKeys(false).map { name ->
                require(name.matches(idPattern)) { "$path.Elements: 元素 ID 使用小写英文 $name" }
                val conf =
                    requireNotNull(section.getConfigurationSection(name)) { "$path.$name: 需要配置段" }
                val location = "$path.Elements.$name"
                keys(
                    conf,
                    setOf(
                        "Type",
                        "Position",
                        "Width",
                        "Rows",
                        "Text",
                        "Color",
                        "Sprite",
                        "SelectedSprite",
                        "VisibleWhen",
                        "SelectedWhen",
                        "Permission",
                        "Actions",
                        "Font",
                        "Glyph",
                        "Advance",
                    ),
                    location,
                )
                val type = conf.getString("Type", "text")!!
                require(type in setOf("text", "button", "sprite")) {
                    "$location.Type: text/button/sprite"
                }
                val position = conf.getList("Position")
                require(
                    position != null && position.size == 2 && position.all { it is Int && it >= 0 }
                ) {
                    "$location.Position: [横向像素, 纵向行号]，每行 9 像素"
                }
                val x = position[0] as Int
                val row = position[1] as Int
                val customFont = conf.getString("Font")
                val sprite =
                    if (customFont != null) {
                        require(type == "sprite") { "$location.Font: 仅用于 sprite" }
                        val glyph = conf.getString("Glyph").orEmpty()
                        require(glyph.length == 1 && !glyph[0].isSurrogate()) {
                            "$location.Glyph: 需要单个 BMP 字符，可写 Unicode 转义"
                        }
                        val spriteWidth = integer(conf, "Width", 108, 1..256, location)
                        val spriteRows = integer(conf, "Rows", 12, 1..28, location)
                        DialogCanvas.Skin(
                            glyph[0].code,
                            spriteWidth,
                            spriteRows,
                            Key.key(customFont),
                            listOf(integer(conf, "Advance", spriteWidth + 1, 0..1024, location)),
                            1,
                        )
                    } else if (type != "text")
                        TemplateSkins.get(
                            theme,
                            conf.getString(
                                "Sprite",
                                if (type == "button") "button" else "emblem",
                            )!!,
                        )
                    else null
                val elementWidth =
                    sprite?.width ?: integer(conf, "Width", width - x - 12, 1..960, location)
                val elementRows = sprite?.rows ?: integer(conf, "Rows", 1, 1..28, location)
                require(x + elementWidth <= width && row + elementRows <= rows) {
                    "$location: 元素超出画布"
                }
                val rawText = conf.get("Text")
                val lines =
                    when (rawText) {
                        null -> emptyList()
                        is String -> listOf(line(rawText, location))
                        else -> strings(rawText, location).map { line(it, location) }
                    }
                require(type != "button" || lines.size == 1) { "$location.Text: 按钮需要单行文字" }
                val actions = conf.get("Actions")?.let { strings(it, location) } ?: emptyList()
                require(actions.size <= 16 && (type == "button" || actions.isEmpty())) {
                    "$location.Actions: 仅用于 button，最多 16 条"
                }
                require(type != "button" || actions.isNotEmpty()) { "$location.Actions: 按钮需要动作" }
                actions.forEachIndexed { index, action ->
                    val verb = action.substringBefore(':').trim()
                    val argument = action.substringAfter(':', "").trim()
                    when (verb) {
                        "set" -> require(condition(argument) != null)
                        "template" ->
                            require(
                                argument.split('/').size <= 2 &&
                                    argument.split('/').all { it.matches(idPattern) }
                            ) {
                                "$location: 无效目标页面"
                            }
                        "message" -> require(argument.isNotBlank()) { "$location: 消息不能为空" }
                        "command",
                        "console" -> {
                            require(
                                argument.isNotBlank() &&
                                    !argument.startsWith('/') &&
                                    argument.length <= 512 &&
                                    '%' !in argument
                            ) {
                                "$location: 指令不加 /，最长 512 字符，不支持 PAPI 指令替换"
                            }
                            Regex("\\{([^}]+)}").findAll(argument).forEach {
                                require(
                                    it.groupValues[1] in variables ||
                                        it.groupValues[1] in setOf("player", "uuid")
                                ) {
                                    "$location: 未声明的指令变量 " + it.value
                                }
                            }
                        }
                        "close",
                        "refresh" -> require(argument.isEmpty())
                        else -> error("$location: 不支持动作 $verb")
                    }
                    require(
                        verb !in setOf("close", "template", "refresh") || index == actions.lastIndex
                    ) {
                        "$location: close/template/refresh 必须为最后一项"
                    }
                }
                val selectedSprite =
                    conf.getString("SelectedSprite")?.let { TemplateSkins.get(theme, it) }
                require(
                    selectedSprite == null ||
                        sprite != null &&
                            selectedSprite.width == sprite.width &&
                            selectedSprite.rows == sprite.rows
                ) {
                    "$location: 选中贴图尺寸必须相同"
                }
                val color = conf.getString("Color", "#e7deed")!!
                require(color.matches(Regex("#[a-fA-F0-9]{6}"))) { "$location.Color: #RRGGBB" }
                TemplateElement(
                    name,
                    type,
                    x,
                    row,
                    elementWidth,
                    elementRows,
                    lines,
                    sprite,
                    selectedSprite,
                    color.drop(1).toInt(16),
                    condition(conf.getString("VisibleWhen")),
                    condition(conf.getString("SelectedWhen")),
                    conf.getString("Permission", "")!!,
                    actions,
                )
            }
        val buttons = elements.filter { it.type == "button" }
        buttons.forEachIndexed { index, a ->
            buttons.drop(index + 1).forEach { b ->
                val exclusive =
                    a.condition != null &&
                        b.condition != null &&
                        a.condition.first == b.condition.first &&
                        a.condition.second != b.condition.second
                require(
                    exclusive ||
                        a.x >= b.x + b.width ||
                        b.x >= a.x + a.width ||
                        a.row >= b.row + b.rows ||
                        b.row >= a.row + a.rows
                ) {
                    "$path: 按钮点击区域重叠 " + a.id + "/" + b.id
                }
            }
        }
        return DialogTemplate(id, title, width, rows, hide, background, variables, elements)
    }

    fun validateLinks(templates: Map<String, DialogTemplate>) {
        templates.values.forEach { template ->
            template.elements.forEach { element ->
                element.actions.forEach {
                    if (it.startsWith("template:"))
                        require(it.substringAfter(':').trim() in templates) {
                            "templates/" + template.id + ".yml: 目标模板不存在 $it"
                        }
                }
            }
        }
    }

    private fun strings(raw: Any?, path: String): List<String> {
        require(raw is List<*> && raw.all { it is String }) { "$path: 需要字符串列表（on/off 请加引号）" }
        return raw.filterIsInstance<String>().onEach { line(it, path) }
    }

    private fun line(text: String, path: String): String {
        require(text.length <= 2048 && text.none { it.isISOControl() }) { "$path: 需要不含控制字符的单行文字" }
        return text
    }

    private fun keys(conf: ConfigurationSection, allowed: Set<String>, path: String) {
        require((conf.getKeys(false) - allowed).isEmpty()) {
            "$path: 未知字段 " + (conf.getKeys(false) - allowed)
        }
    }

    private fun integer(
        conf: ConfigurationSection?,
        key: String,
        default: Int,
        range: IntRange,
        path: String,
    ): Int {
        val raw = conf?.get(key) ?: default
        require(raw is Int && raw in range) { "$path.$key: 需要 $range 范围内整数" }
        return raw
    }
}

class TemplateRepository(private val directory: File) {
    var current: Map<String, DialogTemplate> = emptyMap()
        private set

    fun initialize() {
        val folder = File(directory, "templates")
        if (!folder.exists()) {
            folder.mkdirs()
            defaults.forEach { id ->
                File(folder, "$id.yml")
                    .writeText(MenuRepository.resource("templates/$id.yml"), Charsets.UTF_8)
            }
        }
        install(read())
    }

    fun read(): Map<String, DialogTemplate> {
        val folder = File(directory, "templates")
        require(folder.isDirectory) { "templates: 目录不存在" }
        val files =
            folder.listFiles { f -> f.isFile && f.extension == "yml" }!!.sortedBy { it.name }
        require(files.size <= 64) { "templates: 最多 64 个模板" }
        return files
            .associate { file ->
                require(file.length() <= 1_048_576) { file.name + ": 超过 1 MiB" }
                file.nameWithoutExtension to
                    TemplateParser.parse(file.nameWithoutExtension, file.readText(Charsets.UTF_8))
            }
            .also(TemplateParser::validateLinks)
    }

    fun install(next: Map<String, DialogTemplate>) {
        current = next
    }

    companion object {
        val defaults = listOf("npc-dialogue", "boss-intro", "boss-confirm")
    }
}
