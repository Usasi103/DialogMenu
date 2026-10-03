// Called after the vanilla 26.3 text shader.
layout(location = 4) flat out int dmLocal;
layout(location = 5) out vec2 dmCanvas;
layout(location = 6) out vec3 dmView;
layout(location = 7) out vec2 dmTexel;
uniform sampler2D Sampler0;
#include <minecraft:globals.glsl>

void dm_local_vertex() {
    dmLocal = 0;
    dmCanvas = vec2(0.0);
    dmView = vec3(0.0);
    dmTexel = vec2(0.0);
#if !defined(IS_GUI) && !defined(IS_GRAYSCALE)
    if (all(equal(ivec3(round(Color.rgb * 255.0)), ivec3(253, 23, 171)))) {
        // Atlas UVs carry the actual corner. Vertex indices can be reordered by batching.
        ivec2 size = textureSize(Sampler0, 0);
        ivec2 texel = clamp(ivec2(UV0 * size), ivec2(0), size - 1);
        ivec3 tag = ivec3(round(texelFetch(Sampler0, texel, 0).rgb * 255.0));
        const vec2 grid = DM_TILE_GRID;
        const vec2 tileSize = DM_TILE_SIZE;
        const int border = DM_TILE_BORDER;
        int tile = tag.r - 32;
        if (tile < 0 || tile >= int(grid.x * grid.y) || (tag.g != 31 && tag.g != 223) || (tag.b != 31 && tag.b != 223)) return;
        vec2 corner = vec2(tag.gb - ivec2(127)) / 96.0;
        vec2 fraction = vec2(corner.x + 1.0, 1.0 - corner.y) * 0.5;
        vec2 origin = vec2(texel) - fraction * (tileSize + float(2 * border - 1));
        dmTexel = origin + float(border) + fraction * tileSize;
        dmLocal = 1;
        vec2 cell = vec2(tile % int(grid.x), tile / int(grid.x));
        vec2 screen = (cell + fraction) / grid;
        dmCanvas = vec2(screen.x * 320.0 - 160.0, 90.0 - screen.y * 180.0);
        dmView = (ModelViewMat * vec4(Position, 1.0)).xyz;
        // 26.3 uses reversed depth: 1 is nearest. The normal text pass writes depth.
        gl_Position = vec4(dmCanvas / vec2(160.0, 90.0), 1.0, 1.0);
        vertexColor = vec4(1.0);
    }
#endif
}
