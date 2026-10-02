package online.toraka.dialogmenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.event.ClickEvent;

public final class MenuRenderer {

    private MenuRenderer() {}

    public static DialogCanvas render(
            MenuDefinition menu,
            String pageId,
            MenuLanguage language,
            MenuTheme theme,
            Function<String, String> state,
            Function<String, String> expand,
            Function<String, ClickEvent> click) {
        return render(menu, pageId, language, theme, state, expand, click, -1, new RichMenuText());
    }

    public static DialogCanvas render(
            MenuDefinition menu,
            String pageId,
            MenuLanguage language,
            MenuTheme theme,
            Function<String, String> state,
            Function<String, String> expand,
            Function<String, ClickEvent> click,
            int dropdown) {
        return render(
                menu, pageId, language, theme, state, expand, click, dropdown, new RichMenuText());
    }

    public static DialogCanvas render(
            MenuDefinition menu,
            String pageId,
            MenuLanguage language,
            MenuTheme theme,
            Function<String, String> state,
            Function<String, String> expand,
            Function<String, ClickEvent> click,
            int dropdown,
            RichMenuText richText) {
        DialogCanvas canvas = new DialogCanvas(theme, richText, click);
        Renderer renderer = new Renderer(menu, language, theme, state, expand, dropdown, canvas);
        for (MenuWidget widget : menu.common()) {
            renderer.draw(widget);
        }
        int index = 0;
        for (MenuPage page : menu.pages().values()) {
            int row = menu.navRow() + index * menu.navStep();
            canvas.button(
                    menu.navX(),
                    row,
                    page.id().equals(pageId) ? DialogCanvas.SELECTED_NAV : DialogCanvas.NAV,
                    renderer.text(page.label()),
                    "page/" + page.id(),
                    6,
                    menu.navTextSize(),
                    menu.navBold());
            if (!page.icon().isEmpty()) {
                canvas.sprite(
                        menu.navX() + 4,
                        row,
                        Kt.getValue(MenuConfigParser.skins(), page.icon()),
                        "page/" + page.id());
            }
            index++;
        }
        for (MenuWidget widget : Kt.getValue(menu.pages(), pageId).widgets()) {
            renderer.draw(widget);
        }
        MenuWidget widget = renderer.expanded;
        if (widget != null) {
            canvas.coverLabels(widget.x(), widget.row() + 2, 114, widget.options().size() * 2);
            String current = state.apply(widget.state());
            for (int option = 0; option < widget.options().size(); option++) {
                MenuOption choice = widget.options().get(option);
                canvas.button(
                        widget.x(),
                        widget.row() + 2 + option * 2,
                        Kt.equalsIgnoreCase(choice.value(), current)
                                ? DialogCanvas.SELECTED_CONTROL
                                : DialogCanvas.CONTROL,
                        renderer.text(choice.label()),
                        "action/" + choice.action(),
                        6,
                        widget.textSize(),
                        widget.bold());
            }
        }
        return canvas;
    }

    /** The Kotlin local functions and the widget counter they shared. */
    private static final class Renderer {
        private final MenuDefinition menu;
        private final MenuLanguage language;
        private final MenuTheme theme;
        private final Function<String, String> state;
        private final Function<String, String> expand;
        private final int dropdown;
        private final DialogCanvas canvas;
        private int widgetIndex = 0;
        private MenuWidget expanded = null;

        Renderer(
                MenuDefinition menu,
                MenuLanguage language,
                MenuTheme theme,
                Function<String, String> state,
                Function<String, String> expand,
                int dropdown,
                DialogCanvas canvas) {
            this.menu = menu;
            this.language = language;
            this.theme = theme;
            this.state = state;
            this.expand = expand;
            this.dropdown = dropdown;
            this.canvas = canvas;
        }

        String text(String value) {
            String expanded = expand.apply(menu.text(language, value));
            StringBuilder result = new StringBuilder(expanded.length());
            for (int i = 0; i < expanded.length(); i++) {
                char character = expanded.charAt(i);
                result.append(Character.isISOControl(character) ? ' ' : character);
            }
            return result.toString();
        }

        private static String action(String id) {
            return id.isEmpty() ? null : "action/" + id;
        }

        private int color(String value) {
            return switch (value) {
                case "heading" -> theme.heading();
                case "text" -> theme.text();
                case "muted" -> theme.muted();
                default -> Integer.parseInt(Kt.drop(value, 1), 16);
            };
        }

