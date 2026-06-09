#version 450

// Motion-compensated frame interpolation: warps frame N-1/N along motion.comp's backward
// flow to synthesize the phase-t frame. A consistency test falls back to the nearest real
// frame where warps disagree or land off-frame (avoids smearing on HUD/text/disocclusions).

precision mediump float;
precision highp int;

layout(location = 0) in  highp vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform mediump sampler2D prevFrame;    // frame N-1
layout(set = 0, binding = 1) uniform mediump sampler2D currFrame;    // frame N
layout(set = 0, binding = 2) uniform highp   sampler2D motionField;  // rg16f half-res, curr->prev MV in half-res px

layout(push_constant) uniform PC {
    vec2  resolution;   // full-res target size (pixels)
    float phase;        // interpolation phase t in (0,1); 0.5 == single mid frame
    float occlusionLo;  // luma-consistency: fully trusted at/below this delta
    float occlusionHi;  // fully rejected at/above this delta
    float _pad;
} pc;

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

bool offFrame(highp vec2 uv) {
    return any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)));
}

void main() {
    float t  = clamp(pc.phase > 0.0 ? pc.phase : 0.5, 0.0, 1.0);
    float lo = pc.occlusionLo > 0.0 ? pc.occlusionLo : 0.06;
    float hi = pc.occlusionHi > lo  ? pc.occlusionHi : 0.25;

    // motionField is half-res and stores curr->prev displacement in half-res pixels.
    // Normalized displacement = mv_halfPx / mvSize = mv_halfPx * 2 / fullResSize.
    vec2 mvNorm = texture(motionField, vUV).xy * 2.0 / pc.resolution;

    // Linear-trajectory motion compensation. For an intermediate pixel p (== vUV)
    // with backward flow mv (curr->prev):
    //     currPos = p - (1 - t) * mv      prevPos = p + t * mv
    highp vec2 prevPos = vUV + t * mvNorm;
    highp vec2 currPos = vUV - (1.0 - t) * mvNorm;

    vec3 cPrev = texture(prevFrame, prevPos).rgb;
    vec3 cCurr = texture(currFrame, currPos).rgb;
    vec3 mc    = mix(cPrev, cCurr, t);                 // motion-compensated blend

    // Trust = how consistent the two warps are, gated by on-frame-ness.
    float disagree = abs(luma(cPrev) - luma(cCurr));
    float trust    = 1.0 - smoothstep(lo, hi, disagree);
    if (offFrame(prevPos) || offFrame(currPos)) trust = 0.0;

    // Fallback for untrusted pixels: nearest real frame, unwarped (no smear).
    vec3 nearest = (t < 0.5) ? texture(prevFrame, vUV).rgb : texture(currFrame, vUV).rgb;

    outColor = vec4(clamp(mix(nearest, mc, trust), 0.0, 1.0), 1.0);
}
