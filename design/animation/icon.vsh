// Shared GUI-only icon protocol: negative-X band 192, RGB = preset/phase/215.
// One square bitmap glyph is 36 GUI pixels; no image variants or world entities.
layout(location = 8) flat out float dmAnimationAlpha;
void dm_animation_vertex() {
    dmAnimationAlpha = -1.0;
#ifdef IS_GUI
    if (ProjMat[3][3] < 0.5) return;
    int band = int(floor(-Position.x / 16384.0 + 0.5));
    ivec3 code = ivec3(round(Color.rgb * 255.0));
    if (band != 192 || code.b != 215 || code.r < 0 || code.r > 13) return;
    float t = float(code.g) / 255.0;
    float e = t * t * (3.0 - 2.0 * t);
    float pi = 3.14159265359;
    float wave = sin(pi * t);
    vec2 offset = vec2(0.0);
    float scale = 1.0, angle = 0.0, alpha = 1.0;
    if (code.r == 0) alpha = e;
    else if (code.r == 1) alpha = 1.0 - e;
    else if (code.r == 2) { offset.x = -16.0 * (1.0 - e); alpha = t == 0.0 ? 0.0 : 1.0; }
    else if (code.r == 3) { offset.x = 16.0 * e; alpha = t == 1.0 ? 0.0 : 1.0; }
    else if (code.r == 4) scale = e;
    else if (code.r == 5) scale = 1.0 - e;
    else if (code.r == 6 || code.r == 7) {
        float p = code.r == 6 ? t : 1.0 - t;
        float u = p - 1.0;
        scale = p == 0.0 || p == 1.0 ? p : 1.0 + 2.70158 * u * u * u + 1.70158 * u * u;
        offset.y = -6.0 * sin(3.0 * pi * t) * (code.r == 6 ? 1.0 - t : t);
    }
    else if (code.r == 8) scale = 1.0 + 0.18 * wave * wave;
    else if (code.r == 9) offset.x = 5.0 * sin(8.0 * pi * t) * wave;
    else if (code.r == 10) angle = 0.28 * sin(4.0 * pi * t) * wave;
    else if (code.r == 11) angle = 2.0 * pi * e;
    else if (code.r == 12) { offset.y = 8.0 * (1.0 - e); alpha = e; }
    else if (code.r == 13) { offset.y = -8.0 * e; alpha = 1.0 - e; }
    // Infer corners from the atlas texel inset, independent of vertex batching order.
    vec2 corner = step(vec2(0.5), fract(UV0 * vec2(textureSize(Sampler0, 0))));
    vec2 local = (corner - 0.5) * 36.0;
    vec3 pos = Position;
    pos.x += 3145728.0;
    pos.xy -= local;
    float c = cos(angle), s = sin(angle);
    pos.xy += mat2(c, s, -s, c) * local * max(0.0, scale) + offset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexColor = vec4(1.0, 1.0, 1.0, Color.a);
    dmAnimationAlpha = alpha;
#endif
}
