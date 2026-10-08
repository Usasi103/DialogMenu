package online.toraka.dialogmenu;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

/** Whole sprites share an origin with the nine-pixel click grid. */
public final class DialogCanvas {

    public static final int WIDTH = 450;
    public static final int ROWS = 29;
    // PlainMessageHandler builds FocusableTextWidget with 4px padding on
    // EACH side. Its maxWidth includes that padding; text receives less.
    public static final int BODY_PADDING = 4;
    public static final int WRAP_SLACK = 2;
    // Keep slack in the measured line itself: updateHeight() wraps again at
    // the measured content width, not at the requested widget width.
    public static final int LINE_WIDTH = WIDTH + WRAP_SLACK;
    public static final int BODY_WIDTH = LINE_WIDTH + BODY_PADDING * 2;
    // Opt-in geometry used by the scoped GUI shader. Ordinary dialogs retain
    // their native width. Keep in sync with the resource-pack selector.
    public static final int FRAMELESS_BODY_WIDTH = BODY_WIDTH + 14;
    public static final Key FONT = Key.key("dialogmenu_settings:ui");
    public static final Key SWITCH_FONT = Key.key("dialogmenu_settings:switches");
    public static final Key LABEL_FONT = Key.key("dialogmenu_settings:labels");
    public static final Key BUTTON_LABEL_FONT = Key.key("dialogmenu_settings:button_labels");

    private static final Properties METRICS = loadMetrics();

    public static final Skin PANEL_TOP = new Skin(0xE000, 336, 9);
    public static final Skin PANEL_BOTTOM = new Skin(0xE020, 336, 14);
    public static final Skin NAV = new Skin(0xE040, 102, 2);
    public static final Skin SELECTED_NAV = new Skin(0xE050, 102, 2);
    public static final Skin CONTROL = new Skin(0xE060, 114, 2);
    public static final Skin SELECTED_CONTROL = new Skin(0xE070, 114, 2);
    public static final Skin SEARCH = new Skin(0xE080, 102, 2);
    public static final Skin SEARCH_ICON = new Skin(0xE096, 16, 2);
    public static final Skin PANEL_ACTION = new Skin(0xE097, 16, 2);
    public static final Skin DROPDOWN_DOWN = new Skin(0xE098, 16, 2);
    public static final Skin DROPDOWN_UP = new Skin(0xE099, 16, 2);

    /** Non-empty {@code slices} replace the glyph columns, e.g. a button stretched to a width. */
    public record Skin(
            int glyph,
            int width,
            int rows,
            Key font,
            List<Integer> advances,
            int columns,
            List<Slice> slices) {

        public Skin(int glyph, int width, int rows) {
            this(glyph, width, rows, FONT, Collections.emptyList());
        }

        public Skin(int glyph, int width, int rows, Key font, List<Integer> advances) {
            this(glyph, width, rows, font, advances, width > 256 ? 2 : 1);
        }

        public Skin(int glyph, int width, int rows, Key font, List<Integer> advances, int columns) {
            this(glyph, width, rows, font, advances, columns, Collections.emptyList());
        }

        /** {@code copy(glyph = glyph)}. */
        public Skin withGlyph(int value) {
            return new Skin(value, width, rows, font, advances, columns, slices);
        }

        /** {@code copy(glyph = glyph, font = font, advances = advances)}. */
        public Skin withLook(int value, Key look, List<Integer> glyphAdvances) {
            return new Skin(value, width, rows, look, glyphAdvances, columns, slices);
        }

        /** {@code copy(width = width, slices = slices)}. */
        public Skin withSlices(int value, List<Slice> pieces) {
            return new Skin(glyph, value, rows, font, advances, columns, pieces);
        }
    }

    /** One glyph of a sprite: code point, painted cell width and measured bitmap advance. */
    public record Slice(int glyph, int width, int advance) {}

    private record Sprite(
            int x, int row, Skin skin, String action, AnimationPreset preset, double progress) {}

    /** Applies fixed-start, speed-scaled timing to a bitmap sprite. Time is in seconds. */
    public void animatedSprite(
            int x,
            int row,
            Skin skin,
            AnimationPreset preset,
            AnimationTiming timing,
            double elapsedSeconds,
            String action) {
        animatedSprite(x, row, skin, preset, timing.progress(elapsedSeconds), action);
    }

    private record Label(
            float x,
            int row,
            MeasuredText text,
            int color,
            String action,
            boolean raised,
            int textSize,
            boolean bold) {

        /** {@code copy(x = x, text = text)}. */
        Label moved(float at, MeasuredText replacement) {
            return new Label(at, row, replacement, color, action, raised, textSize, bold);
        }
    }

