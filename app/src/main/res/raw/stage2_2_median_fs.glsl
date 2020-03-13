#version 300 es

// Blurring shader

precision mediump float;

uniform sampler2D buf;
uniform ivec2 bufSize;

// Out
out float filtered;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float unfiltered = texelFetch(buf, xy, 0).x;
    filtered = 1.f - unfiltered;
}
