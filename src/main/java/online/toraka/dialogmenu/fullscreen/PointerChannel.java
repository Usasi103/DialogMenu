package online.toraka.dialogmenu.fullscreen;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Exact Paper 26.3 boundary. Synthetic replies are consumed before the vanilla packet handler. */
final class PointerChannel extends ChannelDuplexHandler {
    private static final String NAME = "dialogmenu_fullscreen_input";
    private static final AtomicInteger IDS = new AtomicInteger(-1_000_000_000);
    private static final Set<Relative> KEEP_ALL = Set.copyOf(EnumSet.allOf(Relative.class));
    private static final Set<Relative> KEEP_POSITION =
            Set.of(
                    Relative.X,
                    Relative.Y,
                    Relative.Z,
                    Relative.DELTA_X,
                    Relative.DELTA_Y,
                    Relative.DELTA_Z);
    private final ServerPlayer player;
    private final PointerState state;
    private final int captureId;
    private final Channel channel;
    private volatile boolean externalTeleport;
    private volatile boolean worldMasked;

    void maskWorld(boolean enabled) {
        if (worldMasked == enabled) return;
        worldMasked = enabled;
        // Client-only shader marker. 26.3 clockUpdates are preserved by the outbound hook.
        send(
                new ClientboundSetTimePacket(
                        enabled ? -12000 : player.level().getGameTime(), java.util.Map.of()));
    }

    PointerChannel(ServerPlayer player, PointerState state, int captureId) {
        this.player = player;
        this.state = state;
        this.captureId = captureId;
        channel = player.connection.connection.channel;
    }

    void install() {
        if (channel == null
                || !channel.isActive()
                || channel.pipeline().get("packet_handler") == null) {
            throw new IllegalStateException("玩家没有可用的网络连接");
        }
        if (channel.pipeline().get(NAME) != null) throw new IllegalStateException("已有全屏输入会话");
        channel.pipeline().addBefore("packet_handler", NAME, this);
    }

    void detach() {
        if (channel != null)
            channel.eventLoop()
                    .execute(
                            () -> {
                                if (channel.pipeline().context(this) != null)
                                    channel.pipeline().remove(this);
                            });
    }

    void camera(Entity camera) {
        send(new ClientboundSetCameraPacket(camera));
    }

    void mode(int id) {
        send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, id));
    }

    void freezeLocalMovement() {
        var abilities = new net.minecraft.world.entity.player.Abilities();
        abilities.invulnerable = true;
        abilities.flying = true;
        abilities.mayfly = true;
        abilities.setFlyingSpeed(0);
        abilities.setWalkingSpeed(0);
        send(new ClientboundPlayerAbilitiesPacket(abilities));
    }

    void restoreAbilities() {
        send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
    }

    boolean externalTeleport() {
        return externalTeleport;
    }

    void sample() {
        position(
                PointerState.Sync.SAMPLE,
                new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, 0, 0),
                KEEP_ALL);
    }

    void reset(float yaw) {
        position(
                PointerState.Sync.RESET,
                new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, yaw, 0),
                KEEP_POSITION);
    }

    void exitPosition() {
        position(
                PointerState.Sync.EXIT,
                new PositionMoveRotation(
                        player.position(), Vec3.ZERO, player.getYRot(), player.getXRot()),
                Set.of());
    }

    private void position(
            PointerState.Sync kind, PositionMoveRotation position, Set<Relative> relative) {
        int id = IDS.getAndDecrement();
        if (state.expect(id, kind))
            send(new ClientboundPlayerPositionPacket(id, position, relative));
    }

    private void send(Packet<?> packet) {
        player.connection.send(packet);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise)
            throws Exception {
        if (worldMasked && packet instanceof ClientboundSetTimePacket time) {
            packet = new ClientboundSetTimePacket(-12000, time.clockUpdates());
        }
        // Real server teleports take priority over the prototype. Their nonnegative ID is
        // vanilla's.
        if (packet instanceof ClientboundPlayerPositionPacket position && position.id() >= 0) {
            externalTeleport = true;
            state.requestExit();
        }
        super.write(ctx, packet, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
        if (packet instanceof ServerboundAcceptTeleportationPacket ack
                && state.acknowledge(ack.id(), ack.yRot(), ack.xRot())) return;
        if (state.phase() == PointerState.Phase.CLOSED || externalTeleport) {
            super.channelRead(ctx, packet);
            return;
        }
        if (packet instanceof ServerboundMovePlayerPacket move) {
            if (move.hasRotation()) state.rotation(move.getYRot(0), move.getXRot(0));
            return;
        }
        if (packet instanceof ServerboundPlayerInputPacket input) {
            if (input.input().shift()) state.requestExit();
            return;
        }
        if (packet instanceof ServerboundPlayerAbilitiesPacket) return;
        if (packet instanceof ServerboundSpectatorActionPacket spectator) {
            if (spectator.spectateEntityId().isEmpty()) state.requestExit();
            else if (spectator.spectateEntityId().getAsInt() == captureId) click("右键");
            return;
        }
        if (packet instanceof ServerboundInteractPacket interact) {
            if (interact.entityId() == captureId && interact.hand() == InteractionHand.MAIN_HAND)
                click("右键");
            return;
        }
        if (packet instanceof ServerboundAttackPacket attack) {
            if (attack.entityId() == captureId) click("左键");
            return;
        }
        if (packet instanceof ServerboundPunchPacket) {
            click("左键");
            return;
        }
        if (packet instanceof ServerboundUseItemPacket use) {
            if (use.hand() == InteractionHand.MAIN_HAND) click("右键");
            send(new ClientboundBlockChangedAckPacket(use.sequence()));
            return;
        }
        if (packet instanceof ServerboundUseItemOnPacket use) {
            send(new ClientboundBlockChangedAckPacket(use.sequence()));
            return;
        }
        if (packet instanceof ServerboundPlayerActionPacket action) {
            if (action.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND)
                state.requestExit();
            if (action.getSequence() >= 0)
                send(new ClientboundBlockChangedAckPacket(action.getSequence()));
            return;
        }
        if (packet instanceof ServerboundTeleportToEntityPacket) return;
        super.channelRead(ctx, packet);
    }

    private void click(String button) {
        state.click(button, System.nanoTime());
    }
}
