package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Netty observer that replaced TabooLib's packet events. The packet doubles below carry the
 * Mojang simple class names and record components the observer reads on the server.
 */
class ResourcePackPacketsTest {

    record Profile(UUID id, String name) {}

    record ClientboundLoginFinishedPacket(Profile gameProfile, UUID extra) {}

    record ClientboundResourcePackPushPacket(UUID id, String url) {}

    record ClientboundResourcePackPopPacket(Optional<UUID> id) {}

    record ClientboundBundlePacket(List<Object> subPackets) {}

    enum Action {
        SUCCESSFULLY_LOADED,
        DECLINED,
        FAILED_DOWNLOAD,
        ACCEPTED,
        DOWNLOADED,
    }

    record ServerboundResourcePackPacket(UUID id, Action action) {}

    /** Stands in for vanilla's Connection: the end of the inbound path. */
    static final class Sink extends ChannelInboundHandlerAdapter {
        final List<Object> received = new ArrayList<>();

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) {
            received.add(message);
        }
    }

    /** Behaves like CraftEngine's handler: swallows resource-pack responses. */
    static final class Consumer extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            if (message instanceof ServerboundResourcePackPacket) {
                return;
            }
            super.channelRead(context, message);
        }
    }

    private final PackLoadTracker tracker = new PackLoadTracker();
    private final UUID player = UUID.randomUUID();
    private final UUID pack = UUID.randomUUID();

    @AfterEach
    void stop() {
        ResourcePackPackets.shutdown();
    }

    private EmbeddedChannel connection(Sink sink) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(ResourcePackPackets.PACKET_HANDLER, sink);
        ResourcePackPackets.track(tracker);
        // Paper's channel-initialize hook runs once the vanilla handlers are in place.
        ResourcePackPackets.inject(channel, null);
        return channel;
    }

    private void login(EmbeddedChannel channel) {
        ClientboundLoginFinishedPacket packet =
                new ClientboundLoginFinishedPacket(new Profile(player, "Alex"), UUID.randomUUID());
        channel.writeOutbound(packet);
        assertSame(packet, channel.readOutbound());
    }

    @Test
    @DisplayName("configuration-phase responses are recorded before CraftEngine consumes them")
    void configurationPhaseResponsesAreRecordedBeforeCraftEngineConsumesThem() {
        Sink sink = new Sink();
        EmbeddedChannel channel = connection(sink);
        // CraftEngine injects later, right before packet_handler: behind the observer.
        channel.pipeline()
                .addBefore(
                        ResourcePackPackets.PACKET_HANDLER,
                        ResourcePackPackets.CRAFT_ENGINE_HANDLER,
                        new Consumer());
        login(channel);
        channel.writeOutbound(new ClientboundResourcePackPushPacket(pack, "https://example"));
        assertFalse(tracker.contains(player, Set.of(pack)));
        channel.writeInbound(new ServerboundResourcePackPacket(pack, Action.ACCEPTED));
        channel.writeInbound(new ServerboundResourcePackPacket(pack, Action.DOWNLOADED));
        assertFalse(tracker.contains(player, Set.of(pack)));
        channel.writeInbound(new ServerboundResourcePackPacket(pack, Action.SUCCESSFULLY_LOADED));
        assertTrue(tracker.contains(player, Set.of(pack)));
        assertTrue(sink.received.isEmpty(), "CraftEngine still consumes the responses");
        assertNull(channel.pipeline().get(ResourcePackPackets.CRAFT_ENGINE_OBSERVER));
        List<String> names = channel.pipeline().names();
        assertTrue(
                names.indexOf(ResourcePackPackets.OBSERVER)
                        < names.indexOf(ResourcePackPackets.CRAFT_ENGINE_HANDLER));
    }

    @Test
    @DisplayName("a CraftEngine handler in front of the observer gets its own read-only observer")
    void craftEngineHandlerInFrontOfTheObserverGetsItsOwnReadOnlyObserver() {
        Sink sink = new Sink();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(ResourcePackPackets.PACKET_HANDLER, sink);
        channel.pipeline().addFirst(ResourcePackPackets.CRAFT_ENGINE_HANDLER, new Consumer());
        ResourcePackPackets.track(tracker);
        ResourcePackPackets.inject(channel, null);
        login(channel);
        assertNotNull(channel.pipeline().get(ResourcePackPackets.CRAFT_ENGINE_OBSERVER));
        List<String> names = channel.pipeline().names();
        assertEquals(
                names.indexOf(ResourcePackPackets.CRAFT_ENGINE_HANDLER) - 1,
                names.indexOf(ResourcePackPackets.CRAFT_ENGINE_OBSERVER));
        channel.writeInbound(new ServerboundResourcePackPacket(pack, Action.SUCCESSFULLY_LOADED));
        assertTrue(tracker.contains(player, Set.of(pack)));
        // The added observer only reads responses; pushes are still counted once.
        channel.writeOutbound(new ClientboundResourcePackPushPacket(pack, "https://example"));
        assertFalse(tracker.contains(player, Set.of(pack)));
    }

    @Test
    @DisplayName("pushes, pops and bundles reset the loaded state; packets pass unchanged")
    void pushesPopsAndBundlesResetTheLoadedStatePacketsPassUnchanged() {
        Sink sink = new Sink();
        EmbeddedChannel channel = connection(sink);
        login(channel);
        ServerboundResourcePackPacket loaded =
                new ServerboundResourcePackPacket(pack, Action.SUCCESSFULLY_LOADED);
        channel.writeInbound(loaded);
        assertSame(loaded, sink.received.get(0));
        assertTrue(tracker.contains(player, Set.of(pack)));
        ClientboundBundlePacket bundle =
                new ClientboundBundlePacket(
                        List.of("other", new ClientboundResourcePackPushPacket(pack, "u")));
        channel.writeOutbound(bundle);
        assertSame(bundle, channel.readOutbound(), "outgoing packets are passed on as they are");
        assertFalse(tracker.contains(player, Set.of(pack)));
        channel.writeInbound(loaded);
        UUID other = UUID.randomUUID();
        channel.writeInbound(new ServerboundResourcePackPacket(other, Action.SUCCESSFULLY_LOADED));
        channel.writeOutbound(new ClientboundResourcePackPopPacket(Optional.of(other)));
        assertTrue(tracker.contains(player, Set.of(pack)));
        assertFalse(tracker.contains(player, Set.of(other)));
        channel.writeOutbound(new ClientboundResourcePackPopPacket(Optional.empty()));
        assertFalse(tracker.contains(player, Set.of(pack)));
    }

    @Test
    @DisplayName("responses before login and after a declined pack never unlock the menu")
    void responsesBeforeLoginAndAfterADeclinedPackNeverUnlockTheMenu() {
        EmbeddedChannel channel = connection(new Sink());
        channel.writeInbound(new ServerboundResourcePackPacket(pack, Action.SUCCESSFULLY_LOADED));
        assertFalse(tracker.contains(player, Set.of(pack)));
        login(channel);
        channel.writeInbound(new ServerboundResourcePackPacket(pack, Action.SUCCESSFULLY_LOADED));
        channel.writeInbound(new ServerboundResourcePackPacket(pack, Action.DECLINED));
        assertFalse(tracker.contains(player, Set.of(pack)));
    }

    @Test
    @DisplayName(
            "a joined player's channel is bound, closing forgets it, shutdown removes observers")
    void aJoinedPlayersChannelIsBoundClosingForgetsItShutdownRemovesObservers() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ResourcePackPackets.track(tracker);
        // Test players have no packet_handler: the observer goes to the tail.
        ResourcePackPackets.inject(channel, player);
        ResourcePackPackets.inject(channel, player);
        assertEquals(
                1,
                channel.pipeline().names().stream()
                        .filter(ResourcePackPackets.OBSERVER::equals)
                        .count());
        channel.writeInbound(new ServerboundResourcePackPacket(pack, Action.SUCCESSFULLY_LOADED));
        assertTrue(tracker.contains(player, Set.of(pack)));
        EmbeddedChannel second = new EmbeddedChannel();
        ResourcePackPackets.inject(second, UUID.randomUUID());
        channel.close();
        assertFalse(tracker.contains(player, Set.of(pack)));
        ResourcePackPackets.shutdown();
        second.runPendingTasks();
        assertNull(second.pipeline().get(ResourcePackPackets.OBSERVER));
    }
}