    public record Hit(int x, int row, int width, int rows, String action) {}

    private final MenuTheme theme;
    private final int width;
    private final int rows;
    private final Key labelFont;
    private final Key buttonLabelFont;
    private final RichMenuText richText;
    private final Function<String, ClickEvent> click;

    private final List<Sprite> sprites = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Component> tooltips = new HashMap<>();

    /** The click grid, glyph and label share the same tooltip. */
    public void tooltip(String action, Component text) {
        tooltips.put(action, text);
    }

    private Component interactive(Component component, String action) {
        Component result = component.clickEvent(click.apply(action));
        Component tooltip = tooltips.get(action);
        return tooltip == null ? result : result.hoverEvent(HoverEvent.showText(tooltip));
    }

    public DialogCanvas(
            MenuTheme theme,
            int width,
            int rows,
            Key labelFont,
            Key buttonLabelFont,
            RichMenuText richText,
            Function<String, ClickEvent> click) {
        this.theme = theme;
        this.width = width;
        this.rows = rows;
        this.labelFont = labelFont;
        this.buttonLabelFont = buttonLabelFont;
        this.richText = richText;
        this.click = click;
    }

    /** Kotlin defaults: settings geometry and fonts, plain text. */
    public DialogCanvas(MenuTheme theme, Function<String, ClickEvent> click) {
        this(theme, new RichMenuText(), click);
    }

    /** Kotlin defaults: settings geometry and fonts. */
    public DialogCanvas(
            MenuTheme theme, RichMenuText richText, Function<String, ClickEvent> click) {
        this(theme, WIDTH, ROWS, LABEL_FONT, BUTTON_LABEL_FONT, richText, click);
    }

    /** Kotlin defaults: dark settings canvas. */
    public DialogCanvas(Function<String, ClickEvent> click) {
        this(MenuTheme.DARK, click);
    }

    public List<Hit> hits() {
        return hits;
    }

    public void sprite(int x, int row, Skin skin) {
        sprite(x, row, skin, null);
    }

    public void sprite(int x, int row, Skin skin, String action) {
        sprites.add(new Sprite(x, row, theme.skin(skin), action, null, 1));
        if (action != null) {
            hits.add(new Hit(x, row, skin.width(), skin.rows(), action));
        }
    }

    /**
     * Paints one 36px square bitmap glyph using the shared GUI animation shader. The caller
     * supplies progress and owns playback/cancellation. Click cells stay fixed during motion.
     * The skin's font must also define E000/E001 space advances -3145728/+3145728.
     */
    public void animatedSprite(
            int x, int row, Skin skin, AnimationPreset preset, double progress, String action) {
        if (skin.width() != 36 || skin.columns() != 1 || !skin.slices().isEmpty())
            throw new IllegalArgumentException("Animated icons require one 36px square glyph");
        if (preset == null) throw new IllegalArgumentException("Animation preset is required");
        if (!preset.frame(progress).visible()) return;
        sprites.add(new Sprite(x, row, skin, action, preset, progress));
        if (action != null) hits.add(new Hit(x - 22, row, 80, 6, action));
    }

    public void text(int x, int row, String text) {
        text(x, row, text, null, null, false, 8, false);
    }

    /** A null {@code color} is the theme's text colour (the Kotlin default). */
    public void text(
            int x,
            int row,
            String text,
            Integer color,
            String action,
            boolean raised,
            int textSize,
            boolean bold) {
        text(x, row, prepare(text, textSize, bold, raised), color, action, raised, textSize, bold);
    }

    public MeasuredText prepare(String text) {
        return prepare(text, 8, false, false);
    }

    public MeasuredText prepare(String text, int textSize) {
        return prepare(text, textSize, false, false);
    }

    public MeasuredText prepare(String text, int textSize, boolean bold) {
        return prepare(text, textSize, bold, false);
    }

    public MeasuredText prepare(String text, int textSize, boolean bold, boolean raised) {
        return richText.measure(
                text,
                textSize != 8
                        ? TitleFont.font(textSize, raised)
                        : raised ? buttonLabelFont : labelFont,
                textSize,
                bold);
    }

    public void text(int x, int row, MeasuredText text) {
        text(x, row, text, null, null, false, 8, false);
    }

    public void text(int x, int row, MeasuredText text, Integer color) {
        text(x, row, text, color, null, false, 8, false);
    }

    /** A null {@code color} is the theme's text colour (the Kotlin default). */
    public void text(
            int x,
            int row,
            MeasuredText text,
            Integer color,
            String action,
            boolean raised,
            int textSize,
            boolean bold) {
        labels.add(
                new Label(
                        (float) x,
                        row,
                        text,
                        color != null ? color : theme.text(),
                        action,
                        raised,
                        textSize,
                        bold));
    }

