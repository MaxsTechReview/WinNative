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
layout(set = 0, binding = 2) uniform highp   sampler2D motionField;  // rgba16f half-res (.xy), curr->prev MV in half-res px

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

vec2 med3(vec2 a, vec2 b, vec2 c) { return max(min(a, b), min(max(a, b), c)); }

vec2 sampleMV(highp vec2 uv, highp vec2 texel) {
    vec2 r0 = med3(texture(motionField, uv + texel * vec2(-1.0, -1.0)).xy,
                   texture(motionField, uv + texel * vec2( 0.0, -1.0)).xy,
                   texture(motionField, uv + texel * vec2( 1.0, -1.0)).xy);
    vec2 r1 = med3(texture(motionField, uv + texel * vec2(-1.0,  0.0)).xy,
                   texture(motionField, uv).xy,
                   texture(motionField, uv + texel * vec2( 1.0,  0.0)).xy);
    vec2 r2 = med3(texture(motionField, uv + texel * vec2(-1.0,  1.0)).xy,
                   texture(motionField, uv + texel * vec2( 0.0,  1.0)).xy,
                   texture(motionField, uv + texel * vec2( 1.0,  1.0)).xy);
    return med3(r0, r1, r2);
}

void main() {
    float t  = clamp(pc.phase > 0.0 ? pc.phase : 0.5, 0.0, 1.0);
    float lo = pc.occlusionLo > 0.0 ? pc.occlusionLo : 0.06;
    float hi = pc.occlusionHi > lo  ? pc.occlusionHi : 0.25;

    // motionField is half-res and stores curr->prev displacement in half-res pixels.
    // Normalized displacement = mv_halfPx / mvSize = mv_halfPx * 2 / fullResSize.
    highp vec2 mvTexel = 2.0 / pc.resolution;
    vec2 mvNorm = sampleMV(vUV, mvTexel) * 2.0 / pc.resolution;

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
    bool  off      = offFrame(prevPos) || offFrame(currPos);
    float trust    = off ? 0.0 : 1.0 - smoothstep(lo, hi, disagree);

    // Fallback for untrusted pixels. Freezing them at the unwarped prev/curr frame (alpha 0/1)
    // made interpolated frames land early: untrusted regions contributed zero motion, so the
    // measured placement was ~trust*t instead of t (which is why the Smoothness/trust slider
    // visibly shifted it). A phase-t crossfade keeps untrusted pixels advancing to ~alpha=t —
    // static UI is identical in both frames so it stays ghost-free, and only genuine disocclusions
    // pick up a faint blend instead of a hard catch-up step. Hard off-frame samples still snap to
    // the nearest real frame so border disocclusions don't warp in out-of-image garbage.
    vec3 cPrevFlat = texture(prevFrame, vUV).rgb;
    vec3 cCurrFlat = texture(currFrame, vUV).rgb;
    vec3 fallback  = off ? ((t < 0.5) ? cPrevFlat : cCurrFlat) : mix(cPrevFlat, cCurrFlat, t);

    outColor = vec4(clamp(mix(fallback, mc, trust), 0.0, 1.0), 1.0);
}
