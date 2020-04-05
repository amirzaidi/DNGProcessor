#version 300 es

precision mediump float;

uniform sampler2D intermediate;
uniform sampler2D noiseTex;

uniform ivec2 outOffset;
uniform int samplingFactor;

// Out
out vec2 analysis;

void main() {
    ivec2 xy = samplingFactor * ivec2(gl_FragCoord.xy) + outOffset;
    analysis.x = texelFetch(noiseTex, xy, 0).x;
    analysis.y = texelFetch(intermediate, xy, 0).z;
}
