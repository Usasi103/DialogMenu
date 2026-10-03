package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemSourceTest {
    @TempDir Path directory;

    private final String page =
            """
            Title: 物品菜单
            Layout: [彩虹鱼]
            Icons:
              彩虹鱼:
                Display:
                  Material: "source:CE:customfishing:rainbow_fish"
                  Name: {zh_cn: 彩虹鱼, en_us: Rainbow fish}
                  Lore: [查看鱼类模型]
                  Amount: 2
                Permission: example.fish
                Actions: ["close", "command: spawn"]""";

    private MenuDefinition parse() {
        return parse(page);
    }

    private MenuDefinition parse(String source) {
        return SimpleMenuParser.parse(
                "MenuType: dialog\nVersion: 2\nPages: [items]\n", id -> source);
    }

    @Test
    @DisplayName("source aliases preserve the namespaced CE id")
    void sourceAliasesPreserveTheNamespacedCeId() {
        for (String alias : List.of("CE", "CRAFTENGINE", "ce", "CraftEngine")) {
            assertEquals(
                    new ItemReference("craftengine", "fish:rainbow/rare"),
                    ItemReference.parse("source:" + alias + ":fish:rainbow/rare", "test"));
        }
        for (String value :
                List.of(
                        "DIAMOND",
                        "minecraft:diamond",
                        "source:VANILLA:DIAMOND",
                        "source:MINECRAFT:minecraft:diamond")) {
            assertEquals(
                    new ItemReference("minecraft", "diamond"), ItemReference.parse(value, "test"));
        }
        for (String bad :
                List.of(
                        "source:CE:fish",
                        "source:CE:Fish:rainbow",
                        "source:CE:fish:%id%",
                        "source:XX:fish:id",
                        "AIR",
                        "not_an_item",
                        "DIAMOND ",
                        "custom:diamond")) {
            assertThrows(Exception.class, () -> ItemReference.parse(bad, "test"), bad);
        }
    }

    @Test
    @DisplayName("item layout retains bilingual captions and secure actions")
    void itemLayoutRetainsBilingualCaptionsAndSecureActions() {
        MenuDefinition menu = parse();
        MenuPage page = Kt.getValue(menu.pages(), "items");
        assertTrue(page.itemLayout());
        assertTrue(page.widgets().isEmpty());
        ItemMenuEntry entry = Kt.single(page.itemEntries());
        assertEquals("Rainbow fish", menu.text(MenuLanguage.ENGLISH, entry.name()));
        assertEquals(2, entry.display().amount());
        MenuAction action = Kt.getValue(menu.actions(), entry.action());
        assertEquals("example.fish", action.permission());
        assertEquals(List.of("close", "command: spawn"), action.reaction().lines());
    }

    @Test
    @DisplayName("invalid layout fields and unsafe configurations are rejected")
    void invalidLayoutFieldsAndUnsafeConfigurationsAreRejected() {
        for (String bad :
                List.of(
                        "Renderer: canvas\n" + page,
                        "Renderer: typo\n" + page,
                        page.replace("Amount: 2", "Amount: 0"),
                        page.replace("Amount: 2", "Amount: '2'"),
                        page.replace("Amount: 2", "Amount: 100"),
                        page.replace("Amount: 2", "Fallback: source:CE:fish:fallback"),
                        page.replace("Display:", "Type: toggle\n    Display:"),
                        page.replace("Display:", "Name: duplicate\n    Display:"),
                        page.replace("Amount: 2", "Model: typo"),
                        page.replace("Display:", "Position: [10, 20]\n    Display:"),
                        page.replace("command: spawn", "console: say {input}"),
                        page.replace("Display:", "Type: item\n    Display:"))) {
            assertThrows(Exception.class, () -> parse(bad), bad);
        }
        boolean anyItemLayout = false;
        for (MenuPage simple :
                SimpleMenuParser.parse(
                                MenuRepository.resource("simple/config.yml"),
                                id -> MenuRepository.resource("simple/menus/" + id + ".yml"))
                        .pages()
                        .values()) {
            if (simple.itemLayout()) {
                anyItemLayout = true;
                break;
            }
        }
        assertFalse(anyItemLayout);
    }

    @Test
    @DisplayName("fresh source lookup returns isolated copies and gracefully falls back")
    void freshSourceLookupReturnsIsolatedCopiesAndGracefullyFallsBack() {
        AtomicBoolean ready = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger(0);
        ItemStack original = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(original.getType()).thenReturn(Material.DIAMOND);
        when(original.clone()).thenReturn(clone);
        MenuItemSource source =
                new MenuItemSource() {
                    @Override
                    public String problem(String input) {
                        return ready.get() ? null : "missing";
                    }

                    @Override
                    public ItemStack build(String input, Player player) {
                        calls.incrementAndGet();
                        return original;
                    }
                };
        Map<String, MenuItemSource> sources = new LinkedHashMap<>();
        sources.put("craftengine", source);
        sources.put(
                "minecraft",
                new MenuItemSource() {
                    @Override
                    public String problem(String input) {
                        return null;
                    }

                    @Override
                    public ItemStack build(String input, Player player) {
                        return original;
                    }
                });
        MenuItemSources registry = new MenuItemSources(sources);
        ItemDisplay display =
                Kt.single(Kt.getValue(parse().pages(), "items").itemEntries()).display();
        assertSame(clone, registry.resolve(display, null).item());
        verify(clone).setAmount(2);
        verify(original, never()).setAmount(anyInt());
        registry.resolve(display, null);
        assertEquals(2, calls.get());
        ready.set(false);
        assertNull(registry.resolve(display, null).item());
        ResolvedMenuItem fallback =
                registry.resolve(
                        display.withFallback(new ItemReference("minecraft", "barrier")), null);
        assertSame(clone, fallback.item());
        assertFalse(fallback.available());
        ready.set(true);
        assertTrue(registry.resolve(display, null).available());
    }

    @Test
    @DisplayName("unavailable source rejects reload without installing snapshot and fallback warns")
    void unavailableSourceRejectsReloadWithoutInstallingSnapshotAndFallbackWarns() {
        MenuRepository repository = new MenuRepository(directory.toFile());
        repository.initialize();
        MenuDefinition original = repository.current();
        MenuItemSources unavailable = new MenuItemSources(Collections.emptyMap());
        assertThrows(
                Exception.class,
                () -> {
                    MenuDefinition next = parse();
                    ItemSources.validate(next, unavailable);
                    repository.install(next);
                });
        assertSame(original, repository.current());
        Map<String, MenuItemSource> sources = new LinkedHashMap<>();
        sources.put(
                "minecraft",
                new MenuItemSource() {
                    @Override
                    public String problem(String input) {
                        return null;
                    }

                    @Override
                    public ItemStack build(String input, Player player) {
                        return null;
                    }
                });
        MenuItemSources registry = new MenuItemSources(sources);
        List<String> warnings =
                ItemSources.validate(
                        parse(page.replace("Amount: 2", "Fallback: BARRIER")), registry);
        assertEquals(1, warnings.size());
        assertTrue(Kt.single(warnings).contains("menus/items.yml.Icons.彩虹鱼.Display.Material"));
        assertTrue(Kt.single(warnings).contains("动作停用"));
    }
}
