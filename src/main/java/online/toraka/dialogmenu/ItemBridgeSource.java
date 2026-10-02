package online.toraka.dialogmenu;

import cn.gtemc.itembridge.api.context.BuildContext;
import cn.gtemc.itembridge.core.BukkitItemBridge;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Validation queries the registry only; player-dependent generation happens when displaying. */
public final class ItemBridgeSource implements MenuItemSource {
    private final String provider;
    private final String plugin;
    private final Supplier<BukkitItemBridge> bridge;
    private final BooleanSupplier enabled;

    public ItemBridgeSource(
            String provider,
            String plugin,
            Supplier<BukkitItemBridge> bridge,
            BooleanSupplier enabled) {
        this.provider = provider;
        this.plugin = plugin;
        this.bridge = bridge;
        this.enabled = enabled;
    }

    @Override
    public String problem(String input) {
        if (!enabled.getAsBoolean()) {
            return plugin + " 未安装或未启用";
        }
        BukkitItemBridge api = bridge.get();
        if (!api.hasProvider(provider)) {
            return "ItemBridge 未能接入 " + plugin + "，请检查该插件版本和服务端日志";
        }
        if (api.has(provider, input)) {
            return null;
        }
        return plugin + " 尚未加载或不存在物品：" + input;
    }

    @Override
    public ItemStack build(String input, Player player) {
        if (!enabled.getAsBoolean()) {
            return null;
        }
        return bridge.get().buildOrNull(provider, input, player, BuildContext.empty());
    }
}
