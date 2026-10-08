package online.toraka.dialogmenu;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Immutable, transactionally installed timing defaults and per-preset overrides. */
record AnimationSettings(Map<AnimationPreset, AnimationTiming> presets, double stagger) {
    AnimationSettings {
        presets = Map.copyOf(presets);
    }

    static AnimationSettings defaults() {
        return parse(MenuRepository.resource("animations.yml"));
    }

    AnimationTiming timing(AnimationPreset preset) {
        return presets.get(java.util.Objects.requireNonNull(preset));
    }

    int durationTicks(AnimationPreset preset) {
        return (int) Math.ceil((timing(preset).actualEnd() + 5 * stagger) * 20);
    }

    static AnimationSettings parse(String source) {
        Object raw = MenuConfigParser.yamlValue(source, "animations.yml");
        if (!(raw instanceof Map<?, ?> root))
            throw new IllegalArgumentException("animations.yml: ??????");
        keys(root, "animations.yml", Set.of("defaults", "presets", "demo-stagger"));
        AnimationTiming fallback = new AnimationTiming(0, 1, 1);
        AnimationTiming base =
                timing(
                        section(root, "defaults", "animations.yml"),
                        fallback,
                        "animations.yml.defaults");
        double stagger = number(root, "demo-stagger", 0.1, "animations.yml");
        if (!Double.isFinite(stagger) || stagger < 0 || stagger > 3600)
            throw new IllegalArgumentException("animations.yml.demo-stagger: 需要 0–3600 秒");
        Map<?, ?> overrides = section(root, "presets", "animations.yml");
        keys(overrides, "animations.yml.presets", Set.copyOf(AnimationPreset.ids()));
        Map<AnimationPreset, AnimationTiming> result = new EnumMap<>(AnimationPreset.class);
        for (AnimationPreset preset : AnimationPreset.values()) {
            String path = "animations.yml.presets." + preset.id();
            AnimationTiming value =
                    timing(section(overrides, preset.id(), "animations.yml.presets"), base, path);
            if (value.actualEnd() + 5 * stagger > 3600)
                throw new IllegalArgumentException(path + ": 含 demo-stagger 的演示总长不能超过 3600 秒");
            result.put(preset, value);
        }
        return new AnimationSettings(result, stagger);
    }

    private static AnimationTiming timing(Map<?, ?> section, AnimationTiming base, String path) {
        keys(section, path, Set.of("start", "end", "speed"));
        try {
            return new AnimationTiming(
                    number(section, "start", base.start(), path),
                    number(section, "end", base.end(), path),
                    number(section, "speed", base.speed(), path));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    private static Map<?, ?> section(Map<?, ?> parent, String key, String path) {
        if (parent == null || !parent.containsKey(key)) return null;
        if (!(parent.get(key) instanceof Map<?, ?> value))
            throw new IllegalArgumentException(path + "." + key + ": 需要键值映射");
        return value;
    }

    private static double number(Map<?, ?> section, String key, double fallback, String path) {
        if (section == null || !section.containsKey(key)) return fallback;
        if (!(section.get(key) instanceof Number value))
            throw new IllegalArgumentException(path + "." + key + ": 需要数值");
        return value.doubleValue();
    }

    private static void keys(Map<?, ?> section, String path, Set<String> allowed) {
        if (section == null) return;
        for (Object key : section.keySet())
            if (!allowed.contains(key))
                throw new IllegalArgumentException(path + "." + key + ": 未知字段");
    }
}
