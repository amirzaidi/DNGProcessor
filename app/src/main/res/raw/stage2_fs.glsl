#version 300 es

precision mediump float;

uniform sampler2D intermediateBuffer;
uniform ivec2 outOffset;
uniform int samplingFactor;

// Out
out vec4 analysis;

vec3[9] load3x3(ivec2 xy) {
    vec3 outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = texelFetch(intermediateBuffer, xy + ivec2((i % 3) - 1, (i / 3) - 1), 0).xyz;
    }
    return outputArray;
}

void main() {
    ivec2 xy = samplingFactor * ivec2(gl_FragCoord.xy) + outOffset;

    // Load patch
    vec3[9] impatch = load3x3(xy);

    /**
     * STANDARD DEVIATIONS
     */
    vec3 mean;
    for (int i = 0; i < 9; i++) {
        mean += impatch[i];
    }
    mean /= 9.f;
    float chromaSigma, lumaSigma;
    for (int i = 0; i < 9; i++) {
        vec3 diff = mean - impatch[i];
        chromaSigma += diff.x * diff.x + diff.y * diff.y;
        lumaSigma += diff.z * diff.z;
    }
    chromaSigma /= 9.f;
    lumaSigma /= 9.f;

    analysis = vec4(impatch[4].z, sqrt(chromaSigma), sqrt(lumaSigma), 1.f);
}
