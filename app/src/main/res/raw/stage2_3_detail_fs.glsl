#version 300 es

precision mediump float;

uniform sampler2D bilateral;
uniform sampler2D intermediate;

// Out
out float detail;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    detail = texelFetch(intermediate, xy, 0).z - texelFetch(bilateral, xy, 0).x;
}