        void draw(MenuWidget widget) {
            int index = widgetIndex++;
            String current = widget.state().isEmpty() ? null : state.apply(widget.state());
            if (!widget.label().isEmpty()) {
                int room =
                        widget.x()
                                - widget.labelX()
                                - (widget.kind() == WidgetKind.SLIDER ? 60 : 6);
                boolean raised =
                        widget.textSize() != 8
                                || widget.kind() == WidgetKind.SLIDER
                                || (widget.kind() == WidgetKind.TOGGLE
                                        && widget.toggleStyle() == ToggleStyle.SWITCH);
                canvas.text(
                        widget.labelX(),
                        widget.labelRow(),
                        canvas.prepare(
                                        text(widget.label()),
                                        widget.textSize(),
                                        widget.bold(),
                                        raised)
                                .fit(room),
                        theme.text(),
                        null,
                        raised,
                        widget.textSize(),
                        widget.bold());
            }
            switch (widget.kind()) {
                case TEXT, HEADING ->
                        canvas.text(
                                widget.x(),
                                widget.row(),
                                canvas.prepare(
                                                text(widget.text()),
                                                widget.textSize(),
                                                widget.bold())
                                        .fit(widget.width()),
                                color(widget.color()),
                                action(widget.action()),
                                false,
                                widget.textSize(),
                                widget.bold());
                case SPRITE ->
                        canvas.sprite(
                                widget.x(),
                                widget.row(),
                                Kt.getValue(MenuConfigParser.skins(), widget.skin()),
                                action(widget.action()));
                case BUTTON -> {
                    DialogCanvas.Skin base = Kt.getValue(MenuConfigParser.skins(), widget.skin());
                    boolean selected =
                            !widget.selected().isEmpty() && widget.selected().equals(current);
                    DialogCanvas.Skin skin;
                    if (!selected) {
                        skin = base;
                    } else if (base.equals(DialogCanvas.NAV) || base.equals(DialogCanvas.SEARCH)) {
                        skin = DialogCanvas.SELECTED_NAV;
                    } else {
                        skin = DialogCanvas.SELECTED_CONTROL;
                    }
                    canvas.button(
                            widget.x(),
                            widget.row(),
                            skin,
                            text(widget.text()),
                            "action/" + widget.action(),
                            6,
                            widget.textSize(),
                            widget.bold());
                }
                case TOGGLE -> {
                    Boolean on = MenuDialog.booleanState(current);
                    String key;
                    if (on == null) {
                        key = "unavailable";
                    } else if (on) {
                        key = "on";
                    } else {
                        key = "off";
                    }
                    String label = menu.text(language, "$" + key);
                    if (widget.toggleStyle() == ToggleStyle.SWITCH) {
                        canvas.toggleSwitch(
                                widget.x(),
                                widget.row(),
                                on,
                                label,
                                "action/" + widget.action(),
                                widget.textSize(),
                                widget.bold());
                    } else {
                        canvas.button(
                                widget.x(),
                                widget.row(),
                                Boolean.TRUE.equals(on)
                                        ? DialogCanvas.SELECTED_CONTROL
                                        : DialogCanvas.CONTROL,
                                label,
                                "action/" + widget.action(),
                                6,
                                widget.textSize(),
                                widget.bold());
                    }
                }
                case SLIDER -> {
                    int selected = -1;
                    for (int i = 0; i < widget.options().size(); i++) {
                        if (Kt.equalsIgnoreCase(widget.options().get(i).value(), current)) {
                            selected = i;
                            break;
                        }
                    }
                    MenuOption option = Kt.getOrNull(widget.options(), selected);
                    String label =
                            option != null
                                    ? text(option.label())
                                    : menu.text(language, "$" + "unavailable");
                    List<String> actions = new ArrayList<>();
                    for (MenuOption choice : widget.options()) {
                        actions.add("action/" + choice.action());
                    }
                    canvas.slider(
                            widget.x(),
                            widget.row(),
                            selected,
                            label,
                            actions,
                            widget.textSize(),
                            widget.bold());
                }
                case DROPDOWN -> {
                    boolean opened = dropdown == index;
                    String route = "dropdown/" + index;
                    MenuOption option = null;
                    for (MenuOption choice : widget.options()) {
                        if (Kt.equalsIgnoreCase(choice.value(), current)) {
                            option = choice;
                            break;
                        }
                    }
                    String label =
                            option != null
                                    ? text(option.label())
                                    : menu.text(language, "$" + "unavailable");
                    canvas.button(
                            widget.x(),
                            widget.row(),
                            DialogCanvas.CONTROL,
                            label,
                            route,
                            22,
                            widget.textSize(),
                            widget.bold());
                    canvas.sprite(
                            widget.x() + 96,
                            widget.row(),
                            opened ? DialogCanvas.DROPDOWN_UP : DialogCanvas.DROPDOWN_DOWN,
                            route);
                    if (opened) {
                        expanded = widget;
                    }
                }
            }
        }
    }
}
