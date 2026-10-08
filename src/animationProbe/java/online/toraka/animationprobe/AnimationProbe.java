package online.toraka.animationprobe;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;

/** Isolated protocol probe; this source set is never shipped in the production JAR. */
public final class AnimationProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private ServerPlayer handle;
    private EmbeddedChannel channel;
    private final ArrayDeque<Object> outbound = new ArrayDeque<>();
    private ClientboundShowDialogPacket last;
    private int dialogs, clears, assertions, baseline, maxBytes;
    private String oldToken, newToken, originalTiming;
    private boolean started, failed;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @org.bukkit.event.EventHandler
    public void join(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!event.getPlayer().getName().equals("AnimationVisual")) return;
        event.getPlayer().setInvulnerable(true);
        later(80, () -> event.getPlayer().performCommand("dmenu open demo-animation"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            Player player = Bukkit.getPlayerExact("AnimationVisual");
            if (player != null) {
                if (args[0].equals("external")) foreign(player);
                else if (args[0].equals("close")) player.closeDialog();
                else player.performCommand("dmenu open demo-animation" + (args[0].equals("open") ? "" : " " + args[0]));
            }
            return true;
        }
        if (started) return true;
        started = true;
        try {
            spawn();
            Bukkit.getScheduler().runTaskTimer(this, this::pump, 1, 1);
            later(5, this::open);
            later(
                    8,
                    () -> {
                        check(dialogs > 0, "real dialog packets emitted");
                        check(last.dialog().value().common().canCloseWithEscape(), "ESC enabled");
                        check(
                                last.dialog().value().onCancel().orElseThrow() instanceof CustomAll,
                                "native ESC has close callback");
                        oldToken = token();
                        int before = dialogs;
                        click(oldToken, "icon/5");
                        check(dialogs == before, "unrevealed icon rejects forged click");
                        click(oldToken, "icon/99");
                        check(dialogs == before, "invalid icon rejected");
                    });
            later(
                    45,
                    () -> {
                        check(dialogs == 31, "exactly initial frame plus thirty ticks");
                        baseline = dialogs;
                        check(sessionCount() == 1, "one player-local session");
                    });
            later(
                    52,
                    () -> {
                        check(dialogs == baseline, "no redraw after settling");
                        click(oldToken, "icon/2");
                        check(
                                last.dialog().value().common().body().toString().contains("已点击"),
                                "click produces visible feedback");
                        click(oldToken, "replay");
                        newToken = token();
                        check(!newToken.equals(oldToken), "replay rotates session token");
                        int before = dialogs;
                        click(oldToken, "replay");
                        click(oldToken, "close");
                        check(
                                dialogs == before && sessionCount() == 1,
                                "old replay and close cannot affect new playback");
                    });
            later(
                    56,
                    () -> {
                        int before = clears;
                        click(newToken, "close");
                        check(clears == before + 1, "close emits clear packet");
                        check(sessionCount() == 0, "close discards session");
                        baseline = dialogs;
                    });
            later(
                    64,
                    () -> {
                        check(dialogs == baseline, "ESC callback prevents reopening");
                        open();
                    });
            later(
                    68,
                    () -> {
                        foreign(player());
                        pump();
                        baseline = dialogs;
                    });
            later(
                    74,
                    () -> {
                        check(
                                dialogs == baseline && sessionCount() == 0,
                                "foreign dialog stops playback");
                        check(
                                last.dialog().value().common().title().getString().equals("外来菜单"),
                                "foreign screen preserved");
                        open();
                    });
            later(
                    78,
                    () -> {
                        player().closeDialog();
                        baseline = dialogs;
                    });
            later(
                    84,
                    () -> {
                        check(
                                dialogs == baseline && sessionCount() == 0,
                                "external clear stops playback");
                        open();
                    });
            later(
                    87,
                    () -> {
                        var path = pluginFolder().resolve("animations.yml");
                        originalTiming = Files.readString(path);
                        Files.writeString(path, "defaults: {speed: 0}\n");
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dmenu reload");
                        check(sessionCount() == 1, "failed reload preserves active session");
                        check(Files.readString(path).equals("defaults: {speed: 0}\n"), "failed timing reload preserves original bytes");
                        Files.writeString(path, originalTiming);
                    });
            later(
                    92,
                    () -> {
                        check(sessionCount() == 1, "playback survives rejected reload");
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dmenu reload");
                        check(sessionCount() == 0, "successful reload clears session");
                        baseline = dialogs;
                    });
            later(
                    100,
                    () -> {
                        check(dialogs == baseline, "reload cannot resurrect animation");
                        check(
                                player().getInventory().contains(org.bukkit.Material.DIAMOND, 7),
                                "inventory preserved");
                        open();
                    });
            later(
                    104,
                    () -> {
                        player().performCommand("dmenu open demo-dialogue");
                        pump();
                        baseline = dialogs;
                        check(sessionCount() == 0, "ordinary menu switch cancels animation");
                    });
            later(
                    111,
                    () -> {
                        check(dialogs == baseline, "ordinary menu not overwritten");
                        open();
                    });
            later(
                    115,
                    () -> {
                        Bukkit.getPluginManager()
                                .callEvent(
                                        new org.bukkit.event.player.PlayerQuitEvent(
                                                player(),
                                                (net.kyori.adventure.text.Component) null));
                        check(sessionCount() == 0, "quit clears session");
                        baseline = dialogs;
                    });
            later(
                    122,
                    () -> {
                        check(dialogs == baseline, "quit cancels scheduled frames");
                        Files.writeString(pluginFolder().resolve("animations.yml"), "defaults: {start: 0.2, end: 0.6, speed: 2}\ndemo-stagger: 0\n");
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dmenu reload");
                        open();
                        baseline = dialogs;
                    });
            later(125, () -> check(dialogs == baseline, "fixed start delay sends no duplicate frames"));
            later(136, () -> {
                check(dialogs == baseline + 4, "speed-scaled timing emits four changed phases and settles");
                Files.writeString(pluginFolder().resolve("animations.yml"), originalTiming);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dmenu reload");
                open();
            });
            later(
                    140,
                    () -> {
                        Bukkit.getPluginManager()
                                .disablePlugin(Bukkit.getPluginManager().getPlugin("DialogMenu"));
                        check(sessionCount() == 0, "disable clears session");
                        baseline = dialogs;
                    });
            later(
                    148,
                    () -> {
                        check(dialogs == baseline, "disable cancels playback");
                        getLogger()
                                .info(
                                        "ANIMATION_PROBE_PASS assertions="
                                                + assertions
                                                + " max-packet-bytes="
                                                + maxBytes);
                        handle.connection.disconnect(
                                net.minecraft.network.chat.Component.literal("Probe complete"));
                    });
        } catch (Throwable error) {
            fail(error);
        }
        return true;
    }

    private java.nio.file.Path pluginFolder() {
        return Bukkit.getPluginManager().getPlugin("DialogMenu").getDataFolder().toPath();
    }

    private Player player() {
        return handle.getBukkitEntity();
    }

    private void open() {
        player().performCommand("dmenu open demo-animation");
        pump();
    }

    private String token() {
        String id = ((CustomAll) last.dialog().value().onCancel().orElseThrow()).id().getPath();
        return id.substring(0, id.indexOf('/'));
    }

    private void click(String token, String action) {
        handle.connection.handleCustomClickAction(
                new ServerboundCustomClickActionPacket(
                        Identifier.parse("dialogmenu_animation:" + token + "/" + action),
                        Optional.empty()));
        pump();
    }

    private static void foreign(Player player) {
        player.showDialog(
                io.papermc.paper.dialog.Dialog.create(
                        factory ->
                                factory.empty()
                                        .base(
                                                io.papermc.paper.registry.data.dialog.DialogBase
                                                        .builder(
                                                                net.kyori.adventure.text.Component
                                                                        .text("外来菜单"))
                                                        .build())
                                        .type(
                                                io.papermc.paper.registry.data.dialog.type
                                                        .DialogType.notice())));
    }

    private int sessionCount() throws Exception {
        Class<?> type =
                Class.forName(
                        "online.toraka.dialogmenu.AnimationDialog",
                        true,
                        Bukkit.getPluginManager()
                                .getPlugin("DialogMenu")
                                .getClass()
                                .getClassLoader());
        var field = type.getDeclaredField("SESSIONS");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).size();
    }

    private void spawn() {
        var server = ((CraftServer) Bukkit.getServer()).getServer();
        var level = ((CraftWorld) Bukkit.getWorlds().getFirst()).getHandle();
        var profile =
                new GameProfile(
                        UUIDUtil.createOfflinePlayerUUID("AnimationProbe"), "AnimationProbe");
        handle = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channel =
                new EmbeddedChannel(
                        new ChannelOutboundHandlerAdapter() {
                            @Override
                            public void write(
                                    ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                                outbound.add(msg);
                                promise.setSuccess();
                            }
                        });
        channel.pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter());
        connection.channel = channel;
        connection.address = new InetSocketAddress("127.0.0.1", 41125);
        server.getPlayerList()
                .placeNewPlayer(
                        connection, handle, CommonListenerCookie.createInitial(profile, false));
        var connections = server.getConnection().getConnections();
        synchronized (connections) {
            connections.add(connection);
        }
        player().setInvulnerable(true);
        player().getInventory().clear();
        player().getInventory()
                .addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 7));
    }

    private void pump() {
        try {
            channel.runPendingTasks();
            while (!outbound.isEmpty()) {
                Object packet = outbound.removeFirst();
                if (packet instanceof ClientboundShowDialogPacket dialog) {
                    last = dialog;
                    dialogs++;
                    var buffer =
                            new RegistryFriendlyByteBuf(Unpooled.buffer(), handle.registryAccess());
                    try {
                        ClientboundShowDialogPacket.STREAM_CODEC.encode(buffer, dialog);
                        maxBytes = Math.max(maxBytes, buffer.readableBytes());
                        ClientboundShowDialogPacket.STREAM_CODEC.decode(buffer);
                    } finally {
                        buffer.release();
                    }
                } else if (packet instanceof ClientboundClearDialogPacket) clears++;
                else if (packet instanceof ClientboundKeepAlivePacket keep)
                    handle.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keep.getId()));
                else if (packet instanceof ClientboundPlayerPositionPacket position)
                    handle.connection.handleAcceptTeleportPacket(
                            new ServerboundAcceptTeleportationPacket(position.id(),
                                    position.change().position().x(), position.change().position().y(), position.change().position().z(),
                                    position.change().yRot(), position.change().xRot()));
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
        getLogger().info("PASS " + message);
    }

    private void later(long ticks, Checked body) {
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            if (!failed)
                                try {
                                    pumpIfPresent();
                                    body.run();
                                } catch (Throwable error) {
                                    fail(error);
                                }
                        },
                        ticks);
    }

    private void pumpIfPresent() {
        if (channel != null) pump();
    }

    private void fail(Throwable error) {
        if (!failed) {
            failed = true;
            getLogger().log(java.util.logging.Level.SEVERE, "ANIMATION_PROBE_FAIL", error);
        }
    }

    private interface Checked {
        void run() throws Exception;
    }
}
