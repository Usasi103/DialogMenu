package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ItemSources {
    /** Like Kotlin's object property: built when ItemSources is first used. */
    private static final MenuItemSources registry = createRegistry();

    private ItemSources() {}

    public static MenuItemSources registry() {
        return registry;
    }

    private static MenuItemSources createRegistry() {
        return createRegistry(false);
    }

    static MenuItemSources previewRegistry() {
        return createRegistry(true);
    }

    private static MenuItemSources createRegistry(boolean preview) {
        Map<String, MenuItemSource> sources = new LinkedHashMap<>();
        sources.put(
                "minecraft",
                new MenuItemSource() {
                    @Override
                    public String problem(String input) {
                        Material material = Material.matchMaterial(input);
                        if (material != null
                                && material.isItem()
                                && !ItemReference.AIR_MATERIALS.contains(material)) {
                            return null;
                        }
                        return "不是可展示的原版物品：" + input;
                    }

                    @Override
                    public ItemStack build(String input, Player player) {
                        if (problem(input) == null) {
                            return new ItemStack(
                                    Objects.requireNonNull(Material.matchMaterial(input)));
                        }
                        return null;
                    }
                });
        sources.putAll(preview ? ItemBridgeSources.previewSources() : ItemBridgeSources.sources());
        return new MenuItemSources(sources);
    }

    public static List<String> validate(MenuDefinition menu) {
        return validate(menu, registry);
    }

    public static List<String> validate(MenuDefinition menu, MenuItemSources sources) {
        List<String> warnings = new ArrayList<>();
        List<ItemMenuEntry> entries = new ArrayList<>();
        for (MenuPage page : menu.pages().values()) {
            entries.addAll(page.itemEntries());
        }
        for (ItemMenuEntry entry : entries) {
            ItemDisplay display = entry.display();
            if (display == null) {
                continue;
            }
            ItemReference fallback = display.fallback();
            if (fallback != null) {
                Kt.require(
                        sources.problem(fallback) == null,
                        () -> display.path() + ".Fallback: 无效回退物品");
            }
            String problem = sources.problem(display.material());
            if (problem != null) {
                String detail = display.path() + ".Material: " + problem;
                Kt.require(display.fallback() != null, () -> detail);
                warnings.add(detail + "；使用回退显示，对应动作停用");
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(warnings));
    }

    public static void changed() {
        MenuImages.reset();
        ItemBridgeSources.reset();
        // Kotlin runCatching { ... }.onSuccess { ... }.onFailure { ... }: only the validation
        // itself is guarded; logging runs outside the catch.
        List<String> warnings = null;
        Throwable failure = null;
        try {
            List<String> found = new ArrayList<>();
            for (MenuDefinition definition : MenuRuntime.definitions()) {
                found.addAll(validate(definition));
            }
            warnings = found;
        } catch (Throwable error) {
            failure = error;
        }
        if (failure == null) {
            for (String warning : warnings) {
                MenuLog.warning(warning);
            }
        } else {
            MenuLog.warning(String.valueOf(failure.getMessage()));
        }
        MenuDialog.reloaded();
        TemplateDialog.reloaded();
    }

    // The TabooLib @SubscribeEvent handlers (CraftEngineReloadEvent via OptionalEvent,
    // PluginDisableEvent and PluginEnableEvent for plugins in ItemBridgeSources.plugins().values())
    // used to live here and scheduled changed() on the next tick. The plugin's Bukkit listeners now
    // do that and call ItemSources.changed().
}
