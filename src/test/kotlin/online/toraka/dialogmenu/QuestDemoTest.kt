package online.toraka.dialogmenu

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class QuestDemoTest {
    private val source = MenuRepository.resource("catalog/menus/demo-quests.yml")
    private val config = "Version: 3\nDefaultMenu: demo-quests\n"

    private fun catalog(value: String = source) =
        MenuCatalogParser.parse(config, mapOf("demo-quests" to value))

    private fun visible(
        template: DialogTemplate,
        values: Map<String, String> = template.values(emptyMap()),
    ) = template.elements.filter { TemplateRenderer.visible(it, values) }

    @Test
    fun `five task entries paginate and category routes remain local`() {
        val catalog = catalog()
        assertEquals("all-mine", catalog.menus.getValue("demo-quests").defaultPage)
        val first = catalog.templates.getValue("demo-quests/all-mine")
        val entries =
            visible(first)
                .filter { it.id.startsWith("task-") || it.id.startsWith("done-") }
                .filter { it.type == "button" }
        assertEquals(
            listOf("task-mine", "task-fish", "task-boss", "done-arrival", "task-ore"),
            entries.map { it.id },
        )
        assertEquals(listOf(6, 8, 10, 12, 14), entries.map { it.row })
        assertFalse(first.elements.any { it.id == "previous" })
        assertEquals(
            listOf("template: demo-quests/all-forest"),
            first.elements.single { it.id == "next" }.actions,
        )
        val last = catalog.templates.getValue("demo-quests/all-forest")
        assertEquals(2, visible(last).count { it.type == "button" && it.id.startsWith("task-") })
        assertFalse(last.elements.any { it.id == "next" })
        assertEquals(
            listOf("template: demo-quests/all-mine"),
            last.elements.single { it.id == "previous" }.actions,
        )
        val weekly = catalog.templates.getValue("demo-quests/weekly-boss")
        assertEquals(
            setOf("task-boss", "task-expedition"),
            visible(weekly)
                .filter { it.type == "button" && it.id.startsWith("task-") }
                .map { it.id }
                .toSet(),
        )
    }

    @Test
    fun `claim and tracking state persist across pages but reopening starts fresh`() {
        val catalog = catalog()
        val fish = catalog.templates.getValue("demo-quests/all-fish")
        val initial = fish.values(emptyMap())
        assertTrue(visible(fish, initial).any { it.id == "claim" })
        val claimed = initial + ("claim-fish" to "claimed") + ("tracked" to "mine")
        assertFalse(visible(fish, claimed).any { it.id == "claim" })
        assertTrue(visible(fish, claimed).any { it.id == "claimed" })
        val completedFish = catalog.templates.getValue("demo-quests/completed-fish")
        assertEquals(claimed, completedFish.values(claimed))
        assertEquals("ready", completedFish.values(emptyMap())["claim-fish"])
        assertFalse(visible(completedFish, claimed).any { it.id == "claim" })
        val mine = catalog.templates.getValue("demo-quests/all-mine")
        assertFalse(visible(mine).any { it.id == "untrack" })
        assertTrue(visible(mine, mine.values(claimed)).any { it.id == "untrack" })
        assertFalse(mine.elements.any { it.id == "claim" })
        assertTrue(
            catalog.templates.values
                .flatMap { it.elements }
                .flatMap { it.actions }
                .none { it.startsWith("console:") || it.startsWith("command:") }
        )
    }

    @Test
    fun `completed category includes ready and claimed tasks while named categories exclude both`() {
        val catalog = catalog()
        val completed = catalog.templates.getValue("demo-quests/completed-fish")
        assertEquals(
            listOf(
                "category-all",
                "category-daily",
                "category-weekly",
                "category-story",
                "category-completed",
            ),
            completed.elements.filter { it.id.startsWith("category-") }.map { it.id },
        )
        assertEquals(
            setOf("task-fish", "done-arrival"),
            visible(completed)
                .filter {
                    it.type == "button" && (it.id.startsWith("task-") || it.id.startsWith("done-"))
                }
                .map { it.id }
                .toSet(),
        )
        assertTrue(visible(completed).any { it.id == "claim" })
        val daily = catalog.templates.getValue("demo-quests/daily-mine")
        assertEquals(
            setOf("task-mine", "task-ore", "task-forest"),
            visible(daily)
                .filter { it.type == "button" && it.id.startsWith("task-") }
                .map { it.id }
                .toSet(),
        )
        assertFalse(catalog.templates.containsKey("demo-quests/daily-fish"))
        assertTrue(
            catalog.templates.getValue("demo-quests/story-empty").elements.any {
                it.id == "empty-list"
            }
        )
        assertEquals(
            listOf("template: demo-quests/completed-fish"),
            daily.elements.single { it.id == "category-completed" }.actions,
        )
    }

    @Test
    fun `completed category paginates five entries and every category destination exists`() {
        val yaml = MenuConfigParser.yaml(source, "quest")
        val tasks = yaml.getConfigurationSection("Tasks")!!
        for (id in tasks.getKeys(false)) tasks.set("$id.Progress", listOf(1, 1))
        val catalog = catalog(yaml.saveToString())
        val first = catalog.templates.getValue("demo-quests/completed-mine")
        assertEquals(
            5,
            visible(first).count {
                it.type == "button" && (it.id.startsWith("task-") || it.id.startsWith("done-"))
            },
        )
        assertEquals(
            listOf("template: demo-quests/completed-forest"),
            first.elements.single { it.id == "next" }.actions,
        )
        val last = catalog.templates.getValue("demo-quests/completed-forest")
        assertEquals(2, visible(last).count { it.type == "button" && it.id.startsWith("task-") })
        assertEquals(
            listOf("template: demo-quests/completed-mine"),
            last.elements.single { it.id == "previous" }.actions,
        )
        for (template in catalog.templates.values) {
            for (action in
                template.elements.flatMap { it.actions }.filter { it.startsWith("template: ") }) {
                assertTrue(catalog.templates.containsKey(action.removePrefix("template: ")), action)
            }
        }
    }

    @Test
    fun `every generated page renders within the supported focus geometry`() {
        for (skin in listOf("amethyst", "parchment")) {
            for (template in
                catalog(source.replace("Skin: amethyst", "Skin: $skin")).templates.values) {
                assertEquals(552, template.width)
                assertEquals(20, template.rows)
                val values = template.values(emptyMap())
                val actions = mutableSetOf<String>()
                val canvas =
                    TemplateRenderer.render(template, values, { it }) {
                        actions += it
                        DialogClicks.custom(Key.key("test", it))
                    }
                canvas.build()
                assertEquals(
                    visible(template, values).filter { it.type == "button" }.map { it.id }.toSet(),
                    actions,
                )
                assertTrue(
                    canvas.hits.all {
                        it.x >= 0 && it.x + it.width <= 552 && it.row + it.rows <= 20
                    }
                )
            }
        }
    }

    @Test
    fun `empty categories and a one task catalog keep usable navigation`() {
        val yaml = MenuConfigParser.yaml(source, "quest")
        val tasks = yaml.getConfigurationSection("Tasks")!!
        tasks.getKeys(false).filter { it != "mine" }.forEach { tasks.set(it, null) }
        val catalog = catalog(yaml.saveToString())
        val empty = catalog.templates.getValue("demo-quests/weekly-empty")
        assertTrue(empty.elements.any { it.id == "empty-list" })
        assertTrue(empty.elements.any { it.id == "category-all" })
        assertFalse(empty.elements.any { it.id == "claim" || it.id == "next" })
        val completed = catalog.templates.getValue("demo-quests/completed-empty")
        assertTrue(completed.elements.any { it.id == "empty-list" })
        assertEquals(
            listOf("template: demo-quests/completed-empty"),
            empty.elements.single { it.id == "category-completed" }.actions,
        )
    }

    @Test
    fun `invalid progress layout categories and icons fail before installing`() {
        for (value in
            listOf(
                source.replace("PageSize: 5", "PageSize: 6"),
                source.replace("Progress: [8, 12]", "Progress: [13, 12]"),
                source.replace("Progress: [8, 12]", "Progress: [8, 0]"),
                source.replace("Category: daily", "Category: missing"),
                source.replace("  daily:", "  completed:"),
                source.replace("Category: daily", "Category: completed"),
                source.replace("Icon: iron_sword", "Icon: missing"),
                source.replace("Claimed: true", "Claimed: 'true'"),
                source.replace("List: [16, 6]", "List: [400, 6]"),
                source.replace("Pagination: [16, 17]", "Pagination: [16, 6]"),
            )) assertThrows(IllegalArgumentException::class.java) { catalog(value) }
    }
}
