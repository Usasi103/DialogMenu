#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 settingsGuiPosition;
layout(location = 2) out vec2 settingsQuadCorner;
layout(location = 3) flat out vec2 settingsGuiExtent;

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
