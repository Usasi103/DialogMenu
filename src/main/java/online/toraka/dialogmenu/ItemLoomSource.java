package online.toraka.dialogmenu;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Uses ItemLoom's published service without generating player-dependent items during validation. */
public final class ItemLoomSource implements MenuItemSource {
    private Object service() throws ReflectiveOperationException {
        var plugin = Bukkit.getPluginManager().getPlugin("ItemLoom");
        if (plugin == null || !plugin.isEnabled())
            throw new IllegalStateException("ItemLoom 未安装或未启用");
        Class<?> api =
                Class.forName(
                        "dev.itemloom.api.ItemLoom", true, plugin.getClass().getClassLoader());
        Object service = Bukkit.getServicesManager().load(api);
        if (service == null) throw new IllegalStateException("ItemLoom 服务尚未就绪");
        return service;
    }

    private Class<?> api(Object service) throws ClassNotFoundException {
        return Class.forName(
                "dev.itemloom.api.ItemLoom", true, service.getClass().getClassLoader());
    }

    @Override
    public String problem(String input) {
        try {
            Object service = service();
            var ids = (Set<?>) api(service).getMethod("ids").invoke(service);
            return ids.contains(input) ? null : "ItemLoom 不存在物品: " + input;
        } catch (ReflectiveOperationException | IllegalStateException | LinkageError failure) {
            return "ItemLoom 物品源不可用: " + failure.getMessage();
        }
    }

    @Override
    public ItemStack build(String input, Player player) {
        try {
            Object service = service();
            return (ItemStack)
                    api(service)
                            .getMethod("create", String.class, OfflinePlayer.class, Map.class)
                            .invoke(service, input, player, Map.of());
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("ItemLoom 生成失败: " + input, failure.getCause());
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("ItemLoom API 不可用", failure);
        }
    }
}
