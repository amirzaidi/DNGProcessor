#version 300 es

precision mediump float;

uniform sampler2D bilateral;
uniform sampler2D intermediate;
uniform sampler2D hist;

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

    // Copy chroma.
    vec3 intermediateValXyz = texelFetch(intermediate, xy, 0).xyz;
    processed.xy = intermediateValXyz.xy;

    float intermediateVal = intermediateValXyz.z;
    float bilateralVal = texelFetch(bilateral, xy, 0).x;
    float detailVal = intermediateVal - bilateralVal;

    float bg = sigmoid(bilateralVal, 0.25f);
    float zEqDiff = histEq(bg) - bg;

    // Background + Correction + Detail
    float z = bg
        + (0.5f * zEqDiff * pow(intermediateVal, 0.25f))
        + 2.f * detailVal;

    // Set new luma.
    processed.z = sigmoid(z, 0.25f);
}
