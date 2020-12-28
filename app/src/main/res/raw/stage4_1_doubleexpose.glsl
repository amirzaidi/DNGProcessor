#version 300 es

precision mediump float;

uniform sampler2D buf;

uniform float factor;

out float result;

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    float x = clamp(factor * texelFetch(buf, xyCenter, 0).z, 0.f, 1.f);
    result = sqrt(x); // Curve to be closer to human vision.
}
