package online.toraka.dialogmenu;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Read-only resource-pack bookkeeping on the players' Netty channels, before and after
 * PlayerJoinEvent.
 *
 * <p>Paper fires {@code PlayerResourcePackStatusEvent} only in the play phase, but CraftEngine and
 * other senders push their packs during the configuration phase. So, as the TabooLib build did
 * with its packet events, a read-only observer watches every connection:
 *
 * <ul>
 *   <li>outgoing {@code ClientboundLoginFinishedPacket} binds the channel to the player's UUID;
 *   <li>outgoing pushes and pops (also inside bundles) reset the pack's loaded state;
 *   <li>incoming {@code ServerboundResourcePackPacket} responses record the result.
 * </ul>
 *
 * <p>New connections get the observer from Paper's channel-initialize hook (placed right before
 * the vanilla {@code packet_handler}, so it sees responses before CraftEngine consumes them);
 * players that joined without it (connections made before this plugin enabled, test players)
 * get it at join. Packets are only read, never replaced, cancelled or changed; no server or
 * plugin handler is replaced. The packet classes are read by their Mojang names and record
 * accessors, which Paper 1.21.11 and 26.2 both use at runtime.
 */
final class ResourcePackPackets {

    static final String OBSERVER = "dialogmenu_resource_pack_observer";
    static final String CRAFT_ENGINE_OBSERVER = "dialogmenu_resource_pack_observer_ce";
    static final String CRAFT_ENGINE_HANDLER = "craftengine_player_channel_handler";
    static final String PACKET_HANDLER = "packet_handler";
    private static final AttributeKey<UUID> PLAYER_ID =
            AttributeKey.valueOf("dialogmenu:resource_pack_player");
    private static final Key LISTENER_KEY = Key.key("dialogmenu", "resource_pack_observer");
    private static final String HOLDER = "io.papermc.paper.network.ChannelInitializeListenerHolder";
    private static final String LISTENER = "io.papermc.paper.network.ChannelInitializeListener";

    private static final Set<Channel> CHANNELS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Method> ACCESSORS = new ConcurrentHashMap<>();
    private static volatile boolean hooked;
    private static volatile PackLoadTracker tracker;

    private ResourcePackPackets() {}

