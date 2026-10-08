package online.toraka.dialogmenu;

import dev.keystone.task.Task;
import dev.keystone.task.Tasks;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.PaperDialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import org.bukkit.entity.Player;

/** Player-local playback; late clicks and foreign dialogs cannot restart an abandoned animation. */
final class AnimationDialog {
    private static final String NAMESPACE = "dialogmenu_animation";
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static final class Session {
        final Player player;
        final AnimationPreset preset;
        final AnimationSettings settings;
        final String token = UUID.randomUUID().toString().replace("-", "");
        final Set<Object> pending = ConcurrentHashMap.newKeySet();
        volatile boolean active = true;
        int tick;
        String notice = "悬停查看名称，点击图标查看反馈";
        Task playback;

        Session(Player player, AnimationPreset preset) {
            this.player = player;
            this.preset = preset;
            this.settings = MenuRuntime.animationSettings();
        }

        Key route(String action) {
            return Key.key(NAMESPACE, token + "/" + action);
        }
    }

    private AnimationDialog() {}

    static void open(Player player, AnimationPreset preset) {
        if (!player.hasPermission("playersettings.use")) return;
        MenuResources.open(
                player,
                () -> {
                    if (!player.isOnline() || !player.hasPermission("playersettings.use")) return;
                    player.closeInventory();
                    MenuDialog.forget(player.getUniqueId());
                    TemplateDialog.forget(player.getUniqueId());
                    start(player, preset);
                });
    }

    private static void start(Player player, AnimationPreset preset) {
        forget(player.getUniqueId());
        Session session = new Session(player, preset);
        SESSIONS.put(player.getUniqueId(), session);
        try {
            show(session);
            session.playback =
                    Tasks.timer(
                            1,
                            1,
                            task -> {
                                if (!session.active
                                        || !player.isOnline()
                                        || player.isDead()
                                        || !player.hasPermission("playersettings.use")) {
                                    discard(session);
                                    task.cancel();
                                    return;
                                }
                                session.tick++;
                                try {
                                    if (AnimationDemo.changed(
                                            session.settings, session.preset, session.tick))
                                        show(session);
                                } catch (RuntimeException error) {
                                    discard(session);
                                    task.cancel();
                                    MenuLog.warning("动画演示停止：" + error.getMessage());
                                    return;
                                }
                                if (session.tick >= session.settings.durationTicks(session.preset))
                                    task.cancel();
                            });
        } catch (RuntimeException error) {
            discard(session);
            player.sendMessage("DialogMenu：动画演示暂时无法打开，请查看后台。");
            MenuLog.warning("动画演示打开失败：" + error.getMessage());
        }
    }

    private static void show(Session session) {
        if (!session.active) return;
        Component canvas =
                AnimationDemo.render(
                                session.preset,
                                session.settings,
                                session.tick,
                                session.notice,
                                action -> DialogClicks.custom(session.route(action)))
                        .build();
        Dialog dialog =
                Dialog.create(
                        factory ->
                                factory.empty()
                                        .base(
                                                DialogBase.builder(Component.text("动画演示"))
                                                        .canCloseWithEscape(true)
                                                        .pause(false)
                                                        .afterAction(
                                                                DialogBase.DialogAfterAction.NONE)
                                                        .body(
                                                                List.of(
                                                                        DialogBody.plainMessage(
                                                                                canvas,
                                                                                AnimationDemo.WIDTH
                                                                                        + DialogCanvas
                                                                                                .WRAP_SLACK
                                                                                        + 2
                                                                                                * DialogCanvas
                                                                                                        .BODY_PADDING)))
                                                        .build())
                                        .type(
                                                DialogType.multiAction(
                                                                List.of(
                                                                        ActionButton.create(
                                                                                Component.text(
                                                                                        "重播动画"),
                                                                                Component.text(
                                                                                        "重新播放当前预设"),
                                                                                100,
                                                                                DialogAction
                                                                                        .customClick(
                                                                                                session
                                                                                                        .route(
                                                                                                                "replay"),
                                                                                                null)),
                                                                        ActionButton.create(
                                                                                Component.text(
                                                                                        "上一个效果"),
                                                                                null,
                                                                                100,
                                                                                DialogAction
                                                                                        .customClick(
                                                                                                session
                                                                                                        .route(
                                                                                                                "previous"),
                                                                                                null)),
                                                                        ActionButton.create(
                                                                                Component.text(
                                                                                        "下一个效果"),
                                                                                null,
                                                                                100,
                                                                                DialogAction
                                                                                        .customClick(
                                                                                                session
                                                                                                        .route(
                                                                                                                "next"),
                                                                                                null))))
                                                        .columns(3)
                                                        .exitAction(
                                                                ActionButton.create(
                                                                        Component.text("关闭"),
                                                                        null,
                                                                        150,
                                                                        DialogAction.customClick(
                                                                                session.route(
                                                                                        "close"),
                                                                                null)))
                                                        .build()));
        // Track the exact holder, not a packet counter: even interleaved or bundled external
        // dialogs must cancel playback before the next tick can replace their screen.
        Object holder = PaperDialog.bukkitToMinecraftHolder(dialog);
        session.pending.add(holder);
        try {
            session.player.showDialog(dialog);
        } catch (RuntimeException error) {
            session.pending.remove(holder);
            throw error;
        }
    }

