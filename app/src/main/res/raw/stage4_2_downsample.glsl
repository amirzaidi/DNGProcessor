#version 300 es

precision mediump float;

uniform sampler2D buf;

out vec3 result;

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    result = texelFetch(buf, xyCenter * 2, 0).xyz;
}
