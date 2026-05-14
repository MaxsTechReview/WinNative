#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D srcTexture;
layout(push_constant) uniform PC {
    float xform[6];
    vec2 viewSize;
    vec4 uvRect;
    int swapRB;
} pc;

void main() {
    vec3 color = texture(srcTexture, vUV).rgb;
    if (pc.swapRB != 0) {
        color = color.bgr;
    }
    outColor = vec4(color, 1.0);
}
