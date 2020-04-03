#version 300 es

precision mediump float;

uniform sampler2D intermediate;

// Out
out float analysis;

vec2[9] load3x3(ivec2 xy, int n) {
    vec2 outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = texelFetch(intermediate, xy + n * ivec2((i % 3) - 1, (i / 3) - 1), 0).xy;
    }
    return outputArray;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    // Load patch
    vec2[9] impatch = load3x3(xy, 2);

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
