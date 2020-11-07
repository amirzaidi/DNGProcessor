#version 300 es

precision mediump float;

uniform sampler2D buf;
uniform ivec2 maxxy;

out vec3 result;

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    result = texelFetch(buf, min(xyCenter * 2, maxxy), 0).xyz;
}
