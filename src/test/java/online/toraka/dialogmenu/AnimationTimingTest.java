package online.toraka.dialogmenu;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AnimationTimingTest {
    @Test
    void speedScalesDurationWithoutMovingStart() {
        var fast = new AnimationTiming(1, 3, 2);
        assertEquals(2, fast.actualEnd());
        assertEquals(0, fast.progress(0.9));
        assertEquals(0, fast.progress(1));
        assertEquals(0.5, fast.progress(1.5));
        assertEquals(1, fast.progress(2));
        assertEquals(1, fast.progress(200));
        var slow = new AnimationTiming(1, 3, 0.5);
        assertEquals(5, slow.actualEnd());
        assertEquals(0.5, slow.progress(3));
    }

    @Test
    void invalidTimesCannotCreateUnboundedPlayback() {
        for (double[] values :
                new double[][] {
                    {-1, 1, 1},
                    {1, 1, 1},
                    {2, 1, 1},
                    {0, 1, 0},
                    {0, 1, -1},
                    {Double.NaN, 1, 1},
                    {0, Double.POSITIVE_INFINITY, 1},
                    {0, 1, Double.NaN},
                    {0, 1, Double.POSITIVE_INFINITY},
                    {0, 3601, 1},
                    {0, 1, 0.00001},
                    {1, 2, Double.MAX_VALUE}
                })
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnimationTiming(values[0], values[1], values[2]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnimationTiming(0, 1, 1).progress(Double.NaN));
    }

    @Test
    void presetsInheritDefaultsAndCanOverrideEveryTimingField() {
        var settings =
                AnimationSettings.parse(
                        """
                defaults: {start: 1, end: 3, speed: 2}
                demo-stagger: 0.25
                presets:
                  fade_in: {start: 0.5}
                  spin: {start: 2, end: 6, speed: 4}
                """);
        assertEquals(new AnimationTiming(0.5, 3, 2), settings.timing(AnimationPreset.FADE_IN));
        assertEquals(new AnimationTiming(1, 3, 2), settings.timing(AnimationPreset.SHAKE));
        assertEquals(new AnimationTiming(2, 6, 4), settings.timing(AnimationPreset.SPIN));
        assertEquals(85, settings.durationTicks(AnimationPreset.SPIN));
        assertEquals(0, AnimationDemo.progress(settings, AnimationPreset.SPIN, 65, 5));
        assertEquals(1, AnimationDemo.progress(settings, AnimationPreset.SPIN, 85, 5));
    }

    @Test
    void typosInvalidValuesAndInvalidOverridesAreRejected() {
        for (String source :
                new String[] {
                    "defaults: {speeed: 2}", "defaults: {speed: '2'}", "defaults: {speed: false}",
                    "defaults: {speed: null}", "defaults: {speed: .nan}", "defaults: {end: 0}",
                    "presets: {wipe: {}}", "presets: {spin: 2}", "demo-stagger: -1",
                    "defaults: {end: 3600}", "presets: {fade_in: {start: 2}}", "presets: []"
                }) {
            var error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> AnimationSettings.parse(source),
                            source);
            assertTrue(error.getMessage().contains("animations.yml"));
        }
    }

    @Test
    void delayedPlaybackDoesNotSendDuplicateFramesAndEndsAtRoundedTick() {
        var settings =
                AnimationSettings.parse(
                        "defaults: {start: 0.2, end: 0.6, speed: 2}\ndemo-stagger: 0");
        for (var preset : AnimationPreset.values()) {
            assertEquals(8, settings.durationTicks(preset));
            for (int tick = 1; tick <= 4; tick++)
                assertFalse(AnimationDemo.changed(settings, preset, tick));
            for (int tick = 5; tick <= 8; tick++)
                assertTrue(AnimationDemo.changed(settings, preset, tick));
            assertFalse(AnimationDemo.changed(settings, preset, 9));
        }
        assertEquals(
                3,
                AnimationSettings.parse("defaults: {end: 0.101}\ndemo-stagger: 0")
                        .durationTicks(AnimationPreset.SPIN));
    }
}
