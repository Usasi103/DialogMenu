package online.toraka.dialogmenu;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Reusable, finite GUI icon presets. IDs and shader codes are a versioned resource contract. */
public enum AnimationPreset {
    FADE_IN("淡入"),
    FADE_OUT("淡出"),
    FLY_IN("飞入"),
    FLY_OUT("飞出"),
    ZOOM_IN("放大进入"),
    ZOOM_OUT("缩小退出"),
    BOUNCE_IN("弹跳进入"),
    BOUNCE_OUT("回弹退出"),
    PULSE("脉冲"),
    SHAKE("抖动"),
    SWING("摇摆"),
    SPIN("旋转"),
    FLOAT_IN("上浮淡入"),
    FLOAT_OUT("上浮淡出");

    private final String title;

    AnimationPreset(String title) {
        this.title = title;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String title() {
        return title;
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(AnimationPreset::id).toList();
    }

    public static AnimationPreset parse(String id) {
        if (id == null || id.equals("main")) return FADE_IN;
        return valueOf(id.toUpperCase(Locale.ROOT));
    }

    /** Clamps finite progress and quantizes to the same 256 phases sent to the GUI shader. */
    public static int phase(double progress) {
        if (!Double.isFinite(progress))
            throw new IllegalArgumentException("Non-finite animation progress");
        return (int) Math.round(Math.clamp(progress, 0.0, 1.0) * 255);
    }

    int color(double progress) {
        return ordinal() << 16 | phase(progress) << 8 | 215;
    }

    /** GUI pixels, radians and opacity; also used to validate the shipped shader on the GPU. */
    public record Frame(double x, double y, double scale, double angle, double opacity) {
        public boolean visible() {
            return opacity > 0 && scale > 0;
        }
    }

    public Frame frame(double progress) {
        double t = phase(progress) / 255.0;
        double e = t * t * (3 - 2 * t);
        double wave = Math.sin(Math.PI * t);
        double x = 0, y = 0, scale = 1, angle = 0, opacity = 1;
        switch (this) {
            case FADE_IN -> opacity = e;
            case FADE_OUT -> opacity = 1 - e;
            case FLY_IN -> {
                x = -16 * (1 - e);
                opacity = t == 0 ? 0 : 1;
            }
            case FLY_OUT -> {
                x = 16 * e;
                opacity = t == 1 ? 0 : 1;
            }
            case ZOOM_IN -> scale = e;
            case ZOOM_OUT -> scale = 1 - e;
            case BOUNCE_IN -> {
                scale = back(t);
                y = -6 * Math.sin(3 * Math.PI * t) * (1 - t);
            }
            case BOUNCE_OUT -> {
                scale = back(1 - t);
                y = -6 * Math.sin(3 * Math.PI * t) * t;
            }
            case PULSE -> scale = 1 + 0.18 * wave * wave;
            case SHAKE -> x = 5 * Math.sin(8 * Math.PI * t) * wave;
            case SWING -> angle = 0.28 * Math.sin(4 * Math.PI * t) * wave;
            case SPIN -> angle = 2 * Math.PI * e;
            case FLOAT_IN -> {
                y = 8 * (1 - e);
                opacity = e;
            }
            case FLOAT_OUT -> {
                y = -8 * e;
                opacity = 1 - e;
            }
        }
        return new Frame(x, y, Math.max(0, scale), angle, opacity);
    }

    private static double back(double t) {
        if (t == 0 || t == 1) return t;
        double u = t - 1;
        return 1 + 2.70158 * u * u * u + 1.70158 * u * u;
    }
}
