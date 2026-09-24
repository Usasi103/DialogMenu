package online.toraka.dialogmenu

import java.io.File
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/** A file is a menu; page IDs are local to that menu. */
data class CatalogMenu(
    val id: String,
    val defaultPage: String,
    val settings: MenuDefinition?,
    val canvas: Map<String, DialogTemplate>,
) {
    val pages: Set<String>
        get() = settings?.pages?.keys ?: canvas.keys
}

data class MenuCatalog(
    val defaultMenu: String,
    val menus: Map<String, CatalogMenu>,
    val resourcePack: MenuResourcePack = MenuResourcePack.legacy,
) {
    val templates = menus.values.flatMap { it.canvas.values }.associateBy { it.id }
}

object MenuCatalogParser {
    private val identifier = Regex("[a-z][a-z0-9_-]{0,47}")

    fun parse(configSource: String, sources: Map<String, String>): MenuCatalog {
        val config = MenuConfigParser.yaml(configSource, "config.yml")
        keys(config, setOf("Version", "DefaultMenu", "ResourcePack"), "config.yml")
        require(!config.contains("ResourcePack") || config.isConfigurationSection("ResourcePack")) {
            "config.yml.ResourcePack: 需要配置段"
        }
        val resourcePack = MenuResourcePack.parse(config.getConfigurationSection("ResourcePack"))
        require(config.get("Version") == 3) { "config.yml.Version: 必须为 3" }
        require(sources.size in 1..64) { "menus: 需要 1–64 个菜单文件" }
        val roots = sources.mapValues { (id, source) ->
            require(id.matches(identifier)) { "menus: 无效菜单文件名 $id" }
            MenuConfigParser.yaml(source, "menus/$id.yml")
        }
        val defaults = roots.mapValues { (id, root) ->
            val pages =
                requireNotNull(root.getConfigurationSection("Pages")) {
                    "menus/$id.yml: 缺少 Pages 配置段"
                }
            require(pages.getKeys(false).size in 1..64) { "menus/$id.yml.Pages: 需要 1–64 页" }
            pages.getKeys(false).forEach {
                require(it.matches(identifier)) { "menus/$id.yml.Pages: 无效页面 ID $it" }
                require(pages.isConfigurationSection(it)) { "menus/$id.yml.Pages.$it: 需要配置段" }
            }
            val default = root.getString("DefaultPage") ?: pages.getKeys(false).first()
            require(default in pages.getKeys(false)) { "menus/$id.yml.DefaultPage: 页面不存在 $default" }
            default
        }
        val menus = roots.mapValues { (id, root) ->
            val path = "menus/$id.yml"
            require(root.get("Version") == 1) { "$path.Version: 必须为 1" }
            val pages = root.getConfigurationSection("Pages")!!
            when (root.getString("Type")) {
                "settings" -> {
                    keys(
                        root,
                        setOf(
                            "Version",
                            "Type",
                            "Title",
                            "DefaultPage",
                            "Language",
                            "Theme",
                            "HideFocusOutline",
                            "ShowFooter",
                            "MainMenu",
                            "Pages",
                        ),
                        path,
                    )
                    val legacy = copy(root)
                    legacy.set("Version", 2)
                    legacy.set("Type", null)
                    legacy.set("Pages", pages.getKeys(false).toList())
                    val definition =
                        try {
                            SimpleMenuParser.parse(legacy.saveToString()) { page ->
                                copy(pages.getConfigurationSection(page)!!).saveToString()
                            }
                        } catch (error: Exception) {
                            throw IllegalArgumentException("$path: ${error.message}", error)
                        }
                    CatalogMenu(id, defaults.getValue(id), definition, emptyMap())
                }
                "canvas" -> {
                    keys(
                        root,
                        setOf(
                            "Version",
                            "Type",
                            "Title",
                            "DefaultPage",
                            "Skin",
                            "Canvas",
                            "Variables",
                            "Pages",
                        ),
                        path,
                    )
                    val templates =
                        pages.getKeys(false).associateWith { page ->
                            val section = pages.getConfigurationSection(page)!!
                            keys(
                                section,
                                setOf("Title", "Skin", "Canvas", "Variables", "Elements"),
                                "$path.Pages.$page",
                            )
                            val compiled = YamlConfiguration()
                            compiled.set("Version", 1)
                            for (key in listOf("Title", "Skin", "Canvas", "Variables")) {
                                if (root.contains(key)) put(compiled, key, root.get(key))
                            }
                            section.getValues(false).forEach { (key, value) ->
                                put(compiled, key, value)
                            }
                            val elements = compiled.getConfigurationSection("Elements")
                            elements?.getKeys(false)?.forEach { element ->
                                val at = "Elements.$element.Actions"
                                if (compiled.contains(at)) {
                                    val raw = compiled.getList(at)
                                    require(raw != null && raw.all { it is String }) {
                                        "$path.Pages.$page.$at: 需要字符串列表"
                                    }
                                    compiled.set(
                                        at,
                                        raw.map { action ->
                                            action as String
                                            val verb = action.substringBefore(':').trim()
                                            val target = action.substringAfter(':', "").trim()
                                            when (verb) {
                                                "page" -> {
                                                    require(target in pages.getKeys(false)) {
                                                        "$path.Pages.$page: 页面不存在 $target"
                                                    }
                                                    "template: $id/$target"
                                                }
                                                "menu" -> {
                                                    val destination = roots[target]
                                                    require(
                                                        destination?.getString("Type") == "canvas"
                                                    ) {
                                                        "$path.Pages.$page: menu 目标需要存在且为 canvas 菜单：$target"
                                                    }
                                                    "template: $target/${defaults.getValue(target)}"
                                                }
                                                else -> action
                                            }
                                        },
                                    )
                                }
                            }
                            TemplateParser.parse(
                                "$id/$page",
                                compiled.saveToString(),
                                "$path.Pages.$page",
                            )
                        }
                    CatalogMenu(id, defaults.getValue(id), null, templates)
                }
                else -> error("$path.Type: 使用 settings 或 canvas")
            }
        }
        val default = config.getString("DefaultMenu") ?: "settings"
        require(default in menus) { "config.yml.DefaultMenu: 菜单不存在 $default" }
        return MenuCatalog(default, menus, resourcePack).also {
            TemplateParser.validateLinks(it.templates)
        }
    }

