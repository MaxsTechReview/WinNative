#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D srcTexture;
layout(push_constant) uniform PC {
    float xform[6];
    vec2 viewSize;
    vec4 uvRect;
    int swapRB;
} pc;

void main() {
    // Vulkan image formats expose logical R/G/B components to the shader.
    // Do not infer a channel swap from the underlying BGRA/RGBA storage format.
    // Only perform R/B swapping when the user explicitly enabled swapRB.
    vec3 color = texture(srcTexture, vUV).rgb;
    if (pc.swapRB != 0) {
        color = color.bgr;
    }
    outColor = vec4(color, 1.0);
}
