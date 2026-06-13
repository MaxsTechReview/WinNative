#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float mode;
    float p1;
    float p2;
} pc;

void main() {
    vec3 c = texture(screenTexture, vUV).rgb;
    int m = int(pc.mode + 0.5);
    mat3 sim;
    if (m == 1) {
        sim = mat3(0.567, 0.558, 0.0,
                   0.433, 0.442, 0.242,
                   0.0,   0.0,   0.758);
    } else if (m == 2) {
        sim = mat3(0.625, 0.70, 0.0,
                   0.375, 0.30, 0.30,
                   0.0,   0.0,  0.70);
    } else {
        sim = mat3(0.95, 0.0,   0.0,
                   0.05, 0.433, 0.475,
                   0.0,  0.567, 0.525);
    }
    vec3 err = c - sim * c;
    vec3 corrected = c;
    corrected.g += 0.7 * err.r + err.g;
    corrected.b += 0.7 * err.r + err.b;
    outColor = vec4(clamp(corrected, 0.0, 1.0), 1.0);
}
