#version 300 es

precision mediump float;

uniform sampler2D intermediateBuffer;
uniform int intermediateWidth;
uniform int intermediateHeight;

vec3[9] load3x3(ivec2 xy, int n) {
    vec3 outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = texelFetch(intermediateBuffer, xy + n * ivec2((i % 3) - 1, (i / 3) - 1), 0).xyz;
    }
    return outputArray;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    vec3[9] impatch = load3x3(xyPos, 2);
    vec3 mid = impatch[4];

    // Take unfiltered xy and z as starting point.
    vec2 xy = mid.xy;
    float z = mid.z;

    // Calculate stddev for patch
    vec3 mean;
    for (int i = 0; i < 9; i++) {
        mean += impatch[i];
    }
    mean /= 9.f;
    vec3 sigmaLocal;
    for (int i = 0; i < 9; i++) {
        vec3 diff = mean - impatch[i];
        sigmaLocal += diff * diff;
    }
    sigmaLocal = max(sqrt(sigmaLocal / 9.f), sigma);

    vec3 minxyz = impatch[0].xyz, maxxyz = minxyz;
    for (int i = 1; i < 9; i++) {
        minxyz = min(minxyz, impatch[i]);
        maxxyz = max(maxxyz, impatch[i]);
    }
    float distxy = distance(minxyz.xy, maxxyz.xy);
    float distz = distance(minxyz.z, maxxyz.z);
}
