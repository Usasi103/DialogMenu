package online.toraka.dialogmenu;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

/** Finite preset gallery. It never edits menus or grants items. */
final class AnimationDemo {
    static final String ID = "demo-animation";
    static final int WIDTH = 360;
    static final int ROWS = 19;
    private static final AnimationSettings DEFAULTS = AnimationSettings.defaults();
    static final Key FONT = Key.key("dialogmenu_animation:icons");
    static final List<String> NAMES = List.of("钻石", "绿宝石", "书本", "末影珍珠", "金苹果", "下界之星");
    private static final Properties ADVANCES = advances();

    private AnimationDemo() {}

    static double progress(int tick, int icon) {
        return progress(DEFAULTS, AnimationPreset.FADE_IN, tick, icon);
    }

    static double progress(AnimationSettings settings, AnimationPreset preset, int tick, int icon) {
        if (icon < 0 || icon >= NAMES.size()) throw new IllegalArgumentException("icon");
        return settings.timing(preset).progress(tick / 20.0 - icon * settings.stagger());
    }

    static boolean changed(AnimationSettings settings, AnimationPreset preset, int tick) {
        for (int icon = 0; icon < NAMES.size(); icon++) {
            if (AnimationPreset.phase(progress(settings, preset, tick, icon))
                    != AnimationPreset.phase(progress(settings, preset, tick - 1, icon)))
                return true;
        }
        return false;
    }

    static DialogCanvas render(
            AnimationPreset preset, int tick, String notice, Function<String, ClickEvent> click) {
        return render(preset, DEFAULTS, tick, notice, click);
    }

    static DialogCanvas render(
            AnimationPreset preset,
            AnimationSettings settings,
            int tick,
            String notice,
            Function<String, ClickEvent> click) {
        DialogCanvas canvas =
                new DialogCanvas(
                        MenuTheme.DARK,
                        WIDTH,
                        ROWS,
                        DialogCanvas.LABEL_FONT,
                        DialogCanvas.BUTTON_LABEL_FONT,
                        new RichMenuText(),
                        click);
        centered(canvas, preset.title() + " · " + preset.id(), WIDTH / 2, 0, 0xC8D0DC, null);
        for (int icon = 0; icon < NAMES.size(); icon++) {
            double progress = progress(settings, preset, tick, icon);
            int center = 76 + icon % 3 * 104;
            int row = 2 + icon / 3 * 7;
            int glyph = 0xE100 + icon;
            String action = "icon/" + icon;
            boolean visible = preset.frame(progress).visible();
            canvas.animatedSprite(
                    center - 18,
                    row,
                    new DialogCanvas.Skin(
                            glyph,
                            36,
                            4,
                            FONT,
                            List.of(Integer.parseInt(ADVANCES.getProperty("glyph." + glyph)))),
                    preset,
                    progress,
                    action);
            canvas.tooltip(action, Component.text(NAMES.get(icon) + " · 点击查看反馈"));
            centered(canvas, NAMES.get(icon), center, row + 5, 0xEEEEEE, visible ? action : null);
        }
        centered(canvas, notice, WIDTH / 2, 17, 0xE3C58A, null);
        return canvas;
    }

    private static void centered(
            DialogCanvas canvas, String text, int center, int row, int color, String action) {
        MeasuredText measured = canvas.prepare(text);
        canvas.text(center - measured.width() / 2, row, measured, color, action, false, 8, false);
    }

    private static Properties advances() {
        Properties result = new Properties();
        try (InputStream input =
                AnimationDemo.class.getResourceAsStream("/animation-icons.properties")) {
            if (input == null) throw new IllegalStateException("Missing animation font metrics");
            result.load(input);
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
        return result;
    }
}
