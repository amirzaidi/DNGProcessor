#version 300 es

precision mediump float;

uniform sampler2D intermediate;
uniform sampler2D noiseTex;

uniform ivec2 outOffset;
uniform int samplingFactor;

// Out
out vec4 analysis;

void main() {
    ivec2 xy = samplingFactor * ivec2(gl_FragCoord.xy) + outOffset;

    float noise = texelFetch(noiseTex, xy, 0).x;
    float z = texelFetch(intermediate, xy, 0).z;
    analysis = vec4(noise, 0.f, 0.f, z);
}
