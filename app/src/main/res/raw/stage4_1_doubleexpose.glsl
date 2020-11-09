#version 300 es

precision mediump float;

uniform sampler2D buf;

uniform float factor;

out float result;

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    result = min(factor * texelFetch(buf, xyCenter, 0).z, 1.f);
}
