package online.toraka.dialogmenu;

import java.util.List;

public record MenuWidget(
        WidgetKind kind,
        int x,
        int row,
        String text,
        String label,
        int labelX,
        int width,
        String color,
        String skin,
        String action,
        String state,
        String selected,
        List<MenuOption> options,
        ToggleStyle toggleStyle,
        int textSize,
        boolean bold,
        int labelRow) {

    /** Kotlin defaults: button toggles, 8 px text, not bold, label on the next row. */
    public MenuWidget(
            WidgetKind kind,
            int x,
            int row,
            String text,
            String label,
            int labelX,
            int width,
            String color,
            String skin,
            String action,
            String state,
            String selected,
            List<MenuOption> options) {
        this(
                kind,
                x,
                row,
                text,
                label,
                labelX,
                width,
                color,
                skin,
                action,
                state,
                selected,
                options,
                ToggleStyle.BUTTON,
                8,
                false,
                row + 1);
    }

    public MenuWidget withToggleStyle(ToggleStyle style) {
        return new MenuWidget(
                kind, x, row, text, label, labelX, width, color, skin, action, state, selected,
                options, style, textSize, bold, labelRow);
    }
}
