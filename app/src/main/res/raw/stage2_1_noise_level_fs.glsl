#version 300 es

precision mediump float;

uniform sampler2D intermediate;

// Out
out float analysis;

#include load3x3v2

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    // Load patch
    vec2[9] impatch = load3x3(xy, 2, intermediate);

    /**
     * STANDARD DEVIATIONS
     */
    vec2 mean, sigma;
    for (int i = 0; i < 9; i++) {
        mean += impatch[i];
    }
    mean /= 9.f;
    for (int i = 0; i < 9; i++) {
        vec2 diff = mean - impatch[i];
        sigma += diff * diff;
    }

    analysis = length(sqrt(sigma / 9.f));
}
