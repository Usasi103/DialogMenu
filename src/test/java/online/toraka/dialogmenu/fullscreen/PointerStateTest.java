package online.toraka.dialogmenu.fullscreen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PointerStateTest {
    @Test
    void localCursorMatchesAbsoluteShaderAnglesWithoutEdgeDriftOrPitchReset() {
        var state = new PointerState(2, true);
        state.expect(-1, PointerState.Sync.RESET);
        state.acknowledge(-1, 0, 0);
        state.rotation(150, 89);
        assertEquals(new PointerState.Point(158, -88), state.point());
        assertFalse(state.takeReset());
        state.rotation(59.5f, -33.5f);
        assertEquals(new PointerState.Point(119, 67), state.point());
        assertEquals("top-right", DemoLayout.hit(state.point()).id());
        state.rotation(360, 0);
        assertEquals(0, state.point().x(), 0.00001);
        assertEquals(0, state.point().y(), 0.00001);
        state.rotation(Float.NaN, 0);
        assertEquals(0, state.point().x(), 0.00001);
        assertEquals(0, state.point().y(), 0.00001);
    }

    private PointerState ready(float yaw) {
        PointerState state = new PointerState(2);
        state.expect(-1, PointerState.Sync.RESET);
        state.acknowledge(-1, yaw, 0);
        return state;
    }

    @Test
    void yawCrossesSeamAndPitchHasScreenDirection() {
        var state = ready(179);
        state.rotation(-179, 5);
        assertEquals(new PointerState.Point(4, -10), state.point());
    }

    @Test
    void canvasClampsAndRejectsNonfiniteInput() {
        var state = ready(0);
        state.rotation(150, -70);
        assertEquals(new PointerState.Point(158, 88), state.point());
        state.rotation(Float.NaN, Float.POSITIVE_INFINITY);
        assertEquals(new PointerState.Point(158, 88), state.point());
    }

    @Test
    void pitchRecenteringDoesNotMoveCursorOrConsumeAnOlderSampleAsReset() {
        var state = ready(0);
        state.expect(-2, PointerState.Sync.SAMPLE);
        state.rotation(10, 81);
        var before = state.point();
        assertTrue(state.takeReset());
        state.expect(-3, PointerState.Sync.RESET);
        state.acknowledge(-2, 20, 85);
        assertEquals(before, state.point());
        state.acknowledge(-3, 0, 0);
        assertEquals(before, state.point());
        state.rotation(2, 0);
        assertEquals(before.x() + 4, state.point().x());
    }

    @Test
    void clickCapturesItsOwnCoordinateAndCoalescesDuplicatePackets() {
        var state = ready(0);
        state.click("right", 1_000_000_000L);
        state.click("right", 1_001_000_000L);
        state.rotation(50, 0);
        var clicks = state.drainClicks();
        assertEquals(1, clicks.size());
        assertEquals(new PointerState.Point(0, 0), clicks.getFirst().point());
        assertEquals("centre", DemoLayout.hit(clicks.getFirst().point()).id());
    }

    @Test
    void outstandingSamplingIsBoundedUntilMatchingAckWithCoordinates() {
        var state = ready(0);
        for (int i = 2; i < 10; i++) {
            assertTrue(state.expect(-i, PointerState.Sync.SAMPLE));
        }
        assertEquals(8, state.pendingCount());
        assertFalse(state.expect(-10, PointerState.Sync.SAMPLE));
        assertFalse(state.acknowledge(123, 0, 0));
        assertTrue(state.acknowledge(-2, 10, 5));
        assertEquals(7, state.pendingCount());
        assertEquals(new PointerState.Point(20, -10), state.point());
        state.discard();
        assertEquals(0, state.pendingCount());
    }

    @Test
    void exitCompletesOnCoordinateAckWithoutASecondPacket() {
        var state = ready(0);
        state.click("right", 1_000_000_000L);
        state.ending();
        state.expect(-5, PointerState.Sync.EXIT);
        assertEquals(PointerState.Phase.ENDING, state.phase());
        assertTrue(state.drainClicks().isEmpty());
        state.acknowledge(-5, 0, 0);
        assertEquals(PointerState.Phase.CLOSED, state.phase());
        assertEquals(0, state.pendingCount());
    }

    @Test
    void hitRegionsIncludeCornersButExcludeEmptyCanvas() {
        for (var button : DemoLayout.BUTTONS) {
            assertSame(button, DemoLayout.hit(new PointerState.Point(button.x(), button.y())));
        }
        assertNull(DemoLayout.hit(new PointerState.Point(0, 80)));
        assertEquals("top-right", DemoLayout.hit(new PointerState.Point(159, 89)).id());
        assertEquals("top-left", DemoLayout.hit(new PointerState.Point(-160, 90)).id());
        assertEquals("bottom-right", DemoLayout.hit(new PointerState.Point(160, -90)).id());
        assertEquals("bottom-left", DemoLayout.hit(new PointerState.Point(-160, -90)).id());
    }
}
