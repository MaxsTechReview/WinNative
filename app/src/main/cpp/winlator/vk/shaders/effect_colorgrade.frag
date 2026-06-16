#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float saturation;
    float temperature;
    float tint;
} pc;

void main() {
    vec3 c = texture(screenTexture, vUV).rgb;
    c.r *= 1.0 + pc.temperature * 0.30;
    c.b *= 1.0 - pc.temperature * 0.30;
    c.g *= 1.0 + pc.tint * 0.20;
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(luma), c, pc.saturation);
    outColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