    public void button(int x, int row, Skin skin, String label, String action) {
        button(x, row, skin, label, action, 6, 8, false);
    }

    public void button(
            int x,
            int row,
            Skin skin,
            String label,
            String action,
            int rightInset,
            int textSize,
            boolean bold) {
        // Keep the click event on the visual glyphs and label as well as on the
        // invisible hit grid.  Some clients resolve a Dialog body's hit style
        // from the painted glyph instead of the preceding spacing component.
        // Having both representations makes the whole visible button reliable.
        sprite(x, row, skin, action);
        int inset = skin.equals(NAV) || skin.equals(SELECTED_NAV) || skin.equals(SEARCH) ? 24 : 6;
        labels.add(
                new Label(
                        (float) (x + inset),
                        row + (textSize == 8 ? 1 : 0),
                        prepare(label, textSize, bold, true).fit(skin.width() - inset - rightInset),
                        0xF0F0F0,
                        action,
                        true,
                        textSize,
                        bold));
    }

    /** The status and both switch halves share the existing toggle action. */
    public void toggleSwitch(int x, int row, Boolean on, String valueLabel, String action) {
        toggleSwitch(x, row, on, valueLabel, action, 8, false);
    }

    public void toggleSwitch(
            int x,
            int row,
            Boolean on,
            String valueLabel,
            String action,
            int textSize,
            boolean bold) {
        int switchX = x + 78;
        MeasuredText label = prepare(valueLabel, textSize, bold, true).fit(72);
        int labelX = switchX - 6 - label.width();
        int state;
        if (on == null) {
            state = 2;
        } else if (on) {
            state = 0;
        } else {
            state = 1;
        }
        int glyph = 0xE700 + state + (theme == MenuTheme.LIGHT ? 3 : 0);
        sprite(switchX, row, new Skin(glyph, 36, 2, SWITCH_FONT, Kt.listOf(37)), action);
        hits.add(new Hit(labelX, row, switchX - labelX, 2, action));
        text(
                labelX,
                row + (textSize == 8 ? 1 : 0),
                label,
                theme.muted(),
                action,
                true,
                textSize,
                bold);
    }

    /** Clip covered text before painting a popup, including labels on the next text row. */
    public void coverLabels(int x, int row, int width, int rows) {
        List<Sprite> covered = new ArrayList<>();
        for (Sprite sprite : sprites) {
            if (sprite.action() != null
                    && sprite.x() < x + width
                    && x < sprite.x() + sprite.skin().width()
                    && sprite.row() < row + rows
                    && row < sprite.row() + sprite.skin().rows()) {
                covered.add(sprite);
            }
        }
        // A control starting on a later text row must not repaint over the popup.
        // Remove its visual and input regions together until the list collapses.
        sprites.removeAll(new HashSet<>(covered));
        hits.removeIf(
                hit ->
                        hit.x() < x + width
                                && x < hit.x() + hit.width()
                                && hit.row() < row + rows
                                && row < hit.row() + hit.rows());
        labels.removeIf(
                label -> {
                    for (Sprite sprite : covered) {
                        if (java.util.Objects.equals(label.action(), sprite.action())
                                && (label.x() >= sprite.x()
                                        && label.x() < sprite.x() + sprite.skin().width())
                                && label.row() >= sprite.row()
                                && label.row() < sprite.row() + sprite.skin().rows()) {
                            return true;
                        }
                    }
                    return false;
                });
        List<Label> clipped = new ArrayList<>();
        for (Label label : labels) {
            if (!(label.row() >= row && label.row() < row + rows)) {
                clipped.add(label);
                continue;
            }
            float cursor = label.x();
            float start = cursor;
            List<MeasuredGlyph> run = new ArrayList<>();
            for (MeasuredGlyph glyph : label.text().glyphs()) {
                float advance = glyph.advance();
                if (cursor + advance <= x || cursor >= x + width) {
                    if (run.isEmpty()) {
                        start = cursor;
                    }
                    run.add(glyph);
                } else if (!run.isEmpty()) {
                    clipped.add(label.moved(start, new MeasuredText(run)));
                    run = new ArrayList<>();
                }
                cursor += advance;
            }
            if (!run.isEmpty()) {
                clipped.add(label.moved(start, new MeasuredText(run)));
            }
        }
        labels.clear();
        labels.addAll(clipped);
    }

