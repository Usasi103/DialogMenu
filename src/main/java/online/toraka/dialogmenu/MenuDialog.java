package online.toraka.dialogmenu;

import dev.keystone.task.Tasks;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.set.RegistrySet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class MenuDialog {

    private static final Pattern EXPANDABLE =
            Pattern.compile("%[a-zA-Z0-9_:.\\-]+%|\\{(?:player|uuid|ping|world)}");

    public record View(String page, int dropdown, String menu, SettingsDemoSession demo) {

        public View(String page) {
            this(page, -1, "settings", null);
        }

        public View(String page, int dropdown) {
            this(page, dropdown, "settings", null);
        }

        public View(String page, int dropdown, String menu) {
            this(page, dropdown, menu, null);
        }

        public View toggleDropdown(int index) {
            return new View(page, dropdown == index ? -1 : index, menu, demo);
        }

        public View collapsed() {
            return new View(page, -1, menu, demo);
        }

        /** {@code copy(page = page)}. */
        public View withPage(String value) {
            return new View(value, dropdown, menu, demo);
        }

        /** {@code copy(page = page, dropdown = dropdown)}. */
        public View withPage(String value, int dropdownIndex) {
            return new View(value, dropdownIndex, menu, demo);
        }

        /** {@code copy(demo = demo)}. */
        public View withDemo(SettingsDemoSession value) {
            return new View(page, dropdown, menu, value);
        }
    }

    private record Session(
            String token,
            View view,
            Set<String> actions,
            long opened,
            Map<String, ItemDisplay> itemGuards) {

        Session(String token, View view, Set<String> actions, long opened) {
            this(token, view, actions, opened, Collections.emptyMap());
        }
    }

    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();

    private MenuDialog() {}

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
        Map<UUID, View> views = new LinkedHashMap<>();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            views.put(entry.getKey(), entry.getValue().view());
        }
        SESSIONS.clear();
        for (Map.Entry<UUID, View> entry : views.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            View view = entry.getValue();
            if (player.hasPermission("playersettings.use")
                    && MenuRuntime.settings(view.menu()) != null) {
                show(player, view.collapsed().withDemo(newDemo(player, view.menu())));
            } else {
                player.closeDialog();
            }
        }
    }

    public static void open(Player player) {
        open(player, MenuRuntime.current().defaultPage(), "settings");
    }

    public static void open(Player player, String page) {
        open(player, page, "settings");
    }

    public static void open(Player player, String page, String menuId) {
        if (!player.hasPermission("playersettings.use")) {
            return;
        }
        MenuDefinition menu = MenuRuntime.settings(menuId);
        if (menu == null || !menu.pages().containsKey(page)) {
            player.sendMessage("DialogMenu: 未启用的页面 " + page);
            return;
        }
        MenuResources.open(
                player,
                () -> {
                    player.closeInventory();
                    TemplateDialog.forget(player.getUniqueId());
                    show(player, new View(page, -1, menuId, newDemo(player, menuId)));
                });
    }

    static void quit(PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }

    static void clicked(PlayerCustomClickEvent event) {
        if (!event.getIdentifier().namespace().equals("dialogmenu_settings")) {
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
        DialogResponseView response = event.getDialogResponseView();
        String typed = response != null ? response.getText("query") : null;
        String query = typed != null ? Kt.take(typed, 48) : null;
        Tasks.run(() -> handleClick(player, route, query));
    }

    private static void handleClick(Player player, String route, String query) {
        if (!player.isOnline() || !player.hasPermission("playersettings.use")) {
            return;
        }
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (!route.startsWith(session.token() + "/")
                || System.currentTimeMillis() - session.opened() > 600_000) {
            return;
        }
        String action = Kt.substringAfter(route, '/');
        if (!session.actions().contains(action)) {
            return;
        }
        SESSIONS.remove(player.getUniqueId());
        ItemDisplay guard = session.itemGuards().get(action);
        if (guard != null && !ItemSources.registry().resolve(guard, player).available()) {
            player.sendMessage(message(player, "setting.failed"));
            show(player, session.view().collapsed());
            return;
        }
        if (action.equals("search_submit")) {
            MenuDefinition menu = MenuRuntime.settings(session.view().menu());
            String found = menu != null ? menu.search(query != null ? query : "") : null;
            if (found == null) {
                player.sendMessage(message(player, "search.empty"));
            }
            show(
                    player,
                    session.view().withPage(found != null ? found : session.view().page(), -1));
        } else if (action.equals("search_back")) {
            show(player, session.view());
        } else if (action.startsWith("page/")) {
            show(player, session.view().withPage(Kt.substringAfter(action, '/'), -1));
        } else if (action.startsWith("dropdown/")) {
            Integer index = Kt.toIntOrNull(Kt.substringAfter(action, '/'));
            if (index == null) {
                return;
            }
            show(player, session.view().toggleDropdown(index));
        } else if (action.startsWith("action/")) {
            execute(player, session.view().collapsed(), Kt.substringAfter(action, '/'));
        }
    }

    private static void execute(Player player, View view, String id) {
        MenuDefinition menu = MenuRuntime.settings(view.menu());
        MenuAction definition = menu != null ? menu.actions().get(id) : null;
        if (definition == null) {
            return;
        }
        if (view.demo() != null
                && (definition.permission().isEmpty()
                        || player.hasPermission(definition.permission()))
                && view.demo().apply(id)) {
            show(player, view);
            return;
        }
        executeDefinition(player, view, definition);
    }

    private static void executeDefinition(Player player, View view, MenuAction definition) {
        if (!definition.permission().isEmpty() && !player.hasPermission(definition.permission())) {
            player.sendMessage(message(player, "setting.denied"));
            show(player, view);
            return;
        }
        if (!definition.plugin().isEmpty() && EffectPlugins.provider(definition.plugin()) == null) {
            player.sendMessage(message(player, "setting.failed"));
            show(player, view);
            return;
        }
        // An Actions list compiles to a placeholder built-in that carries the reaction; run the
        // reaction before the built-in branch, which would otherwise just refresh.
        if (definition.reaction() != null) {
            ReactionRunner.run(definition.reaction(), new SettingsHost(player, view));
            return;
        }
        if (definition.type().equals("page")) {
            show(player, view.withPage(definition.value(), -1));
            return;
        }
        if (definition.type().equals("builtin")) {
            switch (definition.value()) {
                case "close" -> player.closeDialog();
                case "search" -> searchDialog(player, view);
                case "refresh" -> show(player, view);
                default -> {
                    MenuDefinition menu = MenuRuntime.settings(view.menu());
                    if (menu == null) {
                        return;
                    }
                    MenuPreferences prefs =
                            MenuPreferences.read(player.getPersistentDataContainer(), menu);
                    String value = Kt.substringAfter(definition.value(), ':');
                    MenuPreferences next =
                            definition.value().startsWith("language:")
                                    ? prefs.withLanguage(MenuLanguage.parse(value))
                                    : prefs.withTheme(MenuTheme.parse(value));
                    next.save(player.getPersistentDataContainer());
                    show(player, view);
                }
            }
            return;
        }
        if (definition.close()) {
            player.closeDialog();
        }
        List<MenuAction> steps =
                definition.steps().isEmpty() ? Kt.listOf(definition) : definition.steps();
        for (MenuAction step : steps) {
            if (step.type().equals("builtin") || step.type().equals("page")) {
                executeDefinition(player, view, step);
                return;
            }
            String command;
            if (step.type().equals("toggle-command")) {
                MenuDefinition menu = MenuRuntime.settings(view.menu());
                if (menu == null) {
                    return;
                }
                Boolean on = booleanState(state(player, step.state(), menu));
                if (on == null) {
                    player.sendMessage(message(player, "setting.failed"));
                    if (!definition.close()) {
                        show(player, view);
                    }
                    return;
                }
                command = on ? step.whenTrue() : step.whenFalse();
            } else {
                command = step.command();
            }
            String expanded =
                    CommandTemplate.render(
                            command, player.getName(), player.getUniqueId().toString());
            boolean succeeded =
                    step.type().equals("console-command")
                            ? Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expanded)
                            : player.performCommand(expanded);
            if (!succeeded) {
                player.sendMessage(message(player, "setting.failed"));
                break;
            }
        }
        if (!definition.close()) {
            refreshLater(player, view);
        }
    }

    /** Redraw on the next tick unless another menu opened in between. */
    private static void refreshLater(Player player, View view) {
        String token = UUID.randomUUID().toString();
        SESSIONS.put(
                player.getUniqueId(),
                new Session(token, view, Collections.emptySet(), System.currentTimeMillis()));
        Tasks.later(
                1L,
                () -> {
                    Session pending = SESSIONS.get(player.getUniqueId());
                    if (player.isOnline() && pending != null && pending.token().equals(token)) {
                        show(player, view);
                    }
                });
    }

    /** A settings menu's side of a reaction: commands use {player} and {uuid} only. */
    private record SettingsHost(Player player, View view) implements ReactionRunner.Host {

        @Override
        public String text(String source) {
            return expandText(player, source);
        }

        @Override
        public String commandValue(String name) {
            return switch (name) {
                case "player" -> player.getName();
                case "uuid" -> player.getUniqueId().toString();
                default -> null;
            };
        }

        @Override
        public Map<String, String> names() {
            return Collections.emptyMap();
        }

        @Override
        public Component rich(String line) {
            return MenuImages.text(player).component(line);
        }

        @Override
        public void set(String variable, String value) {}

        @Override
        public void page(String target) {
            show(player, view.withPage(target, -1));
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
            show(player, view);
        }

        @Override
        public void search() {
            searchDialog(player, view);
        }

        /** {@code open:} into a canvas menu is stored as {@code template: menu/page}. */
        @Override
        public void template(String target) {
            MenuRuntime.open(
                    player, Kt.substringBefore(target, '/'), Kt.substringAfter(target, '/'));
        }

        @Override
        public void settle() {
            refreshLater(player, view);
        }

        @Override
        public void failed() {
            player.sendMessage(message(player, "setting.failed"));
        }
    }

    private static void show(Player player, View requested) {
        MenuDefinition menu = MenuRuntime.settings(requested.menu());
        if (menu == null) {
            player.closeDialog();
            return;
        }
        View view =
                menu.pages().containsKey(requested.page())
                        ? requested
                        : requested.withPage(menu.defaultPage(), -1);
        MenuPreferences prefs =
                view.demo() != null
                        ? view.demo().preferences()
                        : MenuPreferences.read(player.getPersistentDataContainer(), menu);
        String token = UUID.randomUUID().toString().replace("-", "");
        Set<String> actions = new LinkedHashSet<>();
        RichMenuText richText = MenuImages.text(player);
        Function<String, ClickEvent> click =
                action -> {
                    actions.add(action);
                    return DialogClicks.custom(
                            Key.key("dialogmenu_settings", token + "/" + action));
                };
        ActionButton close =
                menu.showFooter()
                        ? ActionButton.create(
                                richText.component(
                                        expandText(
                                                player,
                                                menu.text(prefs.language(), menu.footerLabel()))),
                                null,
                                210,
                                DialogAction.staticAction(
                                        click.apply("action/" + menu.footerAction())))
                        : null;
        MenuPage page = Kt.getValue(menu.pages(), view.page());
        if (page.itemLayout()) {
            ItemMenuRender rendered =
                    ItemMenuRenderer.render(
                            menu,
                            page,
                            prefs.language(),
                            text -> expandText(player, text),
                            display -> ItemSources.registry().resolve(display, player),
                            click,
                            richText);
            SESSIONS.put(
                    player.getUniqueId(),
                    new Session(
                            token,
                            view,
                            new LinkedHashSet<>(actions),
                            System.currentTimeMillis(),
                            rendered.guards()));
            DialogType type =
                    rendered.buttons().isEmpty()
                            ? bodyOnlyType(close)
                            : DialogType.multiAction(rendered.buttons(), close, 2);
            Component title =
                    richText.component(
                            expandText(player, menu.text(prefs.language(), page.label())));
            player.showDialog(
                    Dialog.create(
                            factory ->
                                    factory.empty()
                                            .base(
                                                    DialogBase.builder(title)
                                                            .canCloseWithEscape(true)
                                                            .pause(false)
                                                            .afterAction(
                                                                    DialogBase.DialogAfterAction
                                                                            .NONE)
                                                            .body(rendered.bodies())
                                                            .build())
                                            .type(type)));
            return;
        }
        Map<String, String> cache = new HashMap<>();
        DialogCanvas canvas =
                MenuRenderer.render(
                        menu,
                        view.page(),
                        prefs.language(),
                        prefs.theme(),
                        id -> {
                            if (cache.containsKey(id)) {
                                return cache.get(id);
                            }
                            String value =
                                    view.demo() != null
                                            ? view.demo().state(id)
                                            : state(player, id, menu);
                            cache.put(id, value);
                            return value;
                        },
                        text -> expandText(player, text),
                        click,
                        view.dropdown(),
                        richText);
        Component component = canvas.build();
        SESSIONS.put(
                player.getUniqueId(),
                new Session(token, view, new LinkedHashSet<>(actions), System.currentTimeMillis()));
        Component title =
                richText.component(expandText(player, menu.text(prefs.language(), menu.title())));
        DialogType type = bodyOnlyType(close);
        player.showDialog(
                Dialog.create(
                        factory ->
                                factory.empty()
                                        .base(
                                                DialogBase.builder(title)
                                                        .canCloseWithEscape(true)
                                                        .pause(false)
                                                        .afterAction(
                                                                DialogBase.DialogAfterAction.NONE)
                                                        .body(
                                                                Kt.listOf(
                                                                        DialogBody.plainMessage(
                                                                                component,
                                                                                menu.hideFocus()
                                                                                        ? DialogCanvas
                                                                                                .FRAMELESS_BODY_WIDTH
                                                                                        : DialogCanvas
                                                                                                .BODY_WIDTH)))
                                                        .build())
                                        .type(type)));
    }

    private static DialogType bodyOnlyType(ActionButton close) {
        if (close != null) {
            return DialogType.notice(close);
        }
        return DialogType.dialogList(RegistrySet.keySet(RegistryKey.DIALOG)).build();
    }

    private static void searchDialog(Player player, View view) {
        MenuLanguage language =
                view.demo() != null
                        ? view.demo().preferences().language()
                        : MenuPreferences.read(player.getPersistentDataContainer()).language();
        String token = UUID.randomUUID().toString().replace("-", "");
        SESSIONS.put(
                player.getUniqueId(),
                new Session(
                        token,
                        view,
                        Kt.setOf("search_submit", "search_back"),
                        System.currentTimeMillis()));
        ActionButton submit =
                searchButton(MenuText.get(language, "search.submit"), token, "search_submit");
        ActionButton back = searchButton(MenuText.get(language, "back"), token, "search_back");
        Component title = Component.text(MenuText.get(language, "search.title"));
        Component keyword = Component.text(MenuText.get(language, "search.keyword"));
        player.showDialog(
                Dialog.create(
                        factory ->
                                factory.empty()
                                        .base(
                                                DialogBase.builder(title)
                                                        .pause(false)
                                                        .afterAction(
                                                                DialogBase.DialogAfterAction.NONE)
                                                        .inputs(
                                                                Kt.listOf(
                                                                        DialogInput.text(
                                                                                        "query",
                                                                                        keyword)
                                                                                .maxLength(48)
                                                                                .width(280)
                                                                                .build()))
                                                        .build())
                                        .type(DialogType.confirmation(submit, back))));
    }

    private static ActionButton searchButton(String label, String token, String action) {
        return ActionButton.create(
                Component.text(label),
                null,
                140,
                DialogAction.customClick(
                        Key.key("dialogmenu_settings", token + "/" + action), null));
    }

    private static SettingsDemoSession newDemo(Player player, String menuId) {
        MenuDefinition menu = MenuRuntime.settings(menuId);
        if (menu == null) {
            return null;
        }
        return menu.demo()
                ? new SettingsDemoSession(
                        menu, MenuPreferences.read(player.getPersistentDataContainer(), menu))
                : null;
    }

    private static String state(Player player, String id, MenuDefinition menu) {
        String binding = Kt.getValue(menu.states(), id);
        switch (binding) {
            case "language":
                return MenuPreferences.read(player.getPersistentDataContainer(), menu)
                        .language()
                        .id();
            case "theme":
                return MenuPreferences.read(player.getPersistentDataContainer(), menu).theme().id();
            case "pickup":
                if (EffectPlugins.provider("PickupNotifier") != null) {
                    return String.valueOf(
                            !player.getPersistentDataContainer()
                                    .has(
                                            new NamespacedKey("pickupnotifier", "disabled"),
                                            PersistentDataType.BYTE));
                }
                return null;
            case "loot-beams":
                {
                    Boolean value = lootState(player, "getBeams");
                    return value != null ? value.toString() : null;
                }
            case "loot-sounds":
                {
                    Boolean value = lootState(player, "getSounds");
                    return value != null ? value.toString() : null;
                }
            default:
                return papi(player, binding);
        }
    }

    private static String papi(Player player, String token) {
        return MenuPlaceholders.resolve(player, token);
    }

    private static String expandText(Player player, String template) {
        String expanded =
                Kt.replace(
                        EXPANDABLE,
                        template,
                        match -> {
                            switch (match.group()) {
                                case "{player}":
                                    return player.getName();
                                case "{uuid}":
                                    return player.getUniqueId().toString();
                                case "{ping}":
                                    return String.valueOf(player.getPing());
                                case "{world}":
                                    return player.getWorld().getName();
                                default:
                                    String value = papi(player, match.group());
                                    return value != null ? value : message(player, "unavailable");
                            }
                        });
        StringBuilder result = new StringBuilder(expanded.length());
        for (int i = 0; i < expanded.length(); i++) {
            char character = expanded.charAt(i);
            result.append(Character.isISOControl(character) ? ' ' : character);
        }
        return result.toString();
    }

    /** Reads Ambience's LootBeam module preferences by reflection (class name frozen). */
    private static Boolean lootState(Player player, String getter) {
        try {
            Plugin plugin = EffectPlugins.provider("LootBeam");
            if (plugin == null) {
                return null;
            }
            if (!plugin.isEnabled()) {
                return null;
            }
            Class<?> cache =
                    plugin.getClass()
                            .getClassLoader()
                            .loadClass("online.toraka.lootbeam.data.PrefsCache");
            Object prefs =
                    cache.getMethod("peek", UUID.class)
                            .invoke(cache.getField("INSTANCE").get(null), player.getUniqueId());
            if (prefs == null) {
                return null;
            }
            return (Boolean) prefs.getClass().getMethod(getter).invoke(prefs);
        } catch (Throwable error) {
            return null;
        }
    }

    private static String message(Player player, String key) {
        return MenuText.get(
                MenuPreferences.read(player.getPersistentDataContainer()).language(), key);
    }

    public static String findTab(String query) {
        return MenuRuntime.current().search(query);
    }

    public static Boolean booleanState(String value) {
        if (value == null) {
            return null;
        }
        return switch (Kt.lower(value)) {
            case "开", "开启", "on", "enabled", "true", "1" -> true;
            case "关", "关闭", "off", "disabled", "false", "0" -> false;
            default -> null;
        };
    }

    public static String toggleLabel(String value) {
        return toggleLabel(value, MenuLanguage.CHINESE);
    }

    public static String toggleLabel(String value, MenuLanguage language) {
        Boolean state = booleanState(value);
        String key;
        if (state == null) {
            key = "unavailable";
        } else if (state) {
            key = "on";
        } else {
            key = "off";
        }
        return MenuText.get(language, key);
    }

    public static String densityLabel(String value) {
        return densityLabel(value, MenuLanguage.CHINESE);
    }

    public static String densityLabel(String value, MenuLanguage language) {
        String key =
                switch (Kt.lower(value)) {
                    case "off", "关闭" -> "off";
                    case "low", "低" -> "density.low";
                    case "medium", "中" -> "density.medium";
                    case "high", "高" -> "density.high";
                    default -> "unavailable";
                };
        return MenuText.get(language, key);
    }
}
