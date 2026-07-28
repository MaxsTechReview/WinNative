#version 450

precision mediump float;
precision highp int;

layout(location = 0) in  highp vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform mediump sampler2D prevFrame;
layout(set = 0, binding = 1) uniform mediump sampler2D currFrame;
layout(set = 0, binding = 2) uniform highp   sampler2D motionField;
layout(set = 0, binding = 3) uniform highp   sampler2D motionFieldFwd;

layout(push_constant) uniform PC {
    vec2  resolution;
    float phase;
    float occlusionLo;
    float occlusionHi;
    float mode;
} pc;

bool offFrame(highp vec2 uv) {
    return uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0;
}

float valid(highp vec2 p) {
    return offFrame(p) ? 0.0 : 1.0;
}

#define FM_S2(a, b)          { vec2 fm_t = min(a, b); b = max(a, b); a = fm_t; }
#define FM_MN3(a, b, c)      FM_S2(a, b); FM_S2(a, c);
#define FM_MX3(a, b, c)      FM_S2(b, c); FM_S2(a, c);
#define FM_MNMX3(a, b, c)    FM_MX3(a, b, c); FM_S2(a, b);
#define FM_MNMX4(a, b, c, d) FM_S2(a, b); FM_S2(c, d); FM_S2(a, c); FM_S2(b, d);
#define FM_MNMX5(a, b, c, d, e)    FM_S2(a, b); FM_S2(c, d); FM_MN3(a, c, e); FM_MX3(b, d, e);
#define FM_MNMX6(a, b, c, d, e, f) FM_S2(a, d); FM_S2(b, e); FM_S2(c, f); FM_MN3(a, b, c); FM_MX3(d, e, f);

vec2 flowMedian(highp vec2 uv, highp vec2 n, out float spread) {
    vec2 v0 = texture(motionField, uv + vec2(-n.x, -n.y)).xy;
    vec2 v1 = texture(motionField, uv + vec2( 0.0, -n.y)).xy;
    vec2 v2 = texture(motionField, uv + vec2( n.x, -n.y)).xy;
    vec2 v3 = texture(motionField, uv + vec2(-n.x,  0.0)).xy;
    vec2 v4 = texture(motionField, uv).xy;
    vec2 v5 = texture(motionField, uv + vec2( n.x,  0.0)).xy;
    vec2 v6 = texture(motionField, uv + vec2(-n.x,  n.y)).xy;
    vec2 v7 = texture(motionField, uv + vec2( 0.0,  n.y)).xy;
    vec2 v8 = texture(motionField, uv + vec2( n.x,  n.y)).xy;
    vec2 lo = min(min(min(v0, v1), min(v2, v3)), min(min(v4, v5), min(v6, v7)));
    vec2 hi = max(max(max(v0, v1), max(v2, v3)), max(max(v4, v5), max(v6, v7)));
    spread = length(max(hi, v8) - min(lo, v8));
    FM_MNMX6(v0, v1, v2, v3, v4, v5);
    FM_MNMX5(v1, v2, v3, v4, v6);
    FM_MNMX4(v2, v3, v4, v7);
    FM_MNMX3(v3, v4, v8);
    return v4;
}

