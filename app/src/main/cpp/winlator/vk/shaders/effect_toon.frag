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
    vec3 c = texture(screenTexture, vUV).rgb;
    float levels = 6.0;
    vec3 toon = floor(clamp(c, 0.0, 1.0) * levels + 0.5) / levels;
    outColor = vec4(toon, 1.0);
}
