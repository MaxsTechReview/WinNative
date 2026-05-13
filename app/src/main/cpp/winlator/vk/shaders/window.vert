#version 450

layout(location = 0) in vec2 position;
layout(location = 0) out vec2 vUV;

layout(push_constant) uniform PC {
    float xform[6];
    vec2 viewSize;
    vec4 uvRect;
} pc;

void main() {
    vUV = mix(pc.uvRect.xy, pc.uvRect.zw, position);
    vec2 t = vec2(
        pc.xform[0] * position.x + pc.xform[2] * position.y + pc.xform[4],
        pc.xform[1] * position.x + pc.xform[3] * position.y + pc.xform[5]
    );
    gl_Position = vec4(
        2.0 * t.x / pc.viewSize.x - 1.0,
        2.0 * t.y / pc.viewSize.y - 1.0,
        0.0, 1.0
    );
}
