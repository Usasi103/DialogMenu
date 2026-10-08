package online.toraka.dialogmenu;

/** Seconds from playback start, with a fixed start and a speed-scaled duration. */
public record AnimationTiming(double start, double end, double speed) {
    public AnimationTiming {
        if (!Double.isFinite(start)
                || !Double.isFinite(end)
                || !Double.isFinite(speed)
                || start < 0
                || end <= start
                || speed <= 0
                || end > 3600
                || !Double.isFinite(start + (end - start) / speed)
                || start + (end - start) / speed > 3600
                || start + (end - start) / speed <= start) {
            throw new IllegalArgumentException(
                    "start/end 必须为 0–3600 秒且 end > start；speed 必须为正数，实际结束不超过 3600 秒");
        }
    }

    public double actualEnd() {
        return start + (end - start) / speed;
    }

    /** Before start, holds frame zero; after actualEnd, holds the completed frame. */
    public double progress(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds))
            throw new IllegalArgumentException("Non-finite animation time");
        if (elapsedSeconds <= start) return 0;
        if (elapsedSeconds >= actualEnd()) return 1;
        return Math.clamp((elapsedSeconds - start) / (end - start) * speed, 0, 1);
    }
}
