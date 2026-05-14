#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2  resolution;
    float saturation;
    float contrast;
    float sharpness;
    float mode;        // 0 = Super-Resolution, 1 = DLS
} pc;

void main() {
    float SAT   = pc.saturation > 0.0 ? pc.saturation : 1.0;
    float CON   = pc.contrast   > 0.0 ? pc.contrast   : 1.0;
    float SHARP = pc.sharpness  > 0.0 ? pc.sharpness  : 0.5;

    vec2 uv = vUV;
    vec2 res = pc.resolution.x > 0.0 ? pc.resolution : vec2(1280.0, 720.0);
    vec2 stp = 1.0 / res;

    if (pc.mode < 0.5) {
        vec3 c = texture(screenTexture, uv).rgb;
        vec3 t = texture(screenTexture, uv + vec2(0.0, -stp.y)).rgb;
        vec3 b = texture(screenTexture, uv + vec2(0.0,  stp.y)).rgb;
        vec3 l = texture(screenTexture, uv + vec2(-stp.x, 0.0)).rgb;
        vec3 r = texture(screenTexture, uv + vec2( stp.x, 0.0)).rgb;
        float cL = dot(c, vec3(0.299, 0.587, 0.114));
        float tL = dot(t, vec3(0.299, 0.587, 0.114));
        float bL = dot(b, vec3(0.299, 0.587, 0.114));
        float lL = dot(l, vec3(0.299, 0.587, 0.114));
        float rL = dot(r, vec3(0.299, 0.587, 0.114));
        float mnL = min(cL, min(tL, min(bL, min(lL, rL))));
        float mxL = max(cL, max(tL, max(bL, max(lL, rL))));
        float hitMin = mnL / max(4.0 * mxL, 0.0001);
        float hitMax = (1.0 - mxL) / min(4.0 * mnL - 4.0, -0.0001);
        float lobe = max(-hitMin, hitMax);
        lobe = clamp(lobe * SHARP, -0.1875, 0.0);
        vec3 resolved = (lobe * (t + b + l + r) + c) / (4.0 * lobe + 1.0);
        outColor = vec4(clamp(resolved, 0.0, 1.0), 1.0);
    } else {
        vec3 center = texture(screenTexture, uv).rgb;
        center = (center - 0.5) * CON + 0.5;
        float gray = dot(center, vec3(0.299, 0.587, 0.114));
        center = mix(vec3(gray), center, SAT);
        vec3 blur = (
            texture(screenTexture, uv + vec2(0.0, -stp.y)).rgb +
            texture(screenTexture, uv + vec2(0.0,  stp.y)).rgb +
            texture(screenTexture, uv + vec2(-stp.x, 0.0)).rgb +
            texture(screenTexture, uv + vec2( stp.x, 0.0)).rgb
        ) * 0.25;
        center = center + (center - blur) * SHARP;
        outColor = vec4(clamp(center, 0.0, 1.0), 1.0);
    }
}
