#version 300 es

precision mediump float;

uniform sampler2D intermediateBuffer;

// Out
out vec4 intermediate;

vec3[4] load2x2(ivec2 xy) {
    vec3 outputArray[4];
    for (int i = 0; i < 4; i++) {
        outputArray[i] = texelFetch(intermediateBuffer, xy + ivec2(i % 2, i / 2), 0).xyz;
    }
    return outputArray;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) * 2;

    // Load patch
    vec3[4] impatch = load2x2(xy);

    vec3 downscaled = (impatch[0] + impatch[1] + impatch[2] + impatch[3]) * 0.25f;

    intermediate = vec4(downscaled, 1.f);
}
