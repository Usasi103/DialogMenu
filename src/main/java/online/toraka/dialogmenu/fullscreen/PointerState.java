package online.toraka.dialogmenu.fullscreen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small, bounded mailbox shared by Netty input and the server thread. No Bukkit access here. */
final class PointerState {
    enum Phase {
        STARTING,
        ACTIVE,
        ENDING,
        CLOSED
    }

    enum Sync {
        SAMPLE,
        RESET,
        EXIT
    }

    record Point(double x, double y) {}

    record Click(Point point, String button) {}

    private final double sensitivity;
    private final boolean local;
    private final Map<Integer, Sync> pending = new HashMap<>();
    private final ArrayDeque<Click> clicks = new ArrayDeque<>();
    private Phase phase = Phase.STARTING;
    private double x;
    private double y;
    private float yaw;
    private float pitch;
    private boolean resetNeeded;
    private boolean resetting = true;
    private boolean exitRequested;
    private long lastReply = System.nanoTime();
    private long lastClick;
    private long samples;

    PointerState(double sensitivity) {
        this(sensitivity, false);
    }

    PointerState(double sensitivity, boolean local) {
        this.sensitivity = sensitivity;
        this.local = local;
    }

    synchronized boolean expect(int id, Sync kind) {
        if (phase == Phase.CLOSED || kind == Sync.SAMPLE && pending.size() >= 8) {
            return false;
        }
        pending.put(id, kind);
        if (kind == Sync.RESET) resetting = true;
        return true;
    }

    synchronized boolean acknowledge(int id, float nextYaw, float nextPitch) {
        Sync kind = pending.remove(id);
        if (kind == null) return false;
        update(kind, nextYaw, nextPitch);
        return true;
    }

    synchronized void rotation(float nextYaw, float nextPitch) {
        update(null, nextYaw, nextPitch);
    }

    /** 26.3 carries rotation in the teleport acknowledgement; no second PosRot is sent. */
    private void update(Sync kind, float nextYaw, float nextPitch) {
        if (phase == Phase.CLOSED) return;
        if (kind == Sync.EXIT) {
            phase = Phase.CLOSED;
            pending.clear();
            clicks.clear();
            return;
        }
        if (!Float.isFinite(nextYaw) || !Float.isFinite(nextPitch)) return;
        if (kind != null) lastReply = System.nanoTime();
        if (kind == Sync.RESET && phase != Phase.ENDING) {
            yaw = nextYaw;
            pitch = nextPitch;
            resetting = false;
            phase = Phase.ACTIVE;
            return;
        }
        if (phase != Phase.ACTIVE || resetting) return;
        x =
                clamp(
                        local ? wrap(nextYaw) * sensitivity : x + wrap(nextYaw - yaw) * sensitivity,
                        -158,
                        158);
        y =
                clamp(
                        local ? -nextPitch * sensitivity : y - (nextPitch - pitch) * sensitivity,
                        -88,
                        88);
        yaw = nextYaw;
        pitch = nextPitch;
        samples++;
        if (!local && Math.abs(nextPitch) >= 80) {
            resetNeeded = true;
            resetting = true;
        }
    }

    synchronized void click(String button, long now) {
        if (phase != Phase.ACTIVE || resetting || now - lastClick < 150_000_000L) return;
        lastClick = now;
        if (clicks.size() < 8) clicks.addLast(new Click(new Point(x, y), button));
    }

    synchronized List<Click> drainClicks() {
        var result = new ArrayList<>(clicks);
        clicks.clear();
        return result;
    }

    synchronized Point point() {
        return new Point(x, y);
    }

    synchronized Phase phase() {
        return phase;
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    synchronized long samples() {
        return samples;
    }

    synchronized boolean timedOut(long now) {
        return now - lastReply > 5_000_000_000L;
    }

    synchronized void requestExit() {
        exitRequested = true;
    }

    synchronized boolean exitRequested() {
        return exitRequested;
    }

    synchronized boolean takeReset() {
        boolean result = resetNeeded;
        resetNeeded = false;
        return result;
    }

    synchronized void ending() {
        phase = Phase.ENDING;
        clicks.clear();
    }

    synchronized void discard() {
        phase = Phase.CLOSED;
        pending.clear();
        clicks.clear();
    }

    static double wrap(double degrees) {
        double result = degrees % 360;
        if (result >= 180) result -= 360;
        if (result < -180) result += 360;
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
