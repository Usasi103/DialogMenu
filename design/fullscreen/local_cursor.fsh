layout(location = 4) flat in int dmLocal;
layout(location = 5) in vec2 dmCanvas;
layout(location = 6) in vec3 dmView;
layout(location = 7) in vec2 dmTexel;
#include <minecraft:globals.glsl>

bool dm_inside(vec2 point, vec4 rect) {
    return all(lessThanEqual(abs(point - rect.xy), rect.zw * 0.5));
}

float dm_cross_coverage(vec2 distance, vec2 pixel, float halfWidth, float radius) {
    // Analytic pixel coverage of two intersecting rectangles. Each edge blends over
    // one physical pixel, including at non-integer window scales and subpixel motion.
    vec2 outer = clamp((vec2(radius) - distance) / pixel + 0.5, 0.0, 1.0);
    vec2 inner = clamp((vec2(halfWidth) - distance) / pixel + 0.5, 0.0, 1.0);
    return outer.x * outer.y * (1.0 - (1.0 - inner.x) * (1.0 - inner.y));
}

bool dm_local_fragment() {
    if (dmLocal == 0) {
#ifndef IS_GUI
        // Remove world labels (including see-through NPC names) during this player's menu.
        if (GameTime < -0.2 && GameTime > -0.55) discard;
#endif
        return false;
    }
    // Screen-space derivatives recover the world-fixed glyph's view-space axes.
    // Translation, font size, FOV, GUI scale and window size cancel out.
    // Normalize against canvas coordinates instead of assuming derivative scale/sign.
    // TextDisplay's Ry(pi) * scale(-.025) makes glyph-right world +X.
    // Camera-right at yaw zero is world -X, so reverse this recovered axis.
    vec3 right = -normalize(dFdx(dmView) / dFdx(dmCanvas.x));
    vec3 up = normalize(dFdy(dmView) / dFdy(dmCanvas.y));
    vec3 normal = normalize(cross(right, up));
    float yaw = degrees(atan(normal.x, right.x));
    float pitch = degrees(atan(up.z, up.y));
    vec2 pointer = clamp(vec2(yaw, -pitch) * 2.0, vec2(-158,-88), vec2(158,88));
    // Filter only this GUI tile, preserving vanilla sampling for all other text.
    // Neighbour gutters keep interpolation continuous across tile boundaries.
    vec2 sampleAt = dmTexel - 0.5;
    ivec2 base = ivec2(floor(sampleAt));
    vec2 weight = fract(sampleAt);
    vec4 color = mix(
        mix(texelFetch(Sampler0, base, 0), texelFetch(Sampler0, base + ivec2(1,0), 0), weight.x),
        mix(texelFetch(Sampler0, base + ivec2(0,1), 0), texelFetch(Sampler0, base + ivec2(1,1), 0), weight.x), weight.y);
    // Generated from DemoLayout.java by build_fullscreen_pack.py.
    const vec4 buttons[7] = vec4[7](DM_BUTTONS);
    for (int i = 0; i < 7; ++i) {
        if (dm_inside(pointer, buttons[i]) && dm_inside(dmCanvas, buttons[i])) {
            color.rgb = min(color.rgb + vec3(0.10, 0.17, 0.22), vec3(1.0));
        }
    }
    vec2 d = abs(dmCanvas - pointer);
    vec2 pixel = max(fwidth(dmCanvas), vec2(0.00001));
    color = mix(color, vec4(0.03,0.04,0.06,1.0), dm_cross_coverage(d, pixel, 1.4, 5.3));
    color = mix(color, vec4(1.0,0.87,0.20,1.0), dm_cross_coverage(d, pixel, 0.55, 4.5));
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
#endif
    fragColor = color;
#endif
    return true;
}
