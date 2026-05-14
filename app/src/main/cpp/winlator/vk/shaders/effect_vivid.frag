#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2  resolution;
    float saturation;
    float contrast;
    float sharpness;
    float mode;
} pc;

void main() {
    float SAT = pc.saturation > 0.0 ? pc.saturation : 1.0;
    float CON = pc.contrast > 0.0 ? pc.contrast : 1.0;
    float SHARP = pc.sharpness > 0.0 ? pc.sharpness : 0.5;

    vec2 res = pc.resolution.x > 0.0 ? pc.resolution : vec2(1280.0, 720.0);
    vec2 stp = 1.0 / res;

    vec3 center = texture(screenTexture, vUV).rgb;
    center = (center - 0.5) * CON + 0.5;
    float gray = dot(center, vec3(0.299, 0.587, 0.114));
    center = mix(vec3(gray), center, SAT);
    vec3 blur = (
        texture(screenTexture, vUV + vec2(0.0, -stp.y)).rgb +
        texture(screenTexture, vUV + vec2(0.0,  stp.y)).rgb +
        texture(screenTexture, vUV + vec2(-stp.x, 0.0)).rgb +
        texture(screenTexture, vUV + vec2( stp.x, 0.0)).rgb
    ) * 0.25;

    center = center + (center - blur) * SHARP;
    outColor = vec4(clamp(center, 0.0, 1.0), 1.0);
}
