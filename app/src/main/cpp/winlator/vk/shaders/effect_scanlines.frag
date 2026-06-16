#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float intensity;
    float p1;
    float p2;
} pc;

void main() {
    vec2 res = pc.resolution.x > 0.0 ? pc.resolution : vec2(1280.0, 720.0);
    vec3 c = texture(screenTexture, vUV).rgb;
    float band = 0.5 + 0.5 * sin(vUV.y * res.y * 1.5708);
    c *= 1.0 - pc.intensity * (1.0 - band);
    outColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
