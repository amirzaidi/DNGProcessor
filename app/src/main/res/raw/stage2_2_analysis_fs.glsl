#version 300 es

precision mediump float;

uniform sampler2D intermediate;
uniform ivec2 outOffset;
uniform int samplingFactor;

// Out
out vec4 analysis;

vec3[9] load3x3(ivec2 xy, int n) {
    vec3 outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = texelFetch(intermediate, xy + n * ivec2((i % 3) - 1, (i / 3) - 1), 0).xyz;
    }
    return outputArray;
}

void main() {
    ivec2 xy = samplingFactor * ivec2(gl_FragCoord.xy) + outOffset;

    // Load patch
    vec3[9] impatch = load3x3(xy, 2);

    /**
     * STANDARD DEVIATIONS
     */
    vec3 mean, sigma;
    for (int i = 0; i < 9; i++) {
        mean += impatch[i];
    }
    mean /= 9.f;
    for (int i = 0; i < 9; i++) {
        vec3 diff = mean - impatch[i];
        sigma += diff * diff;
    }

    float z = texelFetch(intermediate, xy, 0).z;
    analysis = vec4(sqrt(sigma / 9.f), z);
}
