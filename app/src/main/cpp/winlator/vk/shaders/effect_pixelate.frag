#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float blockSize;
    float p1;
    float p2;
} pc;

void main() {
    vec2 res = pc.resolution.x > 0.0 ? pc.resolution : vec2(1280.0, 720.0);
    float bs = max(pc.blockSize, 1.0);
    vec2 grid = max(res / bs, vec2(1.0));
    vec2 uv = (floor(vUV * grid) + 0.5) / grid;
    outColor = vec4(texture(screenTexture, uv).rgb, 1.0);
}
