#version 450

#define CA_AMOUNT 1.0025
#define SCANLINE_INTENSITY_X 0.125
#define SCANLINE_INTENSITY_Y 0.375
#define SCANLINE_SIZE 1024.0

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float p0;
    float p1;
} pc;

void main() {
    vec4 finalColor = texture(screenTexture, vUV);
    finalColor.rgb = vec3(
        texture(screenTexture, (vUV - 0.5) * CA_AMOUNT + 0.5).r,
        finalColor.g,
        texture(screenTexture, (vUV - 0.5) / CA_AMOUNT + 0.5).b
    );
    float scanlineX = abs(sin(vUV.x * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_X);
    float scanlineY = abs(sin(vUV.y * SCANLINE_SIZE) * 0.5 * SCANLINE_INTENSITY_Y);
    outColor = vec4(mix(finalColor.rgb, vec3(0.0), scanlineX + scanlineY), finalColor.a);
}
