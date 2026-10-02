package online.toraka.dialogmenu;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Only display copies are modified. Nothing here gives or removes player inventory items. */
public final class MenuItemSources {
    private final Map<String, MenuItemSource> sources;

    public MenuItemSources(Map<String, MenuItemSource> sources) {
        this.sources = sources;
    }

    public String problem(ItemReference reference) {
        try {
            MenuItemSource source = sources.get(reference.provider());
            String problem = source == null ? null : source.problem(reference.input());
            if (problem != null) {
                return problem;
            }
            if (!sources.containsKey(reference.provider())) {
                return "物品源未安装：" + reference.provider();
            }
            return null;
        } catch (Exception error) {
            return "物品源 "
                    + reference.provider()
                    + " 无法读取 "
                    + reference.input()
                    + "："
                    + error.getClass().getSimpleName();
        } catch (LinkageError error) {
            return "物品源 " + reference.provider() + " API 不兼容：" + error.getClass().getSimpleName();
        }
    }

    private ItemStack build(ItemReference reference, Player player, int amount) {
        MenuItemSource source = sources.get(reference.provider());
        ItemStack stack = source == null ? null : source.build(reference.input(), player);
        if (stack == null) {
            return null;
        }
        if (ItemReference.AIR_MATERIALS.contains(stack.getType())) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }

    public ResolvedMenuItem resolve(ItemDisplay display, Player player) {
        String failure = problem(display.material());
        if (failure == null) {
            try {
                ItemStack stack = build(display.material(), player, display.amount());
                if (stack != null) {
                    return new ResolvedMenuItem(stack, null);
                }
                failure = "物品源返回空物品：" + display.material().input();
            } catch (Exception error) {
                failure =
                        "物品构建失败 "
                                + display.material().input()
                                + "："
                                + error.getClass().getSimpleName();
            } catch (LinkageError error) {
                failure =
                        "物品源 "
                                + display.material().provider()
                                + " API 不兼容："
                                + error.getClass().getSimpleName();
            }
        }
        ItemStack fallback = null;
        ItemReference reference = display.fallback();
        if (reference != null) {
            // Kotlin runCatching { ... }.getOrNull(): any throwable means no fallback item.
            try {
                fallback = build(reference, player, display.amount());
            } catch (Throwable error) {
                fallback = null;
            }
        }
        return new ResolvedMenuItem(fallback, failure);
    }
}
