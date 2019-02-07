#version 300 es

precision mediump float;

uniform sampler2D intermediateBuffer;
uniform ivec2 outOffset;
uniform int samplingFactor;

// Out
out vec4 analysis;

vec3[9] load3x3(ivec2 xy) {
    vec3 outputArray[9];

    outputArray[0] = texelFetch(intermediateBuffer, xy + ivec2(-1, -1), 0).xyz;
    outputArray[1] = texelFetch(intermediateBuffer, xy + ivec2(0, -1), 0).xyz;
    outputArray[2] = texelFetch(intermediateBuffer, xy + ivec2(1, -1), 0).xyz;
    outputArray[3] = texelFetch(intermediateBuffer, xy + ivec2(-1, 0), 0).xyz;
    outputArray[4] = texelFetch(intermediateBuffer, xy, 0).xyz;
    outputArray[5] = texelFetch(intermediateBuffer, xy + ivec2(1, 0), 0).xyz;
    outputArray[6] = texelFetch(intermediateBuffer, xy + ivec2(-1, 1), 0).xyz;
    outputArray[7] = texelFetch(intermediateBuffer, xy + ivec2(0, 1), 0).xyz;
    outputArray[8] = texelFetch(intermediateBuffer, xy + ivec2(1, 1), 0).xyz;

    return outputArray;
}

void main() {
    ivec2 xy = samplingFactor * ivec2(gl_FragCoord.xy) + outOffset;

    // Load patch
    vec3[9] impatch = load3x3(xy);

    // Calculate values
    float z = impatch[4].z;
    float lumaNoise = 0.1f;
    float chromaNoise1 = 0.2f;
    float chromaNoise2 = 0.3f;

    analysis = vec4(z, lumaNoise, chromaNoise1, chromaNoise2);
}
