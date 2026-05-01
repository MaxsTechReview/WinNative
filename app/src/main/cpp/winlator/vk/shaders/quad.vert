#version 450

layout(location = 0) out vec2 vUV;

// Full-screen triangle: covers the viewport with a single triangle drawn from 3 vertices.
// gl_VertexIndex 0 -> (-1,-1), 1 -> (3,-1), 2 -> (-1,3); UV mirrors clip-space [0..1] within visible quad.
void main() {
    vUV = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(vUV * 2.0 - 1.0, 0.0, 1.0);
}