    private fun keys(section: ConfigurationSection, allowed: Set<String>, path: String) {
        require((section.getKeys(false) - allowed).isEmpty()) {
            "$path: 未知字段 ${section.getKeys(false) - allowed}"
        }
    }

    private fun copy(section: ConfigurationSection) =
        YamlConfiguration().also { target ->
            section.getValues(false).forEach { (key, value) -> put(target, key, value) }
        }

    private fun put(target: ConfigurationSection, key: String, value: Any?) {
        if (value is ConfigurationSection) {
            val child = target.createSection(key)
            value.getValues(false).forEach { (name, item) -> put(child, name, item) }
        } else target.set(key, value)
    }
}

class CatalogRepository(private val directory: File) {
    var current: MenuCatalog? = null
        private set

    fun read(): MenuCatalog {
        val folder = File(directory, "menus")
        require(folder.isDirectory) { "menus: 目录不存在" }
        val files =
            requireNotNull(folder.listFiles { file -> file.isFile && file.extension == "yml" })
        require(files.size <= 64) { "menus: 最多 64 个菜单文件" }
        fun read(file: File): String {
            require(file.isFile && file.length() <= 1_048_576) { "${file.name}: 文件不存在或超过 1 MiB" }
            return file.readText(Charsets.UTF_8)
        }
        return MenuCatalogParser.parse(
            read(File(directory, "config.yml")),
            files.sortedBy { it.name }.associate { it.nameWithoutExtension to read(it) },
        )
    }

    fun install(next: MenuCatalog) {
        current = next
    }

    companion object {
        val defaults = listOf("settings", "demo-dialogue", "demo-boss")

        fun selected(directory: File): Boolean {
            val file = File(directory, "config.yml")
            return file.isFile &&
                MenuConfigParser.yaml(file.readText(Charsets.UTF_8), "config.yml").get("Version") ==
                    3
        }

        fun exportIfNew(directory: File) {
            if (File(directory, "config.yml").exists() || File(directory, "menu.yml").exists())
                return
            require(!File(directory, "menus").exists() && !File(directory, "templates").exists()) {
                "已有菜单目录但缺少 config.yml，请补全配置，现有文件不会覆盖"
            }
            (defaults.map { "menus/$it.yml" } + "config.yml").forEach { name ->
                val file = File(directory, name)
                file.parentFile.mkdirs()
                file.writeText(MenuRepository.resource("catalog/$name"), Charsets.UTF_8)
            }
        }
    }
}
