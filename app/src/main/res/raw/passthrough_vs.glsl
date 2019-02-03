#version 300 es

precision mediump float;

in vec4 vPosition;

void main() {
    // Forward position to fragment shader
    gl_Position = vPosition;
}
