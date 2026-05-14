#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2 resolution;
    float p0;
    float p1;
} pc;

const float HDRPower = 1.30;
const float radius1  = 0.793;
const float radius2  = 0.870;

void main() {
    vec2 texcoord = vUV;
    vec2 px = 1.0 / pc.resolution;
    vec3 color = texture(screenTexture, texcoord).rgb;

    vec3 bloom_sum1  = texture(screenTexture, texcoord + vec2( 1.5, -1.5) * radius1 * px).rgb;
         bloom_sum1 += texture(screenTexture, texcoord + vec2(-1.5, -1.5) * radius1 * px).rgb;
         bloom_sum1 += texture(screenTexture, texcoord + vec2( 1.5,  1.5) * radius1 * px).rgb;
         bloom_sum1 += texture(screenTexture, texcoord + vec2(-1.5,  1.5) * radius1 * px).rgb;
         bloom_sum1 += texture(screenTexture, texcoord + vec2( 0.0, -2.5) * radius1 * px).rgb;
         bloom_sum1 += texture(screenTexture, texcoord + vec2( 0.0,  2.5) * radius1 * px).rgb;
         bloom_sum1 += texture(screenTexture, texcoord + vec2(-2.5,  0.0) * radius1 * px).rgb;
         bloom_sum1 += texture(screenTexture, texcoord + vec2( 2.5,  0.0) * radius1 * px).rgb;
         bloom_sum1 *= 0.005;

    vec3 bloom_sum2  = texture(screenTexture, texcoord + vec2( 1.5, -1.5) * radius2 * px).rgb;
         bloom_sum2 += texture(screenTexture, texcoord + vec2(-1.5, -1.5) * radius2 * px).rgb;
         bloom_sum2 += texture(screenTexture, texcoord + vec2( 1.5,  1.5) * radius2 * px).rgb;
         bloom_sum2 += texture(screenTexture, texcoord + vec2(-1.5,  1.5) * radius2 * px).rgb;
         bloom_sum2 += texture(screenTexture, texcoord + vec2( 0.0, -2.5) * radius2 * px).rgb;
         bloom_sum2 += texture(screenTexture, texcoord + vec2( 0.0,  2.5) * radius2 * px).rgb;
         bloom_sum2 += texture(screenTexture, texcoord + vec2(-2.5,  0.0) * radius2 * px).rgb;
         bloom_sum2 += texture(screenTexture, texcoord + vec2( 2.5,  0.0) * radius2 * px).rgb;
         bloom_sum2 *= 0.010;

    float dist = radius2 - radius1;
    vec3 HDR = (color + (bloom_sum2 - bloom_sum1)) * dist;
    vec3 blend = HDR + color;
    color = pow(abs(blend), vec3(abs(HDRPower))) + HDR;

    outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
