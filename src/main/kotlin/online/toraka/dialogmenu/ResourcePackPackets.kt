package online.toraka.dialogmenu

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.AttributeKey
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.nms.Packet
import taboolib.module.nms.PacketImpl
import taboolib.module.nms.PacketReceiveEvent
import taboolib.module.nms.PacketSendEvent

/** Read-only TabooLib packet events cover resource responses before PlayerJoinEvent. */
object ResourcePackPackets {
    private val playerId = AttributeKey.valueOf<UUID>("dialogmenu:resource_pack_player")
    private const val observer = "dialogmenu_resource_pack_observer"
    private const val craftEngineHandler = "craftengine_player_channel_handler"
    private val channels = ConcurrentHashMap.newKeySet<Channel>()

    @SubscribeEvent
    fun login(event: PacketSendEvent.Handshake) {
        event.channel.attr(playerId).get()?.let { outgoing(it, event.packet) }
        if (event.packet.name != "ClientboundLoginFinishedPacket") return
        val profile = event.packet.read<Any>("gameProfile", false) ?: return
        val uuid = profile.javaClass.getMethod("id").invoke(profile) as UUID
        event.channel.attr(playerId).set(uuid)
        event.channel.closeFuture().addListener {
            channels.remove(event.channel)
            MenuResources.tracker.forget(uuid)
        }
        // CraftEngine consumes configuration-stage responses before TabooLib's packet event.
        // Observe decoded responses through Netty's public pipeline API; never replace handlers
        // or cancel/change a packet. TabooLib and CraftEngine internals remain untouched.
        val pipeline = event.channel.pipeline()
        if (pipeline.get(craftEngineHandler) != null && pipeline.get(observer) == null) {
            pipeline.addBefore(
                craftEngineHandler,
                observer,
                object : ChannelInboundHandlerAdapter() {
                    private var warned = false

                    override fun channelRead(context: ChannelHandlerContext, message: Any) {
                        try {
                            if (message.javaClass.simpleName == "ServerboundResourcePackPacket") {
                                record(uuid, PacketImpl(message))
                            }
                        } catch (error: Exception) {
                            if (!warned) {
                                warned = true
                                Bukkit.getLogger()
                                    .warning("[DialogMenu] 无法读取资源包响应：${error.javaClass.simpleName}")
                            }
                        } finally {
                            context.fireChannelRead(message)
                        }
                    }
                },
            )
            channels.add(event.channel)
        }
    }

    @SubscribeEvent
    fun status(event: PacketReceiveEvent.Handshake) {
        if (event.packet.name != "ServerboundResourcePackPacket") return
        val uuid = event.channel.attr(playerId).get() ?: return
        record(uuid, event.packet)
    }

    @SubscribeEvent
    fun sent(event: PacketSendEvent) {
        outgoing(event.player.uniqueId, event.packet)
    }

    private fun outgoing(uuid: UUID, packet: Packet) {
        when (packet.name) {
            "ClientboundBundlePacket" -> {
                val contents =
                    packet.source.javaClass.getMethod("subPackets").invoke(packet.source)
                        as Iterable<*>
                contents.filterNotNull().forEach { outgoing(uuid, PacketImpl(it)) }
            }
            "ClientboundResourcePackPushPacket" -> {
                val id = packet.read<UUID>("id", false) ?: return
                MenuResources.tracker.record(uuid, id, false)
            }
            "ClientboundResourcePackPopPacket" -> {
                val id = packet.read<Optional<UUID>>("id", false) ?: return
                if (id.isPresent) MenuResources.tracker.record(uuid, id.get(), false)
                else MenuResources.tracker.forget(uuid)
            }
        }
    }

    private fun record(uuid: UUID, packet: Packet) {
        val pack = packet.read<UUID>("id", false) ?: return
        val action = packet.read<Enum<*>>("action", false) ?: return
        MenuResources.tracker.record(uuid, pack, action.name == "SUCCESSFULLY_LOADED")
    }

    @Awake(LifeCycle.DISABLE)
    fun shutdown() {
        channels.forEach { channel ->
            channel.eventLoop().execute {
                if (channel.pipeline().get(observer) != null) channel.pipeline().remove(observer)
            }
        }
        channels.clear()
    }
}
