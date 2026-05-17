#version 450

// Snapdragon Game Super Resolution 1 spatial upscale pass.
//
// Adapted for WinNative's Vulkan compositor from Qualcomm's SGSR v1 mobile
// fragment shader.
// Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

// Keep SGSR math mediump, but texel-space coordinates highp.
precision mediump float;
precision highp int;

layout(location = 0) in highp vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform mediump sampler2D screenTexture;

layout(push_constant) uniform PC {
    vec2  resolution;
    float saturation;
    float contrast;
    float sharpness;
    float mode;
} pc;

const int OPERATION_MODE = 1; // RGBA mode uses green as the luma proxy.
const float EDGE_THRESHOLD = 8.0 / 255.0;

float fastLanczos2(float x) {
    float wA = x - 4.0;
    float wB = x * wA - wA;
    wA *= wA;
    return wB * wA;
}

vec2 weightY(float dx, float dy, float c, float std) {
    float x = (dx * dx + dy * dy) * 0.55 + clamp(abs(c) * std, 0.0, 1.0);
    float w = fastLanczos2(x);
    return vec2(w, w * c);
}

void main() {
    highp vec2 inputSize = vec2(textureSize(screenTexture, 0));
    vec4 color = vec4(textureLod(screenTexture, vUV, 0.0).rgb, 1.0);

    if (inputSize.x < 2.0 || inputSize.y < 2.0) {
        outColor = color;
        return;
    }

    highp vec4 viewportInfo = vec4(1.0 / inputSize, inputSize);
    highp vec2 imgCoord = vUV * viewportInfo.zw + vec2(-0.5, 0.5);
    highp vec2 imgCoordPixel = floor(imgCoord);
    highp vec2 coord = imgCoordPixel * viewportInfo.xy;
    vec2 pl = imgCoord - imgCoordPixel;

    vec4 left = textureGather(screenTexture, coord, OPERATION_MODE);
    float centerY = color[OPERATION_MODE];
    float edgeVote = abs(left.z - left.y) + abs(centerY - left.y) + abs(centerY - left.z);

    if (edgeVote > EDGE_THRESHOLD) {
        coord.x += viewportInfo.x;
        vec4 right = textureGather(screenTexture, coord + vec2(viewportInfo.x, 0.0),
                                   OPERATION_MODE);
        vec4 upDown;
        upDown.xy = textureGather(screenTexture, coord + vec2(0.0, -viewportInfo.y),
                                  OPERATION_MODE).wz;
        upDown.zw = textureGather(screenTexture, coord + vec2(0.0, viewportInfo.y),
                                  OPERATION_MODE).yx;

        float mean = (left.y + left.z + right.x + right.w) * 0.25;
        left -= vec4(mean);
        right -= vec4(mean);
        upDown -= vec4(mean);
        color.w = centerY - mean;

        float sum =
            abs(left.x) + abs(left.y) + abs(left.z) + abs(left.w) +
            abs(right.x) + abs(right.y) + abs(right.z) + abs(right.w) +
            abs(upDown.x) + abs(upDown.y) + abs(upDown.z) + abs(upDown.w);
        float std = 2.181818 / max(sum, 1.0e-6);

        vec2 aWY = weightY(pl.x,       pl.y + 1.0,  upDown.x, std);
        aWY += weightY(pl.x - 1.0, pl.y + 1.0,  upDown.y, std);
        aWY += weightY(pl.x - 1.0, pl.y - 2.0,  upDown.z, std);
        aWY += weightY(pl.x,       pl.y - 2.0,  upDown.w, std);
        aWY += weightY(pl.x + 1.0, pl.y - 1.0,  left.x, std);
        aWY += weightY(pl.x,       pl.y - 1.0,  left.y, std);
        aWY += weightY(pl.x,       pl.y,        left.z, std);
        aWY += weightY(pl.x + 1.0, pl.y,        left.w, std);
        aWY += weightY(pl.x - 1.0, pl.y - 1.0,  right.x, std);
        aWY += weightY(pl.x - 2.0, pl.y - 1.0,  right.y, std);
        aWY += weightY(pl.x - 2.0, pl.y,        right.z, std);
        aWY += weightY(pl.x - 1.0, pl.y,        right.w, std);

        float finalY = aWY.y / max(aWY.x, 1.0e-6);
        float maxY = max(max(left.y, left.z), max(right.x, right.w));
        float minY = min(min(left.y, left.z), min(right.x, right.w));
        float edgeSharpness = mix(1.0, 2.0, clamp(pc.sharpness, 0.0, 1.0));
        finalY = clamp(edgeSharpness * finalY, minY, maxY);

        float deltaY = clamp(finalY - color.w, -23.0 / 255.0, 23.0 / 255.0);
        color.rgb = clamp(color.rgb + vec3(deltaY), 0.0, 1.0);
    }

    outColor = vec4(color.rgb, 1.0);
}
