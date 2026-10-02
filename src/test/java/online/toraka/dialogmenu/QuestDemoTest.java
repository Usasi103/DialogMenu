package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestDemoTest {

    private final String source = MenuRepository.resource("catalog/menus/demo-quests.yml");
    private final String config = "Version: 3\nDefaultMenu: demo-quests\n";

    private MenuCatalog catalog() {
        return catalog(source);
    }

    private MenuCatalog catalog(String value) {
        return MenuCatalogParser.parse(config, Map.of("demo-quests", value));
    }

    private static List<TemplateElement> visible(DialogTemplate template) {
        return visible(template, template.values(Collections.emptyMap()));
    }

    private static List<TemplateElement> visible(
            DialogTemplate template, Map<String, String> values) {
        return template.elements().stream()
                .filter(it -> TemplateRenderer.visible(it, values))
                .toList();
    }

    private static TemplateElement single(DialogTemplate template, String id) {
        return Kt.single(template.elements().stream().filter(it -> it.id().equals(id)).toList());
    }

    @Test
    @DisplayName("five task entries paginate and category routes remain local")
    void fiveTaskEntriesPaginateAndCategoryRoutesRemainLocal() {
        MenuCatalog catalog = catalog();
        assertEquals("all-mine", catalog.menus().get("demo-quests").defaultPage());
        DialogTemplate first = catalog.templates().get("demo-quests/all-mine");
        List<TemplateElement> entries =
                visible(first).stream()
                        .filter(it -> it.id().startsWith("task-") || it.id().startsWith("done-"))
                        .filter(it -> it.type().equals("button"))
                        .toList();
        assertEquals(
                List.of("task-mine", "task-fish", "task-boss", "done-arrival", "task-ore"),
                entries.stream().map(TemplateElement::id).toList());
        assertEquals(
                List.of(6, 8, 10, 12, 14), entries.stream().map(TemplateElement::row).toList());
        assertFalse(first.elements().stream().anyMatch(it -> it.id().equals("previous")));
        assertEquals(List.of("template: demo-quests/all-forest"), single(first, "next").actions());
        DialogTemplate last = catalog.templates().get("demo-quests/all-forest");
        assertEquals(
                2,
                visible(last).stream()
                        .filter(it -> it.type().equals("button") && it.id().startsWith("task-"))
                        .count());
        assertFalse(last.elements().stream().anyMatch(it -> it.id().equals("next")));
        assertEquals(List.of("template: demo-quests/all-mine"), single(last, "previous").actions());
        DialogTemplate weekly = catalog.templates().get("demo-quests/weekly-boss");
        assertEquals(
                Set.of("task-boss", "task-expedition"),
                visible(weekly).stream()
                        .filter(it -> it.type().equals("button") && it.id().startsWith("task-"))
                        .map(TemplateElement::id)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    @Test
    @DisplayName("claim and tracking state persist across pages but reopening starts fresh")
    void claimAndTrackingStatePersistAcrossPagesButReopeningStartsFresh() {
        MenuCatalog catalog = catalog();
        DialogTemplate fish = catalog.templates().get("demo-quests/all-fish");
        Map<String, String> initial = fish.values(Collections.emptyMap());
        assertTrue(visible(fish, initial).stream().anyMatch(it -> it.id().equals("claim")));
        Map<String, String> claimed = new LinkedHashMap<>(initial);
        claimed.put("claim-fish", "claimed");
        claimed.put("tracked", "mine");
        assertFalse(visible(fish, claimed).stream().anyMatch(it -> it.id().equals("claim")));
        assertTrue(visible(fish, claimed).stream().anyMatch(it -> it.id().equals("claimed")));
        DialogTemplate completedFish = catalog.templates().get("demo-quests/completed-fish");
        assertEquals(claimed, completedFish.values(claimed));
        assertEquals("ready", completedFish.values(Collections.emptyMap()).get("claim-fish"));
        assertFalse(
                visible(completedFish, claimed).stream().anyMatch(it -> it.id().equals("claim")));
        DialogTemplate mine = catalog.templates().get("demo-quests/all-mine");
        assertFalse(visible(mine).stream().anyMatch(it -> it.id().equals("untrack")));
        assertTrue(
                visible(mine, mine.values(claimed)).stream()
                        .anyMatch(it -> it.id().equals("untrack")));
        assertFalse(mine.elements().stream().anyMatch(it -> it.id().equals("claim")));
        assertTrue(
                catalog.templates().values().stream()
                        .flatMap(it -> it.elements().stream())
                        .flatMap(it -> it.actions().stream())
                        .noneMatch(it -> it.startsWith("console:") || it.startsWith("command:")));
    }

    @Test
    @DisplayName(
            "completed category includes ready and claimed tasks while named categories exclude"
                    + " both")
    void completedCategoryIncludesReadyAndClaimedTasksWhileNamedCategoriesExcludeBoth() {
        MenuCatalog catalog = catalog();
        DialogTemplate completed = catalog.templates().get("demo-quests/completed-fish");
        assertEquals(
                List.of(
                        "category-all",
                        "category-daily",
                        "category-weekly",
                        "category-story",
                        "category-completed"),
                completed.elements().stream()
                        .filter(it -> it.id().startsWith("category-"))
                        .map(TemplateElement::id)
                        .toList());
        assertEquals(
                Set.of("task-fish", "done-arrival"),
                visible(completed).stream()
                        .filter(
                                it ->
                                        it.type().equals("button")
                                                && (it.id().startsWith("task-")
                                                        || it.id().startsWith("done-")))
                        .map(TemplateElement::id)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        assertTrue(visible(completed).stream().anyMatch(it -> it.id().equals("claim")));
        DialogTemplate daily = catalog.templates().get("demo-quests/daily-mine");
        assertEquals(
                Set.of("task-mine", "task-ore", "task-forest"),
                visible(daily).stream()
                        .filter(it -> it.type().equals("button") && it.id().startsWith("task-"))
                        .map(TemplateElement::id)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        assertFalse(catalog.templates().containsKey("demo-quests/daily-fish"));
        assertTrue(
                catalog.templates().get("demo-quests/story-empty").elements().stream()
                        .anyMatch(it -> it.id().equals("empty-list")));
        assertEquals(
                List.of("template: demo-quests/completed-fish"),
                single(daily, "category-completed").actions());
    }

    @Test
    @DisplayName("completed category paginates five entries and every category destination exists")
    void completedCategoryPaginatesFiveEntriesAndEveryCategoryDestinationExists() {
        YamlConfiguration yaml = MenuConfigParser.yaml(source, "quest");
        ConfigurationSection tasks = Objects.requireNonNull(yaml.getConfigurationSection("Tasks"));
        for (String id : tasks.getKeys(false)) {
            tasks.set(id + ".Progress", List.of(1, 1));
        }
        MenuCatalog catalog = catalog(yaml.saveToString());
        DialogTemplate first = catalog.templates().get("demo-quests/completed-mine");
        assertEquals(
                5,
                visible(first).stream()
                        .filter(
                                it ->
                                        it.type().equals("button")
                                                && (it.id().startsWith("task-")
                                                        || it.id().startsWith("done-")))
                        .count());
        assertEquals(
                List.of("template: demo-quests/completed-forest"), single(first, "next").actions());
        DialogTemplate last = catalog.templates().get("demo-quests/completed-forest");
        assertEquals(
                2,
                visible(last).stream()
                        .filter(it -> it.type().equals("button") && it.id().startsWith("task-"))
                        .count());
        assertEquals(
                List.of("template: demo-quests/completed-mine"),
                single(last, "previous").actions());
        for (DialogTemplate template : catalog.templates().values()) {
            List<String> actions =
                    template.elements().stream()
                            .flatMap(it -> it.actions().stream())
                            .filter(it -> it.startsWith("template: "))
                            .toList();
            for (String action : actions) {
                assertTrue(
                        catalog.templates().containsKey(Kt.removePrefix(action, "template: ")),
                        action);
            }
        }
    }

    @Test
    @DisplayName("every generated page renders within the supported focus geometry")
    void everyGeneratedPageRendersWithinTheSupportedFocusGeometry() {
        for (String skin : List.of("amethyst", "parchment")) {
            for (DialogTemplate template :
                    catalog(source.replace("Skin: amethyst", "Skin: " + skin))
                            .templates()
                            .values()) {
                assertEquals(552, template.width());
                assertEquals(20, template.rows());
                Map<String, String> values = template.values(Collections.emptyMap());
                Set<String> actions = new LinkedHashSet<>();
                DialogCanvas canvas =
                        TemplateRenderer.render(
                                template,
                                values,
                                it -> it,
                                it -> {
                                    actions.add(it);
                                    return DialogClicks.custom(Key.key("test", it));
                                });
                canvas.build();
                assertEquals(
                        visible(template, values).stream()
                                .filter(it -> it.type().equals("button"))
                                .map(TemplateElement::id)
                                .collect(Collectors.toCollection(LinkedHashSet::new)),
                        actions);
                assertTrue(
                        canvas.hits().stream()
                                .allMatch(
                                        it ->
                                                it.x() >= 0
                                                        && it.x() + it.width() <= 552
                                                        && it.row() + it.rows() <= 20));
            }
        }
    }

    @Test
    @DisplayName("empty categories and a one task catalog keep usable navigation")
    void emptyCategoriesAndAOneTaskCatalogKeepUsableNavigation() {
        YamlConfiguration yaml = MenuConfigParser.yaml(source, "quest");
        ConfigurationSection tasks = Objects.requireNonNull(yaml.getConfigurationSection("Tasks"));
        List<String> removed = new ArrayList<>();
        for (String id : tasks.getKeys(false)) {
            if (!id.equals("mine")) {
                removed.add(id);
            }
        }
        for (String id : removed) {
            tasks.set(id, null);
        }
        MenuCatalog catalog = catalog(yaml.saveToString());
        DialogTemplate empty = catalog.templates().get("demo-quests/weekly-empty");
        assertTrue(empty.elements().stream().anyMatch(it -> it.id().equals("empty-list")));
        assertTrue(empty.elements().stream().anyMatch(it -> it.id().equals("category-all")));
        assertFalse(
                empty.elements().stream()
                        .anyMatch(it -> it.id().equals("claim") || it.id().equals("next")));
        DialogTemplate completed = catalog.templates().get("demo-quests/completed-empty");
        assertTrue(completed.elements().stream().anyMatch(it -> it.id().equals("empty-list")));
        assertEquals(
                List.of("template: demo-quests/completed-empty"),
                single(empty, "category-completed").actions());
    }

    @Test
    @DisplayName("invalid progress layout categories and icons fail before installing")
    void invalidProgressLayoutCategoriesAndIconsFailBeforeInstalling() {
        for (String value :
                List.of(
                        source.replace("PageSize: 5", "PageSize: 6"),
                        source.replace("Progress: [8, 12]", "Progress: [13, 12]"),
                        source.replace("Progress: [8, 12]", "Progress: [8, 0]"),
                        source.replace("Category: daily", "Category: missing"),
                        source.replace("  daily:", "  completed:"),
                        source.replace("Category: daily", "Category: completed"),
                        source.replace("Icon: iron_sword", "Icon: missing"),
                        source.replace("Claimed: true", "Claimed: 'true'"),
                        source.replace("List: [16, 6]", "List: [400, 6]"),
                        source.replace("Pagination: [16, 17]", "Pagination: [16, 6]"))) {
            assertThrows(IllegalArgumentException.class, () -> catalog(value));
        }
    }
}
