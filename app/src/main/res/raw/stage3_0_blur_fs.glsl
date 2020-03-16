#version 300 es

precision mediump float;

// Use buf to blur luma while keeping chroma.
uniform sampler2D buf;
uniform ivec2 minxy;
uniform ivec2 maxxy;

uniform float sigma;
uniform int radius;

uniform ivec2 dir;
uniform vec2 ch;

// Out
out float result;

float unscaledGaussian(float d, float s) {
    return exp(-0.5f * pow(d / s, 2.f));
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    float I = 0.f;
    float W = 0.f;

    for (int i = -radius; i <= radius; i++) {
        float z = dot(ch, texelFetch(buf, clamp(xyCenter + i * dir, minxy, maxxy), 0).xz);
        float scale = unscaledGaussian(float(i), sigma);
        I += z * scale;
        W += scale;
    }

    result = I / W;
}
