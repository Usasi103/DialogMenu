package online.toraka.fullscreenprobe;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Runs actual NMS packet objects through an EmbeddedChannel. Does not emulate rendering. */
public final class FullscreenProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private boolean visual;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void clearVisualLabels() {
        // Visual runs persist their diagnostic labels. Load the test chunk before cleanup;
        // a console selector cannot see unloaded entities before the synthetic player joins.
        var world = Bukkit.getWorlds().getFirst();
        for (var entity : world.getChunkAt(0, 0).getEntities()) {
            if (entity instanceof org.bukkit.entity.TextDisplay text
                    && text.text().equals(ComponentText("WORLD LABEL MUST DISAPPEAR"))) {
                text.remove();
            }
        }
    }

    @Override
    public void onDisable() {
        if (visual) clearVisualLabels();
    }

    @org.bukkit.event.EventHandler
    public void visualJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!visual || !event.getPlayer().getName().equals("CursorVisual")) return;
        Player viewer = event.getPlayer();
        viewer.addAttachment(this, "dialogmenu.fullscreen.test", true);
        viewer.setInvulnerable(true);
        viewer.setGameMode(org.bukkit.GameMode.SURVIVAL);
        viewer.getWorld().setTime(6000);
        viewer.teleport(
                new org.bukkit.Location(
                        viewer.getWorld(),
                        0.5,
                        viewer.getWorld().getHighestBlockYAt(0, 0) + 1,
                        0.5,
                        0,
                        0));
        var label =
                viewer.getWorld()
                        .spawn(
                                viewer.getEyeLocation().add(0, 0, 3),
                                org.bukkit.entity.TextDisplay.class);
        label.text(ComponentText("WORLD LABEL MUST DISAPPEAR"));
        label.setSeeThrough(true);
        label.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        label.setBackgroundColor(org.bukkit.Color.fromARGB(0xffff00ff));
        Bukkit.getScheduler().runTaskLater(this, () -> viewer.performCommand("dfullscreen"), 60);
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () ->
                                Bukkit.dispatchCommand(
                                        Bukkit.getConsoleSender(), "dfullscreen status"),
                        300);
    }

    private ServerPlayer handle;
    private EmbeddedChannel channel;
    private PositionMoveRotation local;
    private final List<String> chat = new ArrayList<>();
    private final Set<Integer> spawned = new HashSet<>();
    private final ArrayDeque<Object> outbound = new ArrayDeque<>();
    private int camera;
    private int capture;
    private int mode;
    private int metadata;
    private int dialogs;
    private int escapedSynthetic;
    private int baselineMetadata;
    private int passed;
    private int initialItems;
    private boolean started;
    private boolean answering = true;
    private int packOffers;
    private float clientFlyingSpeed;
    private long clientGameTime;
    private boolean deferPack;
    private java.util.UUID deferredPack;
    private String originalFullscreenMenu;

    private java.nio.file.Path fullscreenMenu() {
        return Bukkit.getPluginManager().getPlugin("DialogMenu").getDataFolder().toPath()
                .resolve("menus/demo-fullscreen.yml");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("fvisual")) {
            visual = true;
            sender.sendMessage(
                    "Visual probe armed for CursorVisual; synthetic probe is not running.");
            return true;
        }
        if (started) return true;
        started = true;
        try {
            clearVisualLabels();
            spawn();
            Bukkit.getScheduler().runTaskTimer(this, this::pump, 1, 1);
            later(5, () -> player().performCommand("dfullscreen"));
            later(
                    8,
                    () -> {
                        check(spawned.isEmpty(), "declined pack never opens the scene");
                        player().performCommand("dfullscreen");
                    });
            later(
                    15,
                    () -> {
                        check(camera == handle.getId(), "local player camera retained");
                        check(
                                mode == 3 && player().getGameMode() == org.bukkit.GameMode.SURVIVAL,
                                "client-only spectator");
                        check(spawned.size() == 2, "2 packet entities");
                        check(clientGameTime < 0, "local shader world mask active");
                        handle.connection.send(
                                new ClientboundSetTimePacket(123456, java.util.Map.of()));
                        check(
                                clientFlyingSpeed == 0
                                        && handle.getAbilities().getFlyingSpeed() > 0,
                                "movement frozen only on client");
                        check(
                                player().getWorld().getEntities().stream()
                                        .noneMatch(e -> spawned.contains(e.getEntityId())),
                                "no world entities");
                        check(
                                channel.pipeline().get("dialogmenu_fullscreen_input") != null,
                                "input handler installed");
                        move(-59.5f, -33.5f);
                        right();
                        right();
                    });
            later(
                    20,
                    () -> {
                        check(
                                clientGameTime < 0,
                                "ordinary time update preserves local shader mask");
                        check(
                                chat.stream().filter(s -> s.contains("左上")).count() == 1,
                                "right click action and duplicate suppression");
                        move(0, 0);
                        channel.writeInbound(new ServerboundAttackPacket(capture));
                        channel.writeInbound(ServerboundPunchPacket.INSTANCE);
                    });
            later(
                    25,
                    () -> {
                        check(
                                chat.stream().filter(s -> s.contains("中央点击")).count() == 1,
                                "left click action and duplicate suppression");
                        move(0, 81);
                    });
            later(
                    32,
                    () -> {
                        check(local.xRot() == 81, "local pitch not reset by network");
                        move(30, 10);
                        baselineMetadata = metadata;
                    });
            later(
                    42,
                    () -> {
                        check(
                                metadata == baselineMetadata,
                                "local pointer motion sends no metadata");
                        channel.writeInbound(
                                new ServerboundPlayerActionPacket(
                                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                        BlockPos.ZERO,
                                        Direction.DOWN));
                    });
            later(
                    49,
                    () -> {
                        closed("F exit");
                        check(
                                clientFlyingSpeed == handle.getAbilities().getFlyingSpeed(),
                                "client abilities restored");
                        player().performCommand("dfullscreen legacy");
                    });
            later(
                    59,
                    () -> {
                        check(
                                camera != handle.getId() && spawned.size() == 21,
                                "legacy comparison remains available");
                        move(-24, 22.5f);
                        right();
                    });
            later(
                    67,
                    () -> {
                        closed("open existing menu");
                        check(dialogs > 0, "DialogMenu action opens real dialog");
                        player().closeDialog();
                        player().performCommand("dfullscreen");
                    });
            later(77, () -> player().teleport(player().getLocation().add(1, 0, 0)));
            later(
                    82,
                    () -> {
                        closed("external teleport");
                        player().performCommand("dfullscreen");
                    });
            later(92, () -> player().setGameMode(org.bukkit.GameMode.CREATIVE));
            later(
                    98,
                    () -> {
                        check(
                                camera == handle.getId() && mode == 1,
                                "game mode change preserves new mode");
                        check(spawned.isEmpty(), "game mode change removes scene");
                        player().setGameMode(org.bukkit.GameMode.SURVIVAL);
                        player().performCommand("dfullscreen");
                    });
            later(
                    108,
                    () -> {
                        channel.writeInbound(
                                new ServerboundSpectatorActionPacket(
                                        java.util.OptionalInt.empty()));
                    });
            later(
                    115,
                    () -> {
                        closed("spectator detach / Shift");
                        player().performCommand("dfullscreen");
                    });
            later(
                    126,
                    () -> {
                        answering = false;
                        baselineMetadata = metadata;
                    });
            later(
                    245,
                    () -> {
                        answering = true;
                        closed("unresponsive client timeout");
                        player().performCommand("dfullscreen");
                    });
            later(
                    255,
                    () -> {
                        try {
                            originalFullscreenMenu = java.nio.file.Files.readString(fullscreenMenu());
                            java.nio.file.Files.writeString(fullscreenMenu(), "MenuType: dialog\nPreset: diagnostic\n");
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dmenu reload");
                        } catch (java.io.IOException error) { throw new RuntimeException(error); }
                    });
            later(261, () -> {
                check(spawned.size() == 2 && mode == 3, "invalid reload preserves active fullscreen");
                try {
                    check(java.nio.file.Files.readString(fullscreenMenu()).equals("MenuType: dialog\nPreset: diagnostic\n"),
                            "invalid reload preserves source bytes");
                    java.nio.file.Files.writeString(fullscreenMenu(), originalFullscreenMenu);
                } catch (java.io.IOException error) { throw new RuntimeException(error); }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dmenu reload");
            });
            later(270, () -> {
                closed("successful reload");
                player().performCommand("dmenu open demo-fullscreen");
            });
            later(280, () -> {
                check(spawned.size() == 2, "catalog routes fullscreen within DialogMenu");
                player().performCommand("dmenu open demo-settings");
            });
            later(290, () -> {
                closed("catalog fullscreen to Dialog");
                check(dialogs > 1, "catalog transition shows real Dialog");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dmenu reload");
            });
            later(295, () -> {
                deferPack = true;
                player().performCommand("dmenu open demo-fullscreen");
            });
            later(303, () -> {
                check(deferredPack != null && spawned.isEmpty(), "fullscreen waits for pack acknowledgement");
                player().performCommand("dmenu open demo-settings");
            });
            later(308, () -> {
                Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerResourcePackStatusEvent(
                        player(), deferredPack, org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
                deferPack = false;
            });
            later(315, () -> {
                closed("late pack acknowledgement after Dialog");
                player().performCommand("dfullscreen");
            });
            later(
                    325,
                    () ->
                            Bukkit.getPluginManager()
                                    .disablePlugin(
                                            Bukkit.getPluginManager()
                                                    .getPlugin("DialogMenu")));
            later(
                    332,
                    () -> {
                        closed("plugin disable");
                        check(
                                escapedSynthetic == 0,
                                "synthetic replies never reached vanilla handler");
                        check(items() == initialItems, "inventory unchanged");
                        getLogger()
                                .info(
                                        "FULLSCREEN_PROBE_PASS assertions="
                                                + passed
                                                + "; rendering and real mouse latency NOT tested");
                        player().kick(ComponentText("Probe complete"));
                    });
        } catch (Throwable error) {
            fail(error);
        }
        return true;
    }

    private static net.kyori.adventure.text.Component ComponentText(String text) {
        return net.kyori.adventure.text.Component.text(text);
    }

    private void later(long delay, Runnable action) {
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            try {
                                action.run();
                            } catch (Throwable error) {
                                fail(error);
                            }
                        },
                        delay);
    }

    private void fail(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE, "FULLSCREEN_PROBE_FAIL", error);
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void check(boolean condition, String label) {
        if (!condition)
            throw new AssertionError(
                    label
                            + " camera="
                            + camera
                            + " mode="
                            + mode
                            + " entities="
                            + spawned.size()
                            + " chat="
                            + chat);
        passed++;
        getLogger().info("PASS " + label);
    }

    private void closed(String reason) {
        channel.runPendingTasks();
        check(camera == handle.getId(), reason + ": camera restored");
        check(
                mode == handle.gameMode.getGameModeForPlayer().getId(),
                reason + ": game mode restored");
        check(spawned.isEmpty(), reason + ": client entities removed");
        check(clientGameTime >= 0, reason + ": client shader clock restored");
        check(
                channel.pipeline().get("dialogmenu_fullscreen_input") == null,
                reason + ": handler removed");
        if (Bukkit.getPluginManager().isPluginEnabled("DialogMenu")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dfullscreen status");
        }
    }

    private Player player() {
        return handle.getBukkitEntity();
    }

    private int items() {
        return java.util.Arrays.stream(player().getInventory().getContents())
                .filter(java.util.Objects::nonNull)
                .mapToInt(org.bukkit.inventory.ItemStack::getAmount)
                .sum();
    }

    private void move(float yaw, float pitch) {
        local = new PositionMoveRotation(local.position(), Vec3.ZERO, yaw, pitch);
        channel.writeInbound(
                new ServerboundMovePlayerPacket.PosRot(local.position(), yaw, pitch, true, false));
    }

    private void right() {
        channel.writeInbound(
                new ServerboundInteractPacket(
                        capture, InteractionHand.MAIN_HAND, Vec3.ZERO, false));
    }

    private void spawn() {
        var server = ((CraftServer) Bukkit.getServer()).getServer();
        var level = ((CraftWorld) Bukkit.getWorlds().getFirst()).getHandle();
        var profile =
                new GameProfile(
                        UUIDUtil.createOfflinePlayerUUID("FullscreenProbe"), "FullscreenProbe");
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
        channel.pipeline()
                .addLast(
                        "packet_handler",
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                if (msg instanceof ServerboundAcceptTeleportationPacket ack) {
                                    if (ack.id() < 0) escapedSynthetic++;
                                    else handle.connection.handleAcceptTeleportPacket(ack);
                                }
                            }
                        });
        connection.channel = channel;
        connection.address = new InetSocketAddress("127.0.0.1", 41123);
        server.getPlayerList()
                .placeNewPlayer(
                        connection, handle, CommonListenerCookie.createInitial(profile, false));
        var connections = server.getConnection().getConnections();
        synchronized (connections) {
            connections.add(connection);
        }
        player().addAttachment(this, "dialogmenu.fullscreen.test", true);
        player().setGameMode(org.bukkit.GameMode.SURVIVAL);
        player().setInvulnerable(true);
        player().getInventory().clear();
        player().getInventory()
                .addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 7));
        initialItems = items();
        local = PositionMoveRotation.of(handle);
        // Demo's fixed canvas does not depend on server yaw; use a known input baseline.
        var ground = player().getLocation().setDirection(new org.bukkit.util.Vector(0, 0, 1));
        ground.setY(player().getWorld().getHighestBlockYAt(ground) + 1);
        player().teleport(ground);
    }

    private void pump() {
        try {
            while (!outbound.isEmpty()) observe(outbound.removeFirst());
            channel.runPendingTasks();
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void observe(Object packet) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (var child : bundle.subPackets()) observe(child);
        } else if (packet
                instanceof
                net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket pack) {
            check(!pack.required(), "resource pack is optional");
            try (var stream = java.net.URI.create(pack.url()).toURL().openStream()) {
                byte[] bytes = stream.readAllBytes();
                check(
                        java.util.HexFormat.of()
                                .formatHex(
                                        java.security.MessageDigest.getInstance("SHA-1")
                                                .digest(bytes))
                                .equals(pack.hash()),
                        "served pack hash matches offer");
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
            if (deferPack) {
                deferredPack = pack.id();
                return;
            }
            Bukkit.getPluginManager()
                    .callEvent(
                            new org.bukkit.event.player.PlayerResourcePackStatusEvent(
                                    player(),
                                    pack.id(),
                                    ++packOffers == 1
                                            ? org.bukkit.event.player.PlayerResourcePackStatusEvent
                                                    .Status.DECLINED
                                            : org.bukkit.event.player.PlayerResourcePackStatusEvent
                                                    .Status.SUCCESSFULLY_LOADED));
        } else if (packet instanceof ClientboundPlayerAbilitiesPacket abilities) {
            clientFlyingSpeed = abilities.getFlyingSpeed();
        } else if (packet instanceof ClientboundSetTimePacket time) {
            clientGameTime = time.gameTime();
        } else if (packet instanceof ClientboundAddEntityPacket add) {
            if (add.getType() == EntityTypes.TEXT_DISPLAY
                    || add.getType() == EntityTypes.INTERACTION) spawned.add(add.getId());
            if (add.getType() == EntityTypes.INTERACTION) capture = add.getId();
        } else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            for (int id : remove.entityIds()) spawned.remove(id);
        } else if (packet instanceof ClientboundSetCameraPacket setCamera) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundSetCameraPacket.STREAM_CODEC.encode(buf, setCamera);
                camera = buf.readVarInt();
            } finally {
                buf.release();
            }
        } else if (packet instanceof ClientboundGameEventPacket event
                && event.getEvent() == ClientboundGameEventPacket.CHANGE_GAME_MODE) {
            mode = (int) event.getParam();
        } else if (packet instanceof ClientboundPlayerPositionPacket position) {
            if (!answering) return;
            local =
                    PositionMoveRotation.calculateAbsolute(
                            local, position.change(), position.relatives());
            channel.writeInbound(new ServerboundAcceptTeleportationPacket(position.id(),
                    local.position().x(), local.position().y(), local.position().z(), local.yRot(), local.xRot()));
        } else if (packet instanceof ClientboundSetEntityDataPacket data
                && spawned.contains(data.id())) {
            metadata++;
        } else if (packet instanceof ClientboundSystemChatPacket message) {
            chat.add(message.content().getString());
        } else if (packet instanceof ClientboundKeepAlivePacket keepalive) {
            handle.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepalive.getId()));
        } else if (packet.getClass().getSimpleName().equals("ClientboundShowDialogPacket")) {
            dialogs++;
        }
    }
}
