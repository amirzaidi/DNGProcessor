#version 300 es

precision mediump float;

uniform sampler2D intermediate;

// Out
out vec3 result;

#include load3x3v3

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) * 2;

    // Load patch
    vec3[9] impatch = load3x3(xy, 2, intermediate);

    /**
     * STANDARD DEVIATIONS
     */
    vec3 mean, sigma;
    for (int i = 0; i < 9; i++) {
        mean += impatch[i];
    }
    mean /= 9.f;
    vec3 diff;
    for (int i = 0; i < 9; i++) {
        diff = mean - impatch[i];
        sigma += diff * diff;
    }

    result = sqrt(sigma / 9.f);
}
