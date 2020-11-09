#version 300 es

precision mediump float;

uniform sampler2D bufChroma;
uniform sampler2D bufLuma;

out vec3 result;

void main() {
    ivec2 xyPos = ivec2(gl_FragCoord.xy);

    result.xy = texelFetch(bufChroma, xyPos, 0).xy;
    result.z = texelFetch(bufLuma, xyPos, 0).x;
}
