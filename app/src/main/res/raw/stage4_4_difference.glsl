#version 300 es

precision mediump float;

uniform sampler2D target;
uniform sampler2D base;

out float result;

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    result = texelFetch(target, xyCenter, 0).x - texelFetch(base, xyCenter, 0).x;
}
