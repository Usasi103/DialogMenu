package online.toraka.dialogmenu;

import dev.keystone.task.Tasks;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.set.RegistrySet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TemplateDialog {

    private record Session(
            String token,
            String template,
            Map<String, String> values,
            Set<String> actions,
            long opened) {}

    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();

    private TemplateDialog() {}

    public static void open(Player player, String requested) {
        String id = MenuRuntime.resolveTemplate(requested);
        if (!player.hasPermission("playersettings.use")) {
            return;
        }
        if (!MenuRuntime.templates().containsKey(id)) {
            player.sendMessage("DialogMenu：模板不存在 " + id);
            return;
        }
        MenuResources.open(
                player,
                () -> {
                    MenuDialog.forget(player.getUniqueId());
                    player.closeInventory();
                    show(player, id, Collections.emptyMap());
                });
    }

    /** Optional providers may still be loading, so missing images and PAPI only warn. */
    public static List<String> warnings(Collection<DialogTemplate> templates) {
        List<String> result = new ArrayList<>();
        if (!MenuPlaceholders.enabled()
                && templates.stream().anyMatch(template -> !template.placeholders().isEmpty())) {
            result.add("未启用 PlaceholderAPI：Placeholders 条件均不成立，对应文字显示为不可用");
        }
        for (DialogTemplate template : templates) {
            for (TemplateElement element : template.elements()) {
                Set<MenuImageRequest> requests = new LinkedHashSet<>();
                if (element.image() != null) {
                    requests.add(element.image());
                }
                for (SpriteCase choice : element.cases()) {
                    if (choice.image() != null) {
                        requests.add(choice.image());
                    }
                }
                for (MenuImageRequest request : requests) {
                    String at = template.id() + " 元素 " + element.id();
                    MenuImage image = MenuImages.resolve(request);
                    if (image == null) {
                        result.add(
                                at
                                        + "：图片 "
                                        + request.provider()
                                        + ":"
                                        + request.id()
                                        + " 不可用"
                                        + "（来源插件未启用、尚未加载或 ID 不存在），将显示 [image:"
                                        + request.id()
                                        + "]");
                    } else if (image.advance() > element.width() + 1) {
                        result.add(
                                at
                                        + "：图片 "
                                        + request.id()
                                        + " 宽 "
                                        + (image.advance() - 1)
                                        + " 像素，超出占位宽度 "
                                        + element.width());
                    }
                }
            }
        }
        return result;
    }

    public static void forget(UUID uuid) {
        SESSIONS.remove(uuid);
    }

    public static void shutdown() {
        for (UUID uuid : new ArrayList<>(SESSIONS.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeDialog();
            }
        }
        SESSIONS.clear();
    }

    public static void reloaded() {
        Map<UUID, Session> previous = new LinkedHashMap<>(SESSIONS);
        SESSIONS.clear();
        for (Map.Entry<UUID, Session> entry : previous.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Session session = entry.getValue();
            if (MenuRuntime.templates().containsKey(session.template())
                    && player.hasPermission("playersettings.use")) {
                show(player, session.template(), session.values());
            } else {
                player.closeDialog();
            }
        }
    }

    /** Menu variables plus this moment's declared PlaceholderAPI values; names never overlap. */
    private static Map<String, String> context(
            Player player, DialogTemplate template, Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>(values);
        result.putAll(MenuPlaceholders.values(player, template.placeholders()));
        return result;
    }

    private static Function<String, String> display(
            Player player, DialogTemplate template, Map<String, String> context) {
        // Kotlin `by lazy`: the fallback text is read at most once, only when needed.
        String[] unavailable = new String[1];
        java.util.function.Supplier<String> fallback =
                () -> {
                    if (unavailable[0] == null) {
                        unavailable[0] = MenuPlaceholders.unavailable(player);
                    }
                    return unavailable[0];
                };
        return source ->
                TemplateRenderer.display(
                        source,
                        key -> {
                            if (key.equals("player")) {
                                return player.getName();
                            }
                            if (key.equals("uuid")) {
                                return player.getUniqueId().toString();
                            }
                            if (template.variables().containsKey(key)) {
                                return context.get(key);
                            }
                            if (template.placeholders().containsKey(key)) {
                                String value = context.get(key);
                                return value != null ? value : fallback.get();
                            }
                            if (key.equals("ping")) {
                                return String.valueOf(player.getPing());
                            }
                            if (key.equals("world")) {
                                return player.getWorld().getName();
                            }
                            return null;
                        },
                        token -> {
                            String value = MenuPlaceholders.resolve(player, token);
                            return value != null ? value : fallback.get();
                        });
    }

    private static void show(Player player, String id, Map<String, String> previous) {
        DialogTemplate template = Kt.getValue(MenuRuntime.templates(), id);
        Map<String, String> values = template.values(previous);
        Map<String, String> context = context(player, template, values);
        String token = UUID.randomUUID().toString().replace("-", "");
        Set<String> actions = new LinkedHashSet<>();
        DialogCanvas canvas =
                TemplateRenderer.render(
                        template,
                        context,
                        display(player, template, context),
                        MenuImages.text(player),
                        action -> {
                            actions.add(action);
                            return DialogClicks.custom(
                                    Key.key("dialogmenu_dialogue", token + "/" + action));
                        });
        Component content = canvas.build();
        SESSIONS.put(
                player.getUniqueId(),
                new Session(token, id, values, actions, System.currentTimeMillis()));
        Component title = MenuImages.text(player).component(template.title());
        int width = template.width() + (template.hideFocus() ? 24 : 10);
        player.showDialog(
                Dialog.create(
                        factory ->
                                factory.empty()
                                        .base(
                                                DialogBase.builder(Component.empty())
                                                        .externalTitle(title)
                                                        .canCloseWithEscape(true)
                                                        .pause(false)
                                                        .afterAction(
                                                                DialogBase.DialogAfterAction.NONE)
                                                        .body(
                                                                Kt.listOf(
                                                                        DialogBody.plainMessage(
                                                                                content, width)))
                                                        .build())
                                        .type(
                                                // An empty dialog list has no native buttons or
                                                // exit action. The canvas supplies its own close
                                                // button; ESC remains available.
                                                DialogType.dialogList(
                                                                RegistrySet.keySet(
                                                                        RegistryKey.DIALOG))
                                                        .build())));
    }

    static void quit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    static void clicked(PlayerCustomClickEvent event) {
        if (!event.getIdentifier().namespace().equals("dialogmenu_dialogue")) {
            return;
        }
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        if (player == null) {
            return;
        }
        String route = event.getIdentifier().value();
        Tasks.run(() -> handleClick(player, route));
    }

    private static void handleClick(Player player, String route) {
        if (!player.isOnline() || !player.hasPermission("playersettings.use")) {
            return;
        }
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        String id = Kt.substringAfter(route, '/');
        if (!route.startsWith(session.token() + "/")
                || !session.actions().contains(id)
                || System.currentTimeMillis() - session.opened() > 600_000) {
            return;
        }
        DialogTemplate template = MenuRuntime.templates().get(session.template());
        if (template == null) {
            return;
        }
        TemplateElement element = null;
        for (TemplateElement candidate : template.elements()) {
            if (candidate.id().equals(id)) {
                element = candidate;
                break;
            }
        }
        if (element == null) {
            return;
        }
        // Placeholder conditions may have changed since the menu was drawn.
        if (!TemplateRenderer.visible(element, context(player, template, session.values()))) {
            SESSIONS.remove(player.getUniqueId());
            show(player, template.id(), session.values());
            return;
        }
        SESSIONS.remove(player.getUniqueId());
        if (!element.permission().isEmpty() && !player.hasPermission(element.permission())) {
            player.sendMessage("你没有权限执行此操作。");
            show(player, template.id(), session.values());
            return;
        }
        ReactionRunner.run(
                element.reaction(),
                new CanvasHost(player, template, new LinkedHashMap<>(session.values())));
    }

    /**
     * A canvas menu's side of a reaction. {@code values} are the page's enum variables; {@code
     * set:} changes them for the rest of the reaction and for the next drawing.
     */
    private static final class CanvasHost implements ReactionRunner.Host {
        private final Player player;
        private final DialogTemplate template;
        private final Map<String, String> values;

        CanvasHost(Player player, DialogTemplate template, Map<String, String> values) {
            this.player = player;
            this.template = template;
            this.values = values;
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public String text(String source) {
            return display(player, template, names()).apply(source);
        }

        @Override
        public String commandValue(String name) {
            return switch (name) {
                case "player" -> player.getName();
                case "uuid" -> player.getUniqueId().toString();
                default -> names().get(name);
            };
        }

        @Override
        public Map<String, String> names() {
            return context(player, template, values);
        }

        @Override
        public Component rich(String line) {
            return MenuImages.text(player).component(line);
        }

        @Override
        public void set(String variable, String value) {
            values.put(variable, value);
        }

        @Override
        public void page(String target) {
            show(player, target, values);
        }

        @Override
        public void open(String target) {
            MenuRuntime.open(
                    player, Kt.substringBefore(target, '/'), Kt.substringAfter(target, '/'));
        }

        @Override
        public void close() {
            player.closeDialog();
        }

        @Override
        public void refresh() {
            show(player, template.id(), values);
        }

        @Override
        public void search() {}

        @Override
        public void template(String target) {
            show(player, target, values);
        }

        @Override
        public void settle() {
            show(player, template.id(), values);
        }

        @Override
        public void failed() {
            player.sendMessage("DialogMenu：指令执行失败，后续动作已停止。");
        }
    }
}
