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
    vec3 src = texture(screenTexture, vUV).rgb;
    int m = int(pc.mode + 0.5);

    vec3 c = pow(src, vec3(2.2));

    float L = 17.8824   * c.r + 43.5161  * c.g + 4.11935 * c.b;
    float M = 3.45565   * c.r + 27.1554  * c.g + 3.86714 * c.b;
    float S = 0.0299566 * c.r + 0.184309 * c.g + 1.46709 * c.b;

    float dL = L;
    float dM = M;
    float dS = S;
    if (m == 1) {
        dL = 2.02344 * M - 2.52581 * S;
    } else if (m == 2) {
        dM = 0.494207 * L + 1.24827 * S;
    } else {
        dS = -0.395913 * L + 0.801109 * M;
    }

    vec3 sim;
    sim.r =  0.0809444479    * dL - 0.130504409   * dM + 0.116721066  * dS;
    sim.g = -0.0102485335    * dL + 0.0540193266  * dM - 0.113614708  * dS;
    sim.b = -0.000365296938  * dL - 0.00412161469 * dM + 0.693511405  * dS;

    vec3 err = c - sim;
    vec3 corrected = c;
    if (m == 3) {
        corrected.r += err.r + 0.7 * err.b;
        corrected.g += err.g + 0.7 * err.b;
    } else {
        corrected.g += 0.7 * err.r + err.g;
        corrected.b += 0.7 * err.r + err.b;
    }
    corrected = clamp(corrected, 0.0, 1.0);
    outColor = vec4(pow(corrected, vec3(1.0 / 2.2)), 1.0);
}
