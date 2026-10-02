package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One open demo, owned by one player's View; never reads plugins or writes player data. */
public final class SettingsDemoSession {

    private final MenuDefinition menu;
    private final List<MenuWidget> controls;
    private final Map<String, String> values;

    public SettingsDemoSession(MenuDefinition menu, MenuPreferences initial) {
        this.menu = menu;
        List<MenuWidget> widgets = new ArrayList<>();
        for (MenuPage page : menu.pages().values()) {
            for (MenuWidget widget : page.widgets()) {
                if (!widget.state().isEmpty()) {
                    widgets.add(widget);
                }
            }
        }
        this.controls = widgets;
        Map<String, String> initialValues = new LinkedHashMap<>();
        for (MenuWidget widget : controls) {
            String binding = menu.states().get(widget.state());
            String value;
            if (Objects.equals(binding, "language")) {
                value = initial.language().id();
            } else if (Objects.equals(binding, "theme")) {
                value = initial.theme().id();
            } else if (widget.kind() == WidgetKind.TOGGLE) {
                value = "true";
            } else {
                value = Kt.last(widget.options()).value();
            }
            initialValues.put(widget.state(), value);
        }
        this.values = initialValues;
    }

    public MenuPreferences preferences() {
        String language = valueFor("language");
        String theme = valueFor("theme");
        return new MenuPreferences(
                MenuLanguage.parse(language != null ? language : menu.language().id()),
                MenuTheme.parse(theme != null ? theme : menu.theme().id()));
    }

    private String valueFor(String binding) {
        for (Map.Entry<String, String> entry : menu.states().entrySet()) {
            if (Objects.equals(entry.getValue(), binding)) {
                String key = entry.getKey();
                return key != null ? values.get(key) : null;
            }
        }
        return null;
    }

    public String state(String id) {
        return values.get(id);
    }

    public boolean apply(String action) {
        for (MenuWidget widget : controls) {
            if (widget.kind() == WidgetKind.TOGGLE && Objects.equals(widget.action(), action)) {
                values.put(
                        widget.state(),
                        String.valueOf(!Objects.equals(values.get(widget.state()), "true")));
                return true;
            }
            MenuOption option = null;
            for (MenuOption candidate : widget.options()) {
                if (Objects.equals(candidate.action(), action)) {
                    option = candidate;
                    break;
                }
            }
            if (option != null) {
                values.put(widget.state(), option.value());
                return true;
            }
        }
        return false;
    }

    public static void validate(MenuDefinition menu) {
        Kt.require(
                menu.pages().values().stream().noneMatch(MenuPage::itemLayout),
                () -> "settings-demo: 演示菜单使用字体控件，不使用外部物品源");
        List<MenuWidget> controls = new ArrayList<>();
        for (MenuPage page : menu.pages().values()) {
            controls.addAll(page.widgets());
        }
        Set<String> simulated = new LinkedHashSet<>();
        for (MenuWidget widget : controls) {
            if (widget.kind() == WidgetKind.TOGGLE) {
                simulated.add(widget.action());
            }
            for (MenuOption option : widget.options()) {
                simulated.add(option.action());
            }
        }
        List<MenuWidget> reachable = Kt.plus(menu.common(), controls);
        List<String> actions = new ArrayList<>();
        for (MenuWidget widget : reachable) {
            if (!widget.action().isEmpty()) {
                actions.add(widget.action());
            }
        }
        actions.add(menu.footerAction());
        Kt.require(
                actions.stream()
                        .allMatch(
                                it ->
                                        simulated.contains(it)
                                                || safe(Kt.getValue(menu.actions(), it))),
                () -> "settings-demo: 按钮仅支持关闭、刷新、搜索、页内跳转与提示类动作；绑定控件自动模拟，不执行外部指令");
    }

    /** The {@code safe} local function of {@code validate}. */
    private static boolean safe(MenuAction action) {
        if (action.reaction() != null) {
            // Display-only actions are harmless in a preview; nothing that runs or opens elsewhere.
            return action.reaction().steps().stream()
                    .allMatch(
                            step ->
                                    Kt.setOf(
                                                    "close",
                                                    "refresh",
                                                    "search",
                                                    "page",
                                                    "tell",
                                                    "title",
                                                    "actionbar",
                                                    "tellraw",
                                                    "sound",
                                                    "delay",
                                                    "return")
                                            .contains(step.name()));
        }
        if (!action.steps().isEmpty()) {
            return action.steps().stream().allMatch(SettingsDemoSession::safe);
        }
        if (Objects.equals(action.type(), "page")) {
            return true;
        }
        if (Objects.equals(action.type(), "builtin")) {
            return Kt.setOf("close", "refresh", "search").contains(action.value());
        }
        return false;
    }
}
