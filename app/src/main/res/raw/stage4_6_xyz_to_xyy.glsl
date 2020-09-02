#version 300 es

precision mediump float;

uniform sampler2D buf;

out vec3 result;

#include xyztoxyy

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    result = XYZtoxyY(texelFetch(buf, xyCenter, 0).xyz);
}
