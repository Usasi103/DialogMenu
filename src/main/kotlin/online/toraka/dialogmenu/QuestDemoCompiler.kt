package online.toraka.dialogmenu

import java.util.Properties
import net.kyori.adventure.key.Key
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/** Compile a small editable demo catalog to ordinary canvas pages and session variables. */
object QuestDemoCompiler {
    private val identifier = Regex("[a-z][a-z0-9_-]{0,19}")
    private val icons =
        Properties().apply {
            requireNotNull(
                    QuestDemoCompiler::class.java.getResourceAsStream("/quest-icons.properties")
                )
                .use { load(it) }
        }

    private data class Position(val x: Int, val row: Int)

    private data class Icon(val font: String, val glyph: String, val width: Int, val advance: Int)

    private data class Reward(val text: String, val icon: Icon)

    private data class Task(
        val id: String,
        val name: String,
        val category: String,
        val description: String,
        val objective: String,
        val current: Int,
        val total: Int,
        val claimed: Boolean,
        val icon: Icon,
        val rewards: List<Reward>,
    ) {
        val complete: Boolean
            get() = current == total

        val variable: String
            get() = "claim-$id"
    }

    fun compile(root: ConfigurationSection, path: String): YamlConfiguration {
        keys(
            root,
            setOf(
                "Version",
                "Type",
                "Title",
                "Subtitle",
                "Skin",
                "HideFocusOutline",
                "PageSize",
                "Categories",
                "Layout",
                "Tasks",
            ),
            path,
        )
        require(root.get("Version") == 1) { "$path.Version: 必须为 1" }
        val pageSize = integer(root.get("PageSize") ?: 5, 1..5, "$path.PageSize")
        val categories = section(root, "Categories", path)
        require(categories.getKeys(false).size in 1..3) { "$path.Categories: 需要 1–3 个分类" }
        val names = linkedMapOf("all" to "全部")
        for (id in categories.getKeys(false)) {
            require(id.matches(identifier) && id !in setOf("all", "completed")) {
                "$path.Categories: 无效分类 ID $id"
            }
            names[id] = text(categories.get(id), "$path.Categories.$id", 4)
        }
        names["completed"] = "已完成"
        val taskSection = section(root, "Tasks", path)
        require(taskSection.getKeys(false).size in 1..15) { "$path.Tasks: 需要 1–15 个演示任务" }
        val tasks =
            taskSection.getKeys(false).map { id ->
                require(id.matches(identifier) && id != "none") { "$path.Tasks: 无效任务 ID $id" }
                val at = "$path.Tasks.$id"
                val value = section(taskSection, id, "$path.Tasks")
                keys(
                    value,
                    setOf(
                        "Name",
                        "Category",
                        "Description",
                        "Objective",
                        "Progress",
                        "Claimed",
                        "Icon",
                        "Rewards",
                    ),
                    at,
                )
                val category = value.getString("Category")
                require(category != null && category in categories.getKeys(false)) {
                    "$at.Category: 分类不存在"
                }
                val progress = value.getList("Progress")
                require(progress != null && progress.size == 2) { "$at.Progress: [当前进度, 目标数量]" }
                val total = integer(progress[1], 1..1_000_000, "$at.Progress[1]")
                val current = integer(progress[0], 0..total, "$at.Progress[0]")
                require(!value.contains("Claimed") || value.isBoolean("Claimed")) {
                    "$at.Claimed: 需要 true/false"
                }
                val claimed = value.getBoolean("Claimed", false)
                require(!claimed || current == total) { "$at.Claimed: 未完成目标不能标记已领取" }
                val rewardList = value.getList("Rewards")
                require(rewardList != null && rewardList.size in 1..3) { "$at.Rewards: 需要 1–3 项奖励" }
                val rewards = rewardList.mapIndexed { index, raw ->
                    val reward = mapping(raw, "$at.Rewards[$index]")
                    require(reward.keys == setOf("Text", "Icon")) {
                        "$at.Rewards[$index]: 使用 Text 和 Icon"
                    }
                    Reward(
                        text(reward["Text"], "$at.Rewards[$index].Text", 30),
                        icon(reward["Icon"], "$at.Rewards[$index].Icon"),
                    )
                }
                Task(
                    id,
                    text(value.get("Name"), "$at.Name", 20),
                    category,
                    text(value.get("Description"), "$at.Description", 256),
                    text(value.get("Objective"), "$at.Objective", 80),
                    current,
                    total,
                    claimed,
                    icon(value.get("Icon"), "$at.Icon"),
                    rewards,
                )
            }
        val layout = root.getConfigurationSection("Layout")
        require(!root.contains("Layout") || layout != null) { "$path.Layout: 需要配置段" }
        layout?.let {
            keys(it, setOf("List", "Categories", "Detail", "Pagination"), "$path.Layout")
        }
        fun position(name: String, x: Int, row: Int): Position {
            val raw = layout?.get(name) ?: return Position(x, row)
            require(raw is List<*> && raw.size == 2) { "$path.Layout.$name: [横向像素, 纵向行号]" }
            return Position(
                integer(raw[0], 0..551, "$path.Layout.$name[0]"),
                integer(raw[1], 0..19, "$path.Layout.$name[1]"),
            )
        }
        val list = position("List", 16, 6)
        val tabs = position("Categories", 16, 3)
        val detail = position("Detail", 222, 6)
        val pagination = position("Pagination", 16, 17)
        val result = YamlConfiguration()
        result.set("Version", 1)
        result.set("Type", "canvas")
        result.set("Title", text(root.get("Title") ?: "任务列表", "$path.Title", 40))
        result.set("Skin", root.get("Skin") ?: "amethyst")
        result.set(
            "Canvas",
            mapOf(
                "Width" to 552,
                "Rows" to 20,
                "Background" to "panel",
                "HideFocusOutline" to (root.get("HideFocusOutline") ?: true),
            ),
        )
        result.set("Variables.tracked", listOf("none") + tasks.map { it.id })
        tasks.forEach { task ->
            result.set(
                "Variables.${task.variable}",
                if (task.claimed) listOf("claimed", "ready") else listOf("ready", "claimed"),
            )
        }
        fun page(category: String, task: Task?) = "$category-${task?.id ?: "empty"}"
        fun inCategory(task: Task, category: String): Boolean =
            when (category) {
                "all" -> true
                "completed" -> task.complete
                else -> task.category == category && !task.complete
            }
        result.set("DefaultPage", page("all", tasks.first()))
        for ((category, label) in names) {
            val filtered = tasks.filter { inCategory(it, category) }
            val selections: List<Task?> = filtered.ifEmpty { listOf(null) }
            for ((index, chosen) in selections.withIndex()) {
                val pageIndex = index / pageSize
                val visible = filtered.drop(pageIndex * pageSize).take(pageSize)
                val elements = result.createSection("Pages.${page(category, chosen)}.Elements")
                fun element(id: String, type: String, x: Int, row: Int, fields: Map<String, Any>) {
                    val value = elements.createSection(id)
                    value.set("Type", type)
                    value.set("Position", listOf(x, row))
                    fields.forEach { (key, field) -> value.set(key, field) }
                }
                fun label(
                    id: String,
                    x: Int,
                    row: Int,
                    width: Int,
                    value: String,
                    color: String = "#d9bfef",
                    rows: Int = 1,
                    condition: String? = null,
                ) {
                    val fields =
                        linkedMapOf<String, Any>(
                            "Width" to width,
                            "Rows" to rows,
                            "Text" to value,
                            "Color" to color,
                        )
                    if (condition != null) fields["VisibleWhen"] = condition
                    element(id, "text", x, row, fields)
                }
                fun button(
                    id: String,
                    x: Int,
                    row: Int,
                    sprite: String,
                    value: String,
                    actions: List<String>,
                    condition: String? = null,
                    selected: String? = null,
                ) {
                    val fields =
                        linkedMapOf<String, Any>(
                            "Sprite" to sprite,
                            "Text" to value,
                            "Actions" to actions,
                        )
                    if (condition != null) fields["VisibleWhen"] = condition
                    if (selected != null) {
                        fields["SelectedWhen"] = selected
                        fields["SelectedSprite"] = "selected"
                    }
                    element(id, "button", x, row, fields)
                }
                fun sprite(id: String, x: Int, row: Int, name: String) =
                    element(id, "sprite", x, row, mapOf("Sprite" to name))
                fun picture(id: String, x: Int, row: Int, icon: Icon) =
                    element(
                        id,
                        "sprite",
                        x,
                        row,
                        mapOf(
                            "Font" to icon.font,
                            "Glyph" to icon.glyph,
                            "Width" to icon.width,
                            "Rows" to 2,
                            "Advance" to icon.advance,
                        ),
                    )
                label("heading", 16, 1, 300, result.getString("Title")!!)
                label(
                    "subtitle",
                    318,
                    3,
                    210,
                    text(root.get("Subtitle") ?: "选择任务，查看目标与奖励", "$path.Subtitle", 80),
                    "#a28ab6",
                )
                button("close", 522, 1, "close", "×", listOf("close"))
                names.entries.forEachIndexed { tabIndex, entry ->
                    val first = tasks.firstOrNull { inCategory(it, entry.key) }
                    button(
                        "category-${entry.key}",
                        tabs.x + tabIndex * 60,
                        tabs.row,
                        if (entry.key == category) "quest-tab-selected" else "quest-tab",
                        entry.value,
                        listOf("page: ${page(entry.key, first)}"),
                    )
                }
                sprite("divider", detail.x - 18, list.row, "quest-divider")
                visible.forEachIndexed { slot, task ->
                    val row = list.row + slot * 2
                    val style = if (task.id == chosen?.id) "quest-row-selected" else "quest-row"
                    val action = listOf("page: ${page(category, task)}")
                    if (task.complete) {
                        button(
                            "task-${task.id}",
                            list.x,
                            row,
                            style,
                            "${task.name} · 可领取",
                            action,
                            "${task.variable}=ready",
                        )
                        button(
                            "done-${task.id}",
                            list.x,
                            row,
                            style,
                            "${task.name} · 已完成",
                            action,
                            "${task.variable}=claimed",
                        )
                    } else
                        button(
                            "task-${task.id}",
                            list.x,
                            row,
                            style,
                            "${task.name} · ${task.current}/${task.total}",
                            action,
                        )
                    picture("icon-${task.id}", list.x + 6, row, task.icon)
                }
                val pages = maxOf(1, (filtered.size + pageSize - 1) / pageSize)
                label(
                    "page-number",
                    pagination.x + 72,
                    pagination.row + 1,
                    45,
                    "${pageIndex+1} / $pages",
                    "#af90c8",
                )
                if (pageIndex > 0)
                    button(
                        "previous",
                        pagination.x,
                        pagination.row,
                        "quest-tab",
                        "上一页",
                        listOf("page: ${page(category, filtered[(pageIndex - 1) * pageSize])}"),
                    )
                if (pageIndex + 1 < pages)
                    button(
                        "next",
                        pagination.x + 126,
                        pagination.row,
                        "quest-tab",
                        "下一页",
                        listOf("page: ${page(category, filtered[(pageIndex + 1) * pageSize])}"),
                    )
                if (chosen == null) {
                    label("empty-list", list.x + 24, list.row + 4, 145, "此分类暂无任务", "#a28ab6")
                    label("empty-detail", detail.x, detail.row + 3, 300, "请切换到其他任务分类。", "#a28ab6")
                    continue
                }
                label("task-title", detail.x, detail.row, 220, chosen.name)
                label(
                    "description",
                    detail.x,
                    detail.row + 2,
                    312,
                    chosen.description,
                    "#a28ab6",
                    2,
                )
                label("objective", detail.x, detail.row + 4, 232, chosen.objective)
                label(
                    "progress-value",
                    detail.x + 237,
                    detail.row + 4,
                    75,
                    "${chosen.current}/${chosen.total}",
                )
                sprite(
                    "progress-bar",
                    detail.x,
                    detail.row + 5,
                    "quest-progress-${chosen.current.toLong() * 20 / chosen.total}",
                )
                label("rewards-heading", detail.x, detail.row + 7, 160, "任务奖励", "#af90c8")
                chosen.rewards.forEachIndexed { rewardIndex, reward ->
                    picture(
                        "reward-$rewardIndex",
                        detail.x + rewardIndex * 104,
                        detail.row + 8,
                        reward.icon,
                    )
                    label(
                        "reward-label-$rewardIndex",
                        detail.x + rewardIndex * 104 + 17,
                        detail.row + 9,
                        86,
                        reward.text,
                    )
                }
                if (chosen.complete) {
                    label(
                        "status-ready",
                        detail.x + 249,
                        detail.row,
                        63,
                        "可领取",
                        "#dcc784",
                        condition = "${chosen.variable}=ready",
                    )
                    label(
                        "status-claimed",
                        detail.x + 249,
                        detail.row,
                        63,
                        "已完成",
                        "#91ac99",
                        condition = "${chosen.variable}=claimed",
                    )
                    button(
                        "claim",
                        detail.x + 204,
                        detail.row + 11,
                        "button",
                        "领取奖励",
                        listOf(
                            "set: ${chosen.variable}=claimed",
                            "message: 演示：已领取 ${chosen.name} 的奖励（不会发放真实物品）。",
                            "refresh",
                        ),
                        "${chosen.variable}=ready",
                    )
                    label(
                        "claimed",
                        detail.x + 218,
                        detail.row + 12,
                        94,
                        "已领取奖励",
                        "#91ac99",
                        condition = "${chosen.variable}=claimed",
                    )
                } else {
                    label("status", detail.x + 249, detail.row, 63, "进行中", "#af90c8")
                    button(
                        "track",
                        detail.x + 204,
                        detail.row + 11,
                        "button",
                        "追踪任务",
                        listOf("set: tracked=${chosen.id}", "refresh"),
                        selected = "tracked=${chosen.id}",
                    )
                    button(
                        "untrack",
                        detail.x + 90,
                        detail.row + 11,
                        "button",
                        "取消追踪",
                        listOf("set: tracked=none", "refresh"),
                        "tracked=${chosen.id}",
                    )
                }
            }
        }
        return result
    }

