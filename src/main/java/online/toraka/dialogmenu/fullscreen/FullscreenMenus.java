package online.toraka.dialogmenu.fullscreen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.server.level.ServerPlayer;

import online.toraka.dialogmenu.MenuDialog;
import online.toraka.dialogmenu.TemplateDialog;
import online.toraka.dialogmenu.MenuReaction;
import online.toraka.dialogmenu.MenuRuntime;
import online.toraka.dialogmenu.ReactionRunner;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.CommandExecutor;
import online.toraka.dialogmenu.MenuResources;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Fullscreen session lifecycle owned by the single DialogMenu plugin. */
public final class FullscreenMenus implements Listener, CommandExecutor, AutoCloseable {
    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private BukkitTask ticker;
    private long tick;
    private long clicks;
    private CursorPack pack;

    public FullscreenMenus(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("dfullscreen")).setExecutor(this);
    }

    public void request(Player player) {
        if (!player.hasPermission("playersettings.use")) return;
        if (!Bukkit.getMinecraftVersion().equals("26.3")) {
            player.sendMessage("全屏菜单当前支持 Paper 26.3。");
            return;
        }
        MenuResources.cancelOpen(player);
        if (pack == null) {
            try {
                pack = new CursorPack(plugin, p -> open(p, true), this::closeLocal);
                plugin.getServer().getPluginManager().registerEvents(pack, plugin);
            } catch (Exception error) {
                player.sendMessage("全屏资源包服务不可用，请检查 fullscreen.yml 和控制台。");
                plugin.getLogger()
                        .log(java.util.logging.Level.SEVERE, "Fullscreen pack unavailable", error);
                return;
            }
        }
        pack.open(player);
    }

    /** Cancel pending pack callbacks and finish the exit handshake before showing a Dialog. */
    public boolean beforeDialog(Player player, Runnable next) {
        if (pack != null) pack.cancel(player);
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return false;
        if (session.state.phase() == PointerState.Phase.ENDING) session.afterClose = next;
        else session.close(next);
        return true;
    }

    public void reloaded() {
        close();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dialogmenu.fullscreen.test")) return true;
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            int entities = sessions.values().stream().mapToInt(s -> s.scene.entityCount()).sum();
            int pending = sessions.values().stream().mapToInt(s -> s.state.pendingCount()).sum();
            sender.sendMessage(
                    "Fullscreen: sessions="
                            + sessions.size()
                            + ", packetEntities="
                            + entities
                            + ", worldEntities=0, pending="
                            + pending
                            + ", clicks="
                            + clicks
                            + ", ticker="
                            + (ticker != null));
            for (Session session : sessions.values()) {
                sender.sendMessage(
                        session.player.getName()
                                + ": mode="
                                + (session.local ? "local" : "legacy")
                                + ", "
                                + session.state.phase()
                                + ", samples="
                                + session.state.samples()
                                + ", metadataUpdates="
                                + session.scene.updates());
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("玩家使用 /dfullscreen 打开；控制台可用 /dfullscreen status。");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("close")) {
            if (pack != null) pack.cancel(player);
            Session session = sessions.get(player.getUniqueId());
            if (session != null) session.close(null);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("legacy")) {
            open(player, false);
            return true;
        }
        if (args.length > 0) return false;
        request(player);
        return true;
    }

    private void open(Player player, boolean local) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage("全屏界面已打开，Shift 或 F 退出。");
            return;
        }
        if (player.isDead()
                || player.isInsideVehicle()
                || player.isSleeping()
                || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage("请在存活、未乘坐载具且非旁观状态下测试。");
            return;
        }
        player.closeInventory();
        MenuDialog.forget(player.getUniqueId());
        TemplateDialog.forget(player.getUniqueId());
        player.closeDialog();
        Session session = new Session(player, local);
        sessions.put(player.getUniqueId(), session);
        try {
            session.channel.install();
            session.channel.maskWorld(local);
            session.scene.show();
            session.channel.camera(session.scene.camera);
            session.channel.mode(3);
            if (local) session.channel.freezeLocalMovement();
            session.channel.reset(local ? 0 : session.anchor.getYaw());
            if (ticker == null)
                ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        } catch (RuntimeException error) {
            finish(session, true, true);
            player.sendMessage("无法打开全屏原型，请查看控制台。");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Fullscreen open failed", error);
        }
    }

    private void tick() {
        tick++;
        for (Session session : new ArrayList<>(sessions.values())) {
            try {
                if (!session.player.isOnline()) {
                    finish(session, false, false);
                } else if (session.channel.externalTeleport()) {
                    finish(session, true, false);
                } else if (session.state.phase() == PointerState.Phase.CLOSED) {
                    finish(session, true, false);
                } else if (session.state.phase() == PointerState.Phase.ENDING) {
                    if (tick - session.endingTick >= 40) finish(session, true, true);
                } else if (session.state.timedOut(System.nanoTime())
                        || tick - session.openTick >= 6000) {
                    session.player.sendMessage("全屏测试结束：确认超时或达到 5 分钟测试时限。");
                    finish(session, true, true);
                } else if (session.state.exitRequested()) {
                    session.close(null);
                } else {
                    if (session.state.takeReset()) session.channel.reset(session.anchor.getYaw());
                    if (session.state.phase() == PointerState.Phase.ACTIVE) {
                        if (!session.ready) {
                            session.ready = true;
                            session.scene.status("已就绪 · 尝试四角与中央按钮");
                        }
                        session.scene.pointer(session.state.point());
                        for (var click : session.state.drainClicks()) {
                            if (session.state.phase() != PointerState.Phase.ACTIVE) break;
                            session.activate(click);
                        }
                        session.scene.flush();
                        if (session.state.phase() == PointerState.Phase.ACTIVE
                                && (!session.local || tick % 20 == 0)) session.channel.sample();
                    }
                }
            } catch (RuntimeException error) {
                plugin.getLogger()
                        .log(java.util.logging.Level.SEVERE, "Fullscreen session failed", error);
                finish(session, true, true);
            }
        }
    }

    private void finish(Session session, boolean restore, boolean synchronize) {
        if (!sessions.remove(session.player.getUniqueId(), session)) return;
        session.state.discard();
        try {
            if (restore && session.player.isOnline()) {
                session.channel.maskWorld(false);
                session.channel.mode(session.handle.gameMode.getGameModeForPlayer().getId());
                if (session.local) session.channel.restoreAbilities();
                session.channel.camera(session.handle);
                // A real vanilla teleport fences any old replies when an exit handshake timed out.
                if (synchronize && !session.player.isDead())
                    session.player.teleport(session.player.getLocation());
                session.scene.remove();
            }
        } finally {
            session.channel.detach();
            if (sessions.isEmpty() && ticker != null) {
                ticker.cancel();
                ticker = null;
            }
        }
        if (session.afterClose != null && session.player.isOnline() && plugin.isEnabled())
            session.afterClose.run();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        abort(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        abort(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameMode(PlayerGameModeChangeEvent event) {
        abort(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) abort(player, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) {
        abort(event.getEntity(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) abort(player, true);
    }

    private void abort(Player player, boolean restore) {
        if (pack != null) pack.cancel(player);
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.afterClose = null;
            finish(session, restore, false);
        }
    }

    private void closeLocal(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.local) session.close(null);
    }

    @Override
    public void close() {
        for (Session session : new ArrayList<>(sessions.values())) {
            session.afterClose = null;
            finish(session, true, true);
        }
        if (pack != null) {
            org.bukkit.event.HandlerList.unregisterAll(pack);
            pack.close();
            pack = null;
        }
    }

    private final class Session implements ReactionRunner.Host {
        final Player player;
        final ServerPlayer handle;
        final Location anchor;
        final PointerState state;
        final boolean local;
        final PacketScene scene;
        final PointerChannel channel;
        final long openTick = tick;
        long endingTick;
        boolean ready;
        Runnable afterClose;

        Session(Player player, boolean local) {
            this.player = player;
            this.local = local;
            state = new PointerState(2, local);
            handle = ((CraftPlayer) player).getHandle();
            anchor = player.getLocation();
            scene = new PacketScene(player, 180, local);
            channel = new PointerChannel(handle, state, scene.captureId);
        }

        void activate(PointerState.Click click) {
            var button = DemoLayout.hit(click.point());
            if (button == null) return;
            clicks++;
            scene.status(button.label() + " · " + click.button() + " · #" + clicks);
            String action =
                    switch (button.id()) {
                        case "close" -> "close";
                        case "settings" -> "open";
                        default -> "tell";
                    };
            String value =
                    switch (action) {
                        case "close" -> "";
                        case "open" -> "default";
                        default -> "全屏按钮：" + button.label() + "（" + click.button() + "）";
                    };
            ReactionRunner.run(
                    new MenuReaction(
                            List.of(
                                    new MenuReaction.Step(
                                            action, value, MenuReaction.Options.NONE))),
                    this);
        }

        void close(Runnable next) {
            if (state.phase() == PointerState.Phase.ENDING
                    || state.phase() == PointerState.Phase.CLOSED) return;
            afterClose = next;
            endingTick = tick;
            state.ending();
            channel.mode(handle.gameMode.getGameModeForPlayer().getId());
            channel.exitPosition();
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public String text(String source) {
            return source.replace("{player}", player.getName());
        }

        @Override
        public String commandValue(String name) {
            return name.equals("player") ? player.getName() : null;
        }

        @Override
        public Map<String, String> names() {
            return Map.of();
        }

        @Override
        public Component rich(String tag) {
            return MiniMessage.miniMessage().deserialize(text(tag));
        }

        @Override
        public void open(String target) {
            close(() -> MenuRuntime.openDialog(player));
        }

        @Override
        public void close() {
            close(null);
        }

        @Override
        public void refresh() {
            scene.flush();
        }

        @Override
        public void settle() {
            scene.flush();
        }

        @Override
        public void failed() {
            close();
        }

        @Override
        public void set(String variable, String value) {
            throw new UnsupportedOperationException("Demo has no variables");
        }

        @Override
        public void page(String target) {
            throw new UnsupportedOperationException("Demo has one page");
        }

        @Override
        public void search() {
            throw new UnsupportedOperationException("Demo has no search");
        }

        @Override
        public void template(String target) {
            close(() -> MenuRuntime.open(player, target));
        }
    }
}
