#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float strength;
    float p1;
    float p2;
} pc;

void main() {
    vec2 res = pc.resolution.x > 0.0 ? pc.resolution : vec2(1280.0, 720.0);
    vec2 t = 1.0 / max(res, vec2(1.0));

    vec3 c = texture(screenTexture, vUV).rgb;
    vec3 blur = (
        texture(screenTexture, vUV + vec2(0.0, -t.y)).rgb +
        texture(screenTexture, vUV + vec2(0.0,  t.y)).rgb +
        texture(screenTexture, vUV + vec2(-t.x, 0.0)).rgb +
        texture(screenTexture, vUV + vec2( t.x, 0.0)).rgb
    ) * 0.25;

    vec3 sharp = c + (c - blur) * (pc.strength * 1.5);
    outColor = vec4(clamp(sharp, 0.0, 1.0), 1.0);
}