    public Component build() {
        TextComponent.Builder result = Component.text();
        int lineWidth = width + WRAP_SLACK;
        for (int row = 0; row < rows; row++) {
            // Hit advances come first so getStyleAtWidth sees a positive, monotonic click grid.
            // Following negative advances paint the visual layers on the same coordinates.
            TreeSet<Integer> cuts = new TreeSet<>();
            cuts.add(0);
            cuts.add(lineWidth);
            for (Hit hit : hits) {
                if (row >= hit.row() && row < hit.row() + hit.rows()) {
                    cuts.add(hit.x());
                    cuts.add(hit.x() + hit.width());
                }
            }
            List<Integer> points = new ArrayList<>(cuts);
            for (int i = 0; i + 1 < points.size(); i++) {
                int start = points.get(i);
                int end = points.get(i + 1);
                Hit found = null;
                for (Hit hit : hits) {
                    if (row >= hit.row()
                            && row < hit.row() + hit.rows()
                            && start >= hit.x()
                            && end <= hit.x() + hit.width()) {
                        found = hit;
                    }
                }
                Component region = space(end - start);
                if (found != null) {
                    region = interactive(region, found.action());
                }
                result.append(region);
            }
            result.append(space(-lineWidth));
            for (Sprite sprite : sprites) {
                if (row != sprite.row()) {
                    continue;
                }
                result.append(space(sprite.x()));
                if (sprite.preset() != null)
                    result.append(Component.text("\uE000").font(sprite.skin().font()));
                for (Slice slice : slices(sprite.skin())) {
                    Component glyph =
                            Component.text(String.valueOf((char) slice.glyph()))
                                    .font(sprite.skin().font())
                                    .color(NamedTextColor.WHITE);
                    if (sprite.preset() != null)
                        glyph =
                                glyph.color(
                                        TextColor.color(sprite.preset().color(sprite.progress())));
                    if (sprite.action() != null) {
                        glyph = interactive(glyph, sprite.action());
                    }
                    result.append(glyph);
                    // Bitmap advances trim transparent right edges. Restore the
                    // texture cell width using the compiled, measured advance.
                    result.append(space(slice.width() - slice.advance()));
                }
                if (sprite.preset() != null) {
                    result.append(Component.text("\uE001").font(sprite.skin().font()));
                }
                result.append(space(-sprite.x() - sprite.skin().width()));
            }
            // Space-only rows lack the CPU draw bounds required by modern client hit testing.
            // Six overlapping existing 36px bounds glyphs cover the fixed 80x54 click cell.
            // They also keep the shader-shifted run inside the client's CPU clipping bounds.
            for (Sprite sprite : sprites) {
                if (sprite.preset() == null) continue;
                int offset = row - sprite.row();
                if (offset != 0 && (offset != 2 || sprite.action() == null)) continue;
                int cells = sprite.action() == null ? 1 : 3;
                for (int cell = 0; cell < cells; cell++) {
                    int at = sprite.x() + (cells == 1 ? 0 : (cell - 1) * 22);
                    Component bounds =
                            Component.text("\uF124")
                                    .font(Key.key("dialogmenu_settings:native_bounds"));
                    if (sprite.action() != null) bounds = interactive(bounds, sprite.action());
                    result.append(space(at));
                    result.append(bounds);
                    result.append(space(-at - 37));
                }
            }
            for (Label label : labels) {
                if (label.row() != row) {
                    continue;
                }
                result.append(space(label.x()));
                Component text =
                        label.text().component().colorIfAbsent(TextColor.color(label.color()));
                if (label.action() != null) {
                    text = interactive(text, label.action());
                }
                result.append(text);
                result.append(space(-label.x() - label.text().advance()));
            }
            result.append(space(lineWidth));
            if (row < rows - 1) {
                result.append(Component.newline());
            }
        }
        return result.build().shadowColor(ShadowColor.none());
    }

    /** The glyphs a sprite paints left to right: its slices, else its equal texture columns. */
    private static List<Slice> slices(Skin skin) {
        if (!skin.slices().isEmpty()) {
            return skin.slices();
        }
        List<Slice> columns = new ArrayList<>();
        for (int column = 0; column < skin.columns(); column++) {
            Integer measured = Kt.getOrNull(skin.advances(), column);
            columns.add(
                    new Slice(
                            skin.glyph() + column,
                            skin.width() / skin.columns(),
                            measured != null ? measured : glyphWidth(skin.glyph() + column)));
        }
        return columns;
    }

    public void densitySlider(int x, int row, String selected, MenuLanguage language) {
        List<String> ids = Kt.listOf("off", "low", "medium", "high");
        List<String> actions = new ArrayList<>();
        for (String id : ids) {
            actions.add("density_" + id);
        }
        slider(
                x,
                row,
                ids.indexOf(Kt.lower(selected)),
                MenuDialog.densityLabel(selected, language),
                actions);
    }

