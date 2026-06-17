#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float p0;
    float p1;
    float p2;
} pc;

void main() {
    vec2 res = pc.resolution.x > 0.0 ? pc.resolution : vec2(1280.0, 720.0);
    vec2 texel = 1.0 / max(res, vec2(1.0));

    vec3 color = texture(screenTexture, vUV).rgb;
    vec3 shifted = color;
    shifted.r = texture(screenTexture, vUV + vec2(0.0, texel.y * 1.25)).r;
    shifted.b = texture(screenTexture, vUV - vec2(0.0, texel.y * 1.25)).b;

    float bleed = sin((vUV.x * max(res.x, 1.0) + vUV.y * 24.0) * 0.45) * 0.018;
    vec3 ntsc = clamp(mix(color, shifted + vec3(bleed), 0.65), 0.0, 1.0);
    outColor = vec4(ntsc, 1.0);
}
