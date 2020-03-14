#version 300 es

precision mediump float;

uniform sampler2D bilateral;
uniform sampler2D intermediate;
uniform sampler2D hist;

uniform float boost;

// Out
out vec3 processed;

float sigmoid(float val, float transfer) {
    if (val > transfer) {
        // This variable maps the cut off point in the linear curve to the sigmoid
        float a = log((1.f + transfer) / (1.f - transfer)) / transfer;

        // Transform val using the sigmoid curve
        val = 2.f / (1.f + exp(-a * val)) - 1.f;
    }
    return val;
}

float histEq(float inVal) {
    return texture(hist, vec2(inVal, 0.5f)).x;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    vec3 intermediateValXyz = texelFetch(intermediate, xy, 0).xyz;
    vec3 bilateralValXyz = texelFetch(bilateral, xy, 0).xyz;

    float intermediateVal = intermediateValXyz.z;
    float bilateralVal = bilateralValXyz.z;
    float detailVal = intermediateVal - bilateralVal;

    float zEqDiff = bilateralVal < 1.f
        ? histEq(bilateralVal) - bilateralVal
        : 0.f;

    // Background + Correction + Detail
    float z = bilateralVal
        + boost * (zEqDiff * pow(intermediateVal, 0.5f))
        + (1.f + boost) * detailVal;

    // Copy chroma from background.
    processed.xy = bilateralValXyz.xy;

    // Set new luma.
    processed.z = sigmoid(z, 0.25f);
}