void main() {
    float t = clamp(pc.phase, 0.0, 1.0);
    float steadier = clamp(pc.occlusionLo, 0.0, 1.0);
    float tolScale = clamp(pc.occlusionHi, 0.05, 1.0) / 0.33;
    bool cnn = pc.mode >= 3.5;
    float imode = cnn ? pc.mode - 4.0 : pc.mode;

    highp vec2 texel = 1.0 / vec2(textureSize(currFrame, 0));
    highp vec2 norm  = 1.0 / vec2(textureSize(motionField, 0));
    // A single wrong flow texel drags a patch of the frame in from the wrong place and
    // reads as a blocky smear. A 3x3 median drops isolated outliers while keeping
    // motion boundaries, which a blur would round off.
    float flowSpread;
    vec2 mvB = flowMedian(vUV, norm, flowSpread);
    vec2 mvBn = mvB * norm;
    // Neighbouring flow texels disagreeing by this much means the warp is about to pull
    // the object apart. A soft region reads far better than a shredded one, so hand
    // those pixels back to the temporal blend.
    float flowOk = 1.0 - smoothstep(3.0, 10.0, flowSpread);

    vec3 cCurrFlat = texture(currFrame, vUV).rgb;
    vec3 cPrevFlat = texture(prevFrame, vUV).rgb;

    vec2 maskConf    = texture(motionField, vUV).zw;
    float staticMask = cnn ? 0.0 : maskConf.x;
    float staticPix  = max(staticMask, 1.0 - smoothstep(0.02, 0.06, length(cCurrFlat - cPrevFlat)));
    float uniq       = cnn ? 1.0 : smoothstep(0.08, 0.35, maskConf.y);

    if (imode > 1.5) {
        highp vec2 srcPos = vUV + t * mvBn;
        vec3 cWarp = texture(currFrame, srcPos).rgb;
        float v = valid(srcPos);
        cWarp = mix(cCurrFlat, cWarp, v);

        vec2 mvBsrc = texture(motionField, srcPos).xy;
        vec2 dv = mvB - mvBsrc;
        float tolE = (0.05 * dot(mvB, mvB) + 2.0) * tolScale;
        float occ  = smoothstep(tolE, 6.0 * tolE + 6.0, dot(dv, dv));
        float relTol   = 0.10 * dot(mvB, mvB) + 4.0;
        float reliable = (1.0 - smoothstep(relTol, 3.0 * relTol, dot(dv, dv))) * uniq * v;

        vec3 cPred = mix(cCurrFlat, cWarp, reliable);
        vec3 col   = mix(cWarp, cPred, occ);

        vec2 txE = texel;
        vec3 blurE = (texture(currFrame, srcPos + vec2(txE.x, 0.0)).rgb
                    + texture(currFrame, srcPos - vec2(txE.x, 0.0)).rgb
                    + texture(currFrame, srcPos + vec2(0.0, txE.y)).rgb
                    + texture(currFrame, srcPos - vec2(0.0, txE.y)).rgb) * 0.25;
        col += 0.55 * v * clamp(cWarp - blurE, -0.25, 0.25);

        float staticHold = staticMask * (1.0 - smoothstep(1.0, 4.0, dot(mvB, mvB)));
        col = mix(col, cCurrFlat, staticHold);
        outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
        return;
    }

    bool bidir = imode > 0.5;
    vec2 mvF  = bidir ? texture(motionFieldFwd, vUV).xy : -mvB;
    vec2 mvFn = mvF * norm;

    highp vec2 uvA = bidir ? (vUV - t * mvFn) : (vUV + t * mvBn);
    highp vec2 uvB = vUV - (1.0 - t) * mvBn;
    vec3 cA = texture(prevFrame, uvA).rgb;
    vec3 cB = texture(currFrame, uvB).rgb;
    float tolE = (0.05 * dot(mvB, mvB) + 2.0) * tolScale;
    float hiE  = 6.0 * tolE + 6.0;
    vec2  dA = bidir ? (mvB + texture(motionFieldFwd, vUV + mvBn).xy)
                     : (mvB - texture(motionField, uvA).xy);
    vec2  dB = bidir ? (mvF + texture(motionField, vUV + mvFn).xy)
                     : (mvB - texture(motionField, uvB).xy);
    float occA = 1.0 - smoothstep(tolE, hiE, dot(dA, dA));
    float occB = 1.0 - smoothstep(tolE, hiE, dot(dB, dB));
    float lA = (occA - 1.0) * 16.0 + (valid(uvA) - 1.0) * 24.0;
    float lB = (occB - 1.0) * 16.0 + (valid(uvB) - 1.0) * 24.0;
    float mE = max(lA, lB);
    float wA = (1.0 - t) * exp(lA - mE);
    float wB = t * exp(lB - mE);
    float wsum = wA + wB + 1e-6;
    vec3 col = (cA * wA + cB * wB) / wsum;

    // Where the warp is not trusted the output used to cross-dissolve the two real
    // frames. Wherever they differ that is a double image, and it covers a large part of
    // the frame - it is where most of the softness came from. Take the nearer warped
    // side there instead, which is sharp and still motion-compensated, and keep the
    // dissolve only where the two frames are close enough that it cannot be seen.
    vec3 xfade  = mix(cPrevFlat, cCurrFlat, t);
    float sdis  = length(cCurrFlat - cPrevFlat);
    // ...but only where the flow is coherent. Taking a single warped sample on an
    // incoherent vector puts a sharp wrong patch on screen, which is worse than the soft
    // one it replaced, so those pixels keep the dissolve.
    float sharp = smoothstep(0.04, 0.20, sdis) * flowOk;
    vec3 repeat = mix(xfade, (t < 0.5) ? cA : cB, sharp);
    col = mix(repeat, col, uniq * flowOk * (1.0 - 0.30 * steadier));

    vec2 tx = texel;
    vec3 blur = (texture(currFrame, uvB + vec2(tx.x, 0.0)).rgb
               + texture(currFrame, uvB - vec2(tx.x, 0.0)).rgb
               + texture(currFrame, uvB + vec2(0.0, tx.y)).rgb
               + texture(currFrame, uvB - vec2(0.0, tx.y)).rgb) * 0.25;
    float kdet = cnn ? (0.30 - 0.15 * steadier)
                     : (0.30 - 0.15 * steadier) * (1.0 - smoothstep(9.0, 64.0, dot(mvB, mvB)));
    col += kdet * clamp(cB - blur, -0.25, 0.25);

    col = mix(col, repeat, staticPix);
    outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
