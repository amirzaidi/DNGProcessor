#version 300 es

precision mediump float;

uniform sampler2D highResBuf;
uniform sampler2D lowResBuf;

out vec3 result;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    vec3 highRes = texelFetch(highResBuf, xy, 0).xyz;
    vec3 lowRes = texelFetch(lowResBuf, xy, 0).xyz;

    result = highRes - lowRes;
}
