#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
out vec4 vertexColor;
out vec2 settingsGuiPosition;
out vec2 settingsQuadCorner;
flat out vec2 settingsGuiExtent;

const vec2 CORNERS[4] = vec2[4](vec2(-1, -1), vec2(-1, 1), vec2(1, 1), vec2(1, -1));

void main() {
    vec4 viewPosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition;
    vertexColor = Color;
    settingsGuiPosition = viewPosition.xy;
    settingsGuiExtent = vec2(2.0 / abs(ProjMat[0][0]), 2.0 / abs(ProjMat[1][1]));
#ifdef VULKAN
    settingsQuadCorner = CORNERS[gl_VertexIndex % 4];
#else
    settingsQuadCorner = CORNERS[gl_VertexID % 4];
#endif
}