    public void slider(int x, int row, int selected, String valueLabel, List<String> actions) {
        slider(x, row, selected, valueLabel, actions, 8, false);
    }

    public void slider(
            int x,
            int row,
            int selected,
            String valueLabel,
            List<String> actions,
            int textSize,
            boolean bold) {
        Kt.require(actions.size() >= 2 && actions.size() <= 8);
        MeasuredText label = prepare(valueLabel, textSize, bold, true).fit(52);
        text(
                x - 8 - label.width(),
                row + (textSize == 8 ? 1 : 0),
                label,
                null,
                null,
                true,
                textSize,
                bold);
        String previous =
                selected >= 1 && selected < actions.size() ? actions.get(selected - 1) : null;
        String next =
                selected >= 0 && selected < actions.size() - 1 ? actions.get(selected + 1) : null;
        sprite(x, row, new Skin(previous == null ? 0xE222 : 0xE220, 18, 2), previous);
        sprite(x + 146, row, new Skin(next == null ? 0xE223 : 0xE221, 18, 2), next);
        for (int index = 0; index < actions.size(); index++) {
            int start = 120 * index / actions.size();
            int end = 120 * (index + 1) / actions.size();
            sprite(
                    x + 22 + start,
                    row,
                    new Skin(sliderGlyph(actions.size(), selected, index), end - start, 2),
                    actions.get(index));
        }
    }

    public static int sliderGlyph(int count, int selected, int column) {
        Kt.require(count >= 2 && count <= 8 && column >= 0 && column < count);
        int index = selected >= 0 && selected < count ? selected : count;
        int offset = 0;
        for (int size = 2; size < count; size++) {
            offset += size * (size + 1);
        }
        return 0xE400 + offset + index * count + column;
    }

    private static Properties loadMetrics() {
        Properties properties = new Properties();
        try (InputStream input = DialogCanvas.class.getResourceAsStream("/ui-metrics.properties")) {
            Kt.requireNotNull(input, () -> "Missing compiled UI font metrics");
            properties.load(input);
        } catch (IOException error) {
            throw Kt.sneaky(error);
        }
        return properties;
    }

    public static int glyphWidth(int glyph) {
        return Integer.parseInt(METRICS.getProperty("glyph." + glyph));
    }

    public static Component space(float width) {
        Kt.require(Float.isFinite(width) && width * 2 == (float) (int) (width * 2));
        int whole = (int) width;
        float fraction = width - whole;
        Component result = space(whole);
        if (fraction == 0f) {
            return result;
        }
        String glyph = fraction > 0 ? "\uE7F0" : "\uE7F1";
        return result.append(Component.text(glyph).font(FONT));
    }

    public static Component space(int width) {
        Kt.require(width >= -4096 && width <= 4096);
        if (width < -512 || width > 512) {
            int step = Math.max(-512, Math.min(512, width));
            return space(step).append(space(width - step));
        }
        return Component.text(String.valueOf((char) (0xE800 + width + 512))).font(FONT);
    }

    // ASCII metrics come from the bundled menu font, independent of GUI
    // scale, Force Unicode Font, or another pack's minecraft:default.
    public static int textWidth(String text) {
        return textWidth(text, 8, false, LABEL_FONT);
    }

    public static int textWidth(String text, int textSize) {
        return textWidth(text, textSize, false, LABEL_FONT);
    }

    public static int textWidth(String text, int textSize, boolean bold) {
        return textWidth(text, textSize, bold, LABEL_FONT);
    }

    public static int textWidth(String text, int textSize, boolean bold, Key font) {
        if (textSize != 8) {
            return TitleFont.width(text, textSize) + (bold ? text.length() : 0);
        }
        return (int) Math.ceil(LabelMetrics.width(text, font, bold));
    }

    public static String fit(String text, int pixels) {
        return fit(text, pixels, 8, false, LABEL_FONT);
    }

    public static String fit(String text, int pixels, int textSize) {
        return fit(text, pixels, textSize, false, LABEL_FONT);
    }

    public static String fit(String text, int pixels, int textSize, boolean bold) {
        return fit(text, pixels, textSize, bold, LABEL_FONT);
    }

    public static String fit(String text, int pixels, int textSize, boolean bold, Key font) {
        StringBuilder result = new StringBuilder();
        for (int codepoint : text.codePoints().toArray()) {
            String character = new String(Character.toChars(codepoint));
            if (textWidth(result + character, textSize, bold, font) > pixels) {
                break;
            }
            result.append(character);
        }
        return result.toString();
    }
}
