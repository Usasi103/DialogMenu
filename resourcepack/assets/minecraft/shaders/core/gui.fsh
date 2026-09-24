#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 settingsGuiPosition;
in vec2 settingsQuadCorner;
flat in vec2 settingsGuiExtent;
out vec4 fragColor;

bool nearValue(float value, float target) { return abs(value - target) < 0.6; }
bool nearSize(vec2 value, vec2 target) { return all(lessThan(abs(value - target), vec2(0.15))); }
bool inScrollRange(float value, float top, float travel) {
    return value >= top - travel - 0.6 && value <= top + 0.6;
}

bool dialogFocusOutline(vec4 color, float bodyWidth, float bodyHeight) {
    // Opt-in shape: 474x269, horizontally centered Dialog body. This is NOT a
    // global white-line filter. Native dialogs and input/button borders remain.
    if (any(lessThan(color, vec4(254.5 / 255.0)))) return false;
    vec2 size = 2.0 * fwidth(settingsGuiPosition) / max(fwidth(settingsQuadCorner), vec2(0.000001));
    if (min(size.x, size.y) > 1.5 || settingsGuiExtent.x < bodyWidth) return false;
    // HeaderAndFooterLayout uses a 33px header/footer and a 30px top
    // content margin, rather than vertically centering the body.
    float contentHeight = max(0.0, settingsGuiExtent.y - 66.0);
    float top = min(63.0, settingsGuiExtent.y - 33.0 - min(bodyHeight, contentHeight));
    float scrollTravel = max(0.0, bodyHeight - contentHeight);
    vec2 topLeft = vec2((settingsGuiExtent.x - bodyWidth) * 0.5, top);
    // GuiRenderer can clip/split border quads before shading. Identify their
    // visible edge pixels instead of assuming an uncut 267px vertical quad.
    bool horizontal = abs(max(size.x, size.y) - bodyWidth) < 0.15
        && settingsGuiPosition.x >= topLeft.x - 0.6 && settingsGuiPosition.x <= topLeft.x + bodyWidth + 0.6
        && (inScrollRange(settingsGuiPosition.y, topLeft.y + 0.5, scrollTravel)
            || inScrollRange(settingsGuiPosition.y, topLeft.y + bodyHeight - 0.5, scrollTravel));
    bool vertical = max(size.x, size.y) <= bodyHeight - 1.85
        && settingsGuiPosition.y >= topLeft.y - scrollTravel - 0.6
        && settingsGuiPosition.y <= topLeft.y + bodyHeight + 0.6
        && (abs(settingsGuiPosition.x - (topLeft.x + 0.5)) < 0.8
            || abs(settingsGuiPosition.x - (topLeft.x + bodyWidth - 0.5)) < 0.8);
    return horizontal || vertical;
}

void main() {
    vec4 color = vertexColor;
    // Reserved opt-in settings canvas and dialogue template geometries only.
    if (color.a == 0.0 || dialogFocusOutline(color, 474.0, 269.0)
        || dialogFocusOutline(color, 576.0, 188.0)) discard;
    fragColor = color * ColorModulator;
}
