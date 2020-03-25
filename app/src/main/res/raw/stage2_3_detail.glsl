#version 300 es

precision mediump float;

uniform sampler2D bilateral;
uniform sampler2D intermediate;
uniform sampler2D hist;

uniform float histFactor;

// Out
out vec3 processed;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    vec3 intermediateValXyz = texelFetch(intermediate, xy, 0).xyz;
    vec3 bilateralValXyz = texelFetch(bilateral, xy, 0).xyz;

    float intermediateVal = intermediateValXyz.z;
    float bilateralVal = bilateralValXyz.z;

    float z = intermediateVal;
    if (bilateralVal > 0.0001f && histFactor > 0.0001f) {
        // (Original Reflectance * Original Luminosity)
        // * (Corrected Luminosity / Original Luminosity)
        float bilateralCorrect = texture(hist, vec2(bilateralVal, 0.5f)).x;
        z *= pow(bilateralCorrect / bilateralVal, histFactor);

        // Boost details
        float detailFactor = 1.5f * histFactor - 0.5f;
        if (detailFactor > 0.0001f) {
            z *= pow(intermediateVal / bilateralVal, detailFactor);
        }
    }

    // Copy chroma from background.
    processed.xy = bilateralValXyz.xy;

    // Set new luma.
    processed.z = z;
}
