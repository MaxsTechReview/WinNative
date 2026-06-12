#version 450

// Motion-compensated frame synthesis.
//   mode 0 (standard):      warp prev/curr along the single backward flow, interpolate at phase t.
//   mode 1 (bidirectional): prev warps along its own forward flow; forward-backward consistency
//                           gives a geometric (dis)occlusion signal.
//   mode 2 (extrapolate):   predict phase t past curr by continuing the backward flow forward;
//                           single-image warp, flow-divergence occlusion, no added latency.
//
// Fallback policy (occLo/occHi = the Smoothness slider):
//   warps agree -> motion-compensated blend; disagree or geometric occlusion -> per-channel
//   median of the two warps and the non-warped phase blend; off-frame -> time-nearest real
//   pixel. The fallback must never collapse to one fixed endpoint or low-confidence regions
//   stop advancing between real frames.

precision mediump float;
precision highp int;

layout(location = 0) in  highp vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform mediump sampler2D prevFrame;     // frame N-1
layout(set = 0, binding = 1) uniform mediump sampler2D currFrame;     // frame N
layout(set = 0, binding = 2) uniform highp   sampler2D motionField;   // backward curr->prev, .xy half-res px
layout(set = 0, binding = 3) uniform highp   sampler2D motionFieldFwd;// forward  prev->curr, .xy half-res px

layout(push_constant) uniform PC {
    vec2  resolution;   // full-res target size (pixels)
    float phase;        // synthesis phase t in (0,1)
    float occlusionLo;  // consistency window: fully trusted at/below this delta
    float occlusionHi;  // fully snapped at/above this delta
    float mode;         // 0 standard, 1 bidirectional, 2 extrapolate
} pc;

bool offFrame(highp vec2 uv) {
    return uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0;
}

vec2 med3(vec2 a, vec2 b, vec2 c) { return max(min(a, b), min(max(a, b), c)); }
vec3 med3v(vec3 a, vec3 b, vec3 c) { return max(min(a, b), min(max(a, b), c)); }

// 3x3 median of the half-res flow field (kills block-match outliers before warping).
vec2 sampleMV(highp sampler2D field, highp vec2 uv, highp vec2 texel) {
    vec2 r0 = med3(texture(field, uv + texel * vec2(-1.0, -1.0)).xy,
                   texture(field, uv + texel * vec2( 0.0, -1.0)).xy,
                   texture(field, uv + texel * vec2( 1.0, -1.0)).xy);
    vec2 r1 = med3(texture(field, uv + texel * vec2(-1.0,  0.0)).xy,
                   texture(field, uv).xy,
                   texture(field, uv + texel * vec2( 1.0,  0.0)).xy);
    vec2 r2 = med3(texture(field, uv + texel * vec2(-1.0,  1.0)).xy,
                   texture(field, uv + texel * vec2( 0.0,  1.0)).xy,
                   texture(field, uv + texel * vec2( 1.0,  1.0)).xy);
    return med3(r0, r1, r2);
}

void main() {
    float t  = clamp(pc.phase, 0.0, 1.0);
    float lo = pc.occlusionLo > 0.0 ? pc.occlusionLo : 0.06;
    float hi = pc.occlusionHi > lo  ? pc.occlusionHi : 0.25;

    // motionField is half-res, stores displacement in half-res pixels. Normalize: mv * 2 / fullRes.
    highp vec2 norm = 2.0 / pc.resolution;

    vec2 mvB  = sampleMV(motionField, vUV, norm);   // backward curr->prev (9-tap median)
    vec2 mvBn = mvB * norm;

    vec3 cCurrFlat = texture(currFrame, vUV).rgb;
    vec3 cPrevFlat = texture(prevFrame, vUV).rgb;

    // Static guard at full resolution: a pixel whose colour is unchanged between the two real
    // frames is static (HUD, text, sync bars, unmoving background) and must never be warped. This
    // is per-pixel and exact, so it catches thin high-contrast overlays that the coarse block-match
    // static mask (motionField.z) misses next to moving content.
    float staticMask = texture(motionField, vUV).z;
    float staticPix = max(staticMask, 1.0 - smoothstep(0.02, 0.06, length(cCurrFlat - cPrevFlat)));

    if (pc.mode > 1.5) {
        // Extrapolation: out(x, N+t) = curr(x + t*mvB(x)). Linear motion only.
        highp vec2 srcPos = vUV + t * mvBn;
        vec3 cWarp = texture(currFrame, srcPos).rgb;
        // The flow at the source must agree with the flow here, else this pixel is being
        // revealed and the warp would smear the object. Motion-proportional tolerance.
        vec2 mvBsrc = sampleMV(motionField, srcPos, norm);
        vec2 dv = mvB - mvBsrc;
        float tolE = 0.01 * dot(mvB, mvB) + 0.5;
        float occ = smoothstep(tolE, 4.0 * tolE + 2.0, dot(dv, dv));
        if (offFrame(srcPos)) occ = 1.0;
        occ = max(occ, staticPix);   // static overlays / unchanged pixels stay anchored
        outColor = vec4(clamp(mix(cWarp, cCurrFlat, occ), 0.0, 1.0), 1.0);
        return;
    }

    // ---- INTERPOLATION ----
    highp vec2 currPos = vUV - (1.0 - t) * mvBn;     // curr sampled along the backward flow
    highp vec2 prevPos;
    float occGeo = 0.0;

    if (pc.mode > 0.5) {
        // Bidirectional: prev warps along its own forward flow; |mvB+mvF| ~ 0 for a coherent
        // feature. The forward-backward residual is compared against a motion-proportional
        // tolerance wide enough to ignore plain block-match search noise (~1px).
        vec2 mvF = texture(motionFieldFwd, vUV).xy;
        prevPos  = vUV - t * (mvF * norm);
        vec2 fbv = mvB + mvF;
        float tol = 0.05 * (dot(mvB, mvB) + dot(mvF, mvF)) + 2.0;
        occGeo   = smoothstep(tol, 4.0 * tol + 4.0, dot(fbv, fbv));
    } else {
        prevPos  = vUV + t * mvBn;                   // single backward flow warps both
    }

    vec3 cPrev = texture(prevFrame, prevPos).rgb;
    vec3 cCurr = texture(currFrame, currPos).rgb;

    // Seam: RGB delta between the two motion-compensated samples, scaled ~[0,1].
    float disagree = length(cPrev - cCurr) * 0.5774;
    float seam = smoothstep(lo, hi, disagree);
    float fade = max(seam, occGeo);

    vec3 warped   = mix(cPrev, cCurr, t);              // where the warps agree
    vec3 dissolve = mix(cPrevFlat, cCurrFlat, t);      // non-warped phase blend
    vec3 nearest  = (t < 0.5) ? cPrevFlat : cCurrFlat; // time-nearest real frame (sharp)
    // Per-channel median of the two one-sided warps and the dissolve, biased toward the sharp
    // nearest real frame as the seam strengthens. The median alone can settle on the dissolve (a
    // 50/50 blend = visible double-image); leaning it toward the nearest frame keeps strong seams
    // sharp instead of ghosted, while good-flow regions (fade~0) are untouched.
    vec3 robust   = mix(med3v(cPrev, cCurr, dissolve), nearest, fade);

    vec3 col = mix(warped, robust, fade);
    if (offFrame(prevPos) || offFrame(currPos)) col = nearest;
    col = mix(col, cCurrFlat, staticPix);              // static overlays / unchanged pixels: unwarped
    outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
