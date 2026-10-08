package online.toraka.dialogmenu;

import dev.keystone.task.Tasks;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/**
 * The Bukkit events the TabooLib objects subscribed to (default priority, cancelled events
 * included, as {@code @SubscribeEvent} did), plus the join hook that gives players the
 * resource-pack observer TabooLib's packet listener used to provide.
 */
final class DialogMenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        ResourcePackPackets.joined(event.getPlayer());
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        MenuResources.status(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        AnimationDialog.forget(event.getPlayer().getUniqueId());
        MenuResources.quit(event);
        MenuDialog.quit(event);
        TemplateDialog.quit(event);
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        AnimationDialog.clicked(event);
        MenuDialog.clicked(event);
        TemplateDialog.clicked(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (event.getPlayer() instanceof org.bukkit.entity.Player player)
            AnimationDialog.forget(player.getUniqueId());
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        AnimationDialog.forget(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (ItemBridgeSources.plugins().containsValue(event.getPlugin().getName())) {
            Tasks.run(() -> ItemSources.changed());
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (ItemBridgeSources.plugins().containsValue(event.getPlugin().getName())) {
            Tasks.run(() -> ItemSources.changed());
        }
    }
}