    static void clicked(PlayerCustomClickEvent event) {
        if (!event.getIdentifier().namespace().equals(NAMESPACE)
                || !(event.getCommonConnection() instanceof PlayerGameConnection connection))
            return;
        Player player = connection.getPlayer();
        if (player == null) return;
        String route = event.getIdentifier().value();
        Tasks.sync(() -> handle(player, route));
    }

    static void handle(Player player, String route) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null
                || !session.active
                || !player.isOnline()
                || !route.startsWith(session.token + "/")) return;
        String action = route.substring(session.token.length() + 1);
        if (action.equals("close") || !player.hasPermission("playersettings.use")) {
            discard(session);
            player.closeDialog();
        } else if (action.equals("replay")) {
            start(player, session.preset);
        } else if (action.equals("previous") || action.equals("next")) {
            AnimationPreset[] presets = AnimationPreset.values();
            int index =
                    Math.floorMod(
                            session.preset.ordinal() + (action.equals("next") ? 1 : -1),
                            presets.length);
            start(player, presets[index]);
        } else {
            for (int icon = 0; icon < AnimationDemo.NAMES.size(); icon++) {
                if (action.equals("icon/" + icon)
                        && session.preset
                                .frame(
                                        AnimationDemo.progress(
                                                session.settings,
                                                session.preset,
                                                session.tick,
                                                icon))
                                .visible()) {
                    session.notice = "已点击：" + AnimationDemo.NAMES.get(icon);
                    show(session);
                    return;
                }
            }
        }
    }

    /** Called by the existing read-only Netty observer; only invalidation happens off-thread. */
    static void sent(UUID uuid, Object packet) {
        if (packet instanceof BundlePacket<?> bundle) {
            for (Object part : bundle.subPackets()) sent(uuid, part);
            return;
        }
        Session session = SESSIONS.get(uuid);
        if (session == null || !session.active) return;
        boolean external = packet instanceof ClientboundClearDialogPacket;
        if (packet instanceof ClientboundShowDialogPacket dialog) {
            external = !session.pending.remove(dialog.dialog());
        }
        if (external) {
            session.active = false;
            Tasks.run(() -> discard(session));
        }
    }

    static void forget(UUID uuid) {
        Session session = SESSIONS.get(uuid);
        if (session != null) discard(session);
    }

    private static void discard(Session session) {
        session.active = false;
        if (session.playback != null) session.playback.cancel();
        session.pending.clear();
        SESSIONS.remove(session.player.getUniqueId(), session);
    }

    static void shutdown() {
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            boolean close = session.active;
            discard(session);
            if (close && session.player.isOnline()) session.player.closeDialog();
        }
    }
}
