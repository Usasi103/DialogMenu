import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import java.lang.reflect.*;
import java.util.*;

public class Probe extends JavaPlugin {
    private int dialogs;
    private int commands;
    private final UUID uuid = UUID.randomUUID();
    private Player player;
    private Object target;
    private Method clicked;
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::run, 20L);
    }
    private Object proxy(Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (obj, method, args) -> {
            String name = method.getName();
            if (name.equals("getUniqueId")) return uuid;
            if (name.equals("getName")) return "Validation";
            if (name.equals("hasPermission") || name.equals("isOnline")) return true;
            if (name.equals("showDialog")) {
                dialogs++;
                var holder = io.papermc.paper.dialog.PaperDialog.bukkitToMinecraftHolder((io.papermc.paper.dialog.Dialog) args[0]);
                var encoded = net.minecraft.server.dialog.Dialog.DIRECT_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, holder.value()).getOrThrow();
                net.minecraft.server.dialog.Dialog.DIRECT_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
                java.nio.file.Files.writeString(java.nio.file.Path.of("dialog-" + dialogs + ".json"), encoded.toString());
                return null;
            }
            if (name.equals("performCommand")) { commands++; return true; }
            if (name.equals("getPersistentDataContainer")) return proxy(PersistentDataContainer.class);
            if (name.equals("getWorld")) return proxy(World.class);
            if (name.equals("getPlayer")) return player;
            if (name.equals("hashCode")) return 1;
            if (name.equals("equals")) return obj == args[0];
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        });
    }
    private void run() {
        try {
            target = Bukkit.getPluginManager().getPlugin("PlayerSettings");
            if (target == null || !((JavaPlugin)target).isEnabled()) throw new AssertionError("Plugin not enabled");
            ClassLoader cl = target.getClass().getClassLoader();
            Class<?> tab = cl.loadClass("online.toraka.playersettings.PlayerSettings$Tab");
            Class<?> view = cl.loadClass("online.toraka.playersettings.PlayerSettings$View");
            Constructor<?> ctor = view.getConstructor(tab, boolean.class);
            Method show = target.getClass().getDeclaredMethod("show", Player.class, view);
            show.setAccessible(true);
            player = (Player)proxy(Player.class);
            for (Object value : tab.getEnumConstants()) show.invoke(target, player, ctor.newInstance(value, false));
            Object particles = Arrays.stream(tab.getEnumConstants()).filter(v -> v.toString().equals("PARTICLES")).findFirst().orElseThrow();
            show.invoke(target, player, ctor.newInstance(particles, true));
            if (dialogs != 7) throw new AssertionError("Dialog count " + dialogs);
            getLogger().info("PROBE: all 6 pages and expanded dropdown built with Paper registry API");
            clicked = target.getClass().getMethod("clicked", PlayerCustomClickEvent.class);
            Field field = target.getClass().getDeclaredField("sessions");
            field.setAccessible(true);
            Object session = ((Map<?,?>)field.get(target)).get(uuid);
            Method getToken = session.getClass().getDeclaredMethod("getToken");
            getToken.setAccessible(true);
            String token = (String)getToken.invoke(session);
            clicked.invoke(target, new Click("invalid/particles"));
            clicked.invoke(target, new Click(token + "/arbitrary_command"));
            clicked.invoke(target, new Click(token + "/particles"));
            clicked.invoke(target, new Click(token + "/particles"));
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (commands != 1 || dialogs != 8) getLogger().severe("PROBE FAIL commands="+commands+" dialogs="+dialogs);
                else getLogger().info("PROBE PASS: scoped actions, one-time tokens, command execution and next-tick redraw");
                Bukkit.shutdown();
            }, 10L);
        } catch (Throwable e) {
            getLogger().severe("PROBE FAIL " + e);
            e.printStackTrace();
            Bukkit.shutdown();
        }
    }
    private class Click extends PlayerCustomClickEvent {
        Click(String route) { super(Key.key("toraka_settings", route), (PlayerGameConnection)proxy(PlayerGameConnection.class)); }
        public BinaryTagHolder getTag() { return null; }
        public DialogResponseView getDialogResponseView() { return null; }
    }
}