    /** Hooks new connections and the players already online. */
    static void start(PackLoadTracker target) {
        track(target);
        try {
            ClassLoader loader = Bukkit.getServer().getClass().getClassLoader();
            Class<?> holder = Class.forName(HOLDER, true, loader);
            Class<?> listenerType = Class.forName(LISTENER, true, loader);
            InvocationHandler handler =
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "afterInitChannel" -> {
                                inject((Channel) args[0], null);
                                return null;
                            }
                            case "hashCode" -> {
                                return System.identityHashCode(proxy);
                            }
                            case "equals" -> {
                                return proxy == args[0];
                            }
                            case "toString" -> {
                                return "DialogMenu resource-pack observer";
                            }
                            default -> {
                                return null;
                            }
                        }
                    };
            Object listener =
                    Proxy.newProxyInstance(loader, new Class<?>[] {listenerType}, handler);
            holder.getMethod("addListener", Key.class, listenerType)
                    .invoke(null, LISTENER_KEY, listener);
            hooked = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            MenuLog.warning("无法监听新连接的资源包状态：" + error.getClass().getSimpleName() + "；只在玩家进服后记录。");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            joined(player);
        }
    }

    /** Where observed responses are recorded (package-private for the channel tests). */
    static void track(PackLoadTracker target) {
        tracker = target;
    }

    /** A player that joined without the observer (or before the plugin enabled) gets it now. */
    static void joined(Player player) {
        Channel channel = channel(player);
        if (channel != null) {
            inject(channel, player.getUniqueId());
        }
    }

    /** Adds the observer unless the channel has it; {@code known} binds a player's channel. */
    static void inject(Channel channel, UUID known) {
        if (known != null && channel.attr(PLAYER_ID).get() == null) {
            channel.attr(PLAYER_ID).set(known);
            channel.closeFuture().addListener(future -> closed(channel, known));
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(OBSERVER) != null) {
            return;
        }
        Observer observer = new Observer(true);
        try {
            if (pipeline.get(PACKET_HANDLER) != null) {
                pipeline.addBefore(PACKET_HANDLER, OBSERVER, observer);
            } else {
                pipeline.addLast(OBSERVER, observer);
            }
            CHANNELS.add(channel);
        } catch (RuntimeException alreadyThere) {
            // Another thread added it first, or the channel is closing.
        }
    }

    private static void closed(Channel channel, UUID uuid) {
        CHANNELS.remove(channel);
        PackLoadTracker target = tracker;
        if (target != null) {
            target.forget(uuid);
        }
    }

    /** The player's Netty channel: {@code getHandle().connection.connection.channel}. */
    private static Channel channel(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object listener = field(handle, "connection");
            Object connection = field(listener, "connection");
            Object channel = field(connection, "channel");
            return channel instanceof Channel netty ? netty : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return null;
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException next) {
                // Declared on a superclass.
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** A record component (or, failing that, a field) of a packet, like TabooLib's read(name). */
    private static Object read(Object packet, String name) throws ReflectiveOperationException {
        Class<?> type = packet.getClass();
        Method accessor =
                ACCESSORS.computeIfAbsent(
                        type.getName() + "#" + name,
                        key -> {
                            try {
                                Method method = type.getMethod(name);
                                method.setAccessible(true);
                                return method;
                            } catch (NoSuchMethodException missing) {
                                return null;
                            }
                        });
        if (accessor != null) {
            return accessor.invoke(packet);
        }
        return field(packet, name);
    }

    private static UUID profileId(Object profile) throws ReflectiveOperationException {
        Method id;
        try {
            id = profile.getClass().getMethod("id");
        } catch (NoSuchMethodException older) {
            id = profile.getClass().getMethod("getId");
        }
        return (UUID) id.invoke(profile);
    }

    private static void outgoing(Channel channel, Object packet)
            throws ReflectiveOperationException {
        UUID bound = channel.attr(PLAYER_ID).get();
        if (bound != null) {
            outgoing(bound, packet);
        }
        if (!packet.getClass().getSimpleName().equals("ClientboundLoginFinishedPacket")) {
            return;
        }
        Object profile = read(packet, "gameProfile");
        if (profile == null) {
            return;
        }
        UUID uuid = profileId(profile);
        channel.attr(PLAYER_ID).set(uuid);
        channel.closeFuture().addListener(future -> closed(channel, uuid));
        // CraftEngine consumes configuration-stage responses. The observer normally sits ahead
        // of its handler; if CraftEngine was placed first, observe its input as well. Only the
        // public pipeline API is used; nothing is replaced, cancelled or changed.
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(CRAFT_ENGINE_HANDLER) != null
                && pipeline.get(CRAFT_ENGINE_OBSERVER) == null
                && !ahead(pipeline, OBSERVER, CRAFT_ENGINE_HANDLER)) {
            pipeline.addBefore(CRAFT_ENGINE_HANDLER, CRAFT_ENGINE_OBSERVER, new Observer(false));
            CHANNELS.add(channel);
        }
    }

    private static boolean ahead(ChannelPipeline pipeline, String first, String second) {
        List<String> names = pipeline.names();
        int a = names.indexOf(first);
        int b = names.indexOf(second);
        return a >= 0 && b >= 0 && a < b;
    }

    private static void outgoing(UUID uuid, Object packet) throws ReflectiveOperationException {
        PackLoadTracker target = tracker;
        if (target == null) {
            return;
        }
        switch (packet.getClass().getSimpleName()) {
            case "ClientboundBundlePacket" -> {
                Object contents = read(packet, "subPackets");
                if (contents instanceof Iterable<?> packets) {
                    for (Object inner : packets) {
                        if (inner != null) {
                            outgoing(uuid, inner);
                        }
                    }
                }
            }
            case "ClientboundResourcePackPushPacket" -> {
                if (read(packet, "id") instanceof UUID id) {
                    target.record(uuid, id, false);
                }
            }
            case "ClientboundResourcePackPopPacket" -> {
                if (read(packet, "id") instanceof Optional<?> id) {
                    if (id.isPresent() && id.get() instanceof UUID pack) {
                        target.record(uuid, pack, false);
                    } else if (id.isEmpty()) {
                        target.forget(uuid);
                    }
                }
            }
            default -> {}
        }
    }

    private static void incoming(Channel channel, Object packet)
            throws ReflectiveOperationException {
        if (!packet.getClass().getSimpleName().equals("ServerboundResourcePackPacket")) {
            return;
        }
        UUID uuid = channel.attr(PLAYER_ID).get();
        PackLoadTracker target = tracker;
        if (uuid == null || target == null) {
            return;
        }
        if (!(read(packet, "id") instanceof UUID pack)) {
            return;
        }
        if (!(read(packet, "action") instanceof Enum<?> action)) {
            return;
        }
        target.record(uuid, pack, action.name().equals("SUCCESSFULLY_LOADED"));
    }

    /** Observes decoded packets through Netty's public pipeline API and passes each on as is. */
    private static final class Observer extends ChannelDuplexHandler {
        private final boolean outbound;
        private boolean warned;

        Observer(boolean outbound) {
            this.outbound = outbound;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            try {
                incoming(context.channel(), message);
            } catch (Throwable error) {
                warn("无法读取资源包响应：", error);
            }
            super.channelRead(context, message);
        }

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
                throws Exception {
            if (outbound) {
                try {
                    outgoing(context.channel(), message);
                } catch (Throwable error) {
                    warn("无法读取资源包发送记录：", error);
                }
            }
            super.write(context, message, promise);
        }

        private void warn(String prefix, Throwable error) {
            if (!warned) {
                warned = true;
                MenuLog.warning(prefix + error.getClass().getSimpleName());
            }
        }
    }

    /** Stops observing: no new connections, and every added observer is removed. */
    static void shutdown() {
        if (hooked) {
            try {
                ClassLoader loader = Bukkit.getServer().getClass().getClassLoader();
                Class.forName(HOLDER, true, loader)
                        .getMethod("removeListener", Key.class)
                        .invoke(null, LISTENER_KEY);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // The server is stopping; new connections no longer matter.
            }
            hooked = false;
        }
        for (Channel channel : CHANNELS) {
            channel.eventLoop()
                    .execute(
                            () -> {
                                remove(channel, OBSERVER);
                                remove(channel, CRAFT_ENGINE_OBSERVER);
                            });
        }
        CHANNELS.clear();
        tracker = null;
    }

    private static void remove(Channel channel, String name) {
        ChannelHandler handler = channel.pipeline().get(name);
        if (handler != null) {
            channel.pipeline().remove(name);
        }
    }
}
