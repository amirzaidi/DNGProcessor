#version 300 es

// Bilateral filter
precision mediump float;

// Use buf to blur luma while keeping chroma.
uniform sampler2D buf;
uniform ivec2 bufSize;

uniform vec2 sigma;
uniform ivec2 radius;

// Out
out float result;

#include gaussian

// Difference
float fr(float diffi) {
    return unscaledGaussian(diffi, sigma.x);
}

// Distance
float gs(float diffx) {
    //return 1.f / (diffx * diffx + 1.f);
    return unscaledGaussian(diffx, sigma.y);
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    float valCenter = texelFetch(buf, xyCenter, 0).x;

    ivec2 minxy = max(ivec2(0, 0), xyCenter - radius.x);
    ivec2 maxxy = min(bufSize - 1, xyCenter + radius.x);

    float I = 0.f;
    float W = 0.f;

    for (int y = minxy.y; y <= maxxy.y; y += radius.y) {
        for (int x = minxy.x; x <= maxxy.x; x += radius.y) {
            ivec2 xyPixel = ivec2(x, y);

            float valPixel = texelFetch(buf, xyPixel, 0).x;

            vec2 dxy = vec2(xyPixel - xyCenter);

            float scale = fr(abs(valPixel - valCenter)) * gs(length(dxy));
            I += valPixel * scale;
            W += scale;
        }
    }

    if (W < 0.0001f) {
        result = valCenter;
    } else {
        result = I / W;
    }
}
