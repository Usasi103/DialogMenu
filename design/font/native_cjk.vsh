// Public menu typography. Only explicit negative-X bands in orthographic GUI text opt in.
// Runs after the vanilla text and fullscreen hooks; world text and HUD coordinates pass through.
void dm_native_cjk_vertex() {
#ifdef IS_GUI
    if (ProjMat[3][3] < 0.5) return;
    int band = int(floor((-Position.x + 8192.0) / 16384.0));
    if (band < 200 || band >= 276) return;
    int encoded = band - 200;
    bool italic = encoded >= 38;
    int typography = encoded % 38;
    int size = typography / 2 + 6;
    bool raised = (typography % 2) != 0;
    float paddedHeight = float((size * 3 + 1) / 2);
    float scale = paddedHeight / 12.0;
    float ascent = size == 8 ? 11.0
        : raised && size <= 12 ? float(7 - (18 - size) / 2) : float(min(7, size));
#ifdef VULKAN
    int corner = gl_VertexIndex % 4;
#else
    int corner = gl_VertexID % 4;
#endif
    // Vanilla BakedSheetGlyph emits top-left, bottom-left, bottom-right, top-right.
    vec2 local = vec2(corner >= 2 ? 8.0 : 0.0, corner == 1 || corner == 2 ? 8.0 : 0.0);
    vec3 position = Position;
    position.x += float(band * 16384) + local.x * (scale - 1.0);
    position.y += 7.0 - ascent + local.y * (scale - 1.0);
    if (italic) position.x += 1.0 - 0.25 * (7.0 - ascent + local.y / 8.0 * paddedHeight);
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
#endif
}
