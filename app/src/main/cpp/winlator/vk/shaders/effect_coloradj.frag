#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float brightness;
    float contrast;
    float gamma;
} pc;

void main() {
    vec3 color = texture(screenTexture, vUV).rgb;
    vec3 adjusted = color + vec3(pc.brightness);
    adjusted = (adjusted - 0.5) * (1.0 + pc.contrast) + 0.5;
    adjusted = pow(clamp(adjusted, 0.0, 1.0), vec3(1.0 / max(pc.gamma, 0.01)));
    outColor = vec4(clamp(adjusted, 0.0, 1.0), 1.0);
}
