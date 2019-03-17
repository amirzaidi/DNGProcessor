#version 300 es

// Blurring shader

precision mediump float;

uniform sampler2D sampleBuf;
uniform sampler2D blurBuf;
uniform ivec2 bufSize;
uniform ivec2 dir;
uniform vec2 ch;

const int w = 61;
const float b = 1.f;
const float maxs = 15.f;

// Out
out float blurred;

float unscaledGaussian(int dx, float s) {
    return exp(-0.5f * pow(float(dx) / s, 2.f));
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float res;

    float mean, sigma;
    for (int i = 0; i < w; i++) {
        ivec2 pos = clamp(xy + (i - w / 2) * dir, ivec2(1, 1), bufSize - 2);
        mean += dot(ch, texelFetch(sampleBuf, pos, 0).xz);
    }
    mean /= float(w);
    for (int i = 0; i < w; i++) {
        ivec2 pos = clamp(xy + (i - w / 2) * dir, ivec2(1, 1), bufSize - 2);
        float diff = mean - dot(ch, texelFetch(sampleBuf, pos, 0).xz);
        sigma += diff * diff;
    }

    float s = min(b / pow(sigma / float(w), 0.5f), maxs);
    float totalGauss;
    for (int i = 0; i < w; i++) {
        float gauss = unscaledGaussian(i - w / 2, s);
        totalGauss += gauss;

        ivec2 pos = clamp(xy + (i - w / 2) * dir, ivec2(1, 1), bufSize - 2);
        res += gauss * dot(ch, texelFetch(blurBuf, pos, 0).xz);
    }
    blurred = res / totalGauss;
}
