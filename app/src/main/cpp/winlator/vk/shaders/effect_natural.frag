#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float p0;
    float p1;
} pc;

const mat3 RGBtoYIQ = mat3(0.299, 0.596, 0.212,
                           0.587,-0.275,-0.523,
                           0.114,-0.321, 0.311);
const mat3 YIQtoRGB = mat3(1.0, 1.0, 1.0,
                           0.95568806,-0.27158179,-1.10817732,
                           0.61985809,-0.64687381, 1.70506455);
const vec3 val00 = vec3(1.2, 1.2, 1.2);

void main() {
    vec3 c0 = texture(screenTexture, vUV).rgb;
    vec3 t0 = c0 * RGBtoYIQ;
    t0 = vec3(pow(t0.r, 1.12), t0.gb * val00.gb);
    vec3 cFinal = t0 * YIQtoRGB;
    outColor = vec4(cFinal, 1.0);
}