    private fun icon(raw: Any?, path: String): Icon {
        if (raw is String) {
            val data =
                requireNotNull(icons.getProperty(raw)) { "$path: 未知图标 $raw" }
                    .split(',')
                    .map(String::toInt)
            return Icon("dialogmenu_dialogue:quest_items", data[0].toChar().toString(), 12, data[1])
        }
        val value = mapping(if (raw is ConfigurationSection) raw.getValues(false) else raw, path)
        require(value.keys.all { it in setOf("Font", "Glyph", "Width", "Advance") }) {
            "$path: 图标使用 Font/Glyph/Width/Advance"
        }
        val font = text(value["Font"], "$path.Font", 120)
        Key.key(font)
        val glyph = text(value["Glyph"], "$path.Glyph", 1)
        require(!glyph[0].isSurrogate()) { "$path.Glyph: 需要单个 BMP 字符" }
        return Icon(
            font,
            glyph,
            integer(value["Width"] ?: 12, 1..18, "$path.Width"),
            integer(value["Advance"], 0..1024, "$path.Advance"),
        )
    }

    private fun keys(value: ConfigurationSection, allowed: Set<String>, path: String) {
        require(value.getKeys(false).all { it in allowed }) {
            "$path: 未知字段 ${value.getKeys(false) - allowed}"
        }
    }

    private fun section(
        value: ConfigurationSection,
        name: String,
        path: String,
    ): ConfigurationSection =
        requireNotNull(value.getConfigurationSection(name)) { "$path.$name: 需要配置段" }

    private fun text(raw: Any?, path: String, max: Int): String {
        require(
            raw is String && raw.isNotBlank() && raw.length <= max && raw.none { it.isISOControl() }
        ) {
            "$path: 需要 1–$max 字符的单行文字"
        }
        return raw
    }

    private fun integer(raw: Any?, range: IntRange, path: String): Int {
        require(raw is Int && raw in range) { "$path: 需要 $range 范围内整数" }
        return raw
    }

    private fun mapping(raw: Any?, path: String): Map<*, *> {
        require(raw is Map<*, *>) { "$path: 需要配置段" }
        return raw
    }
}
