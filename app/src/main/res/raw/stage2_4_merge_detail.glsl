#version 300 es

precision mediump float;

uniform sampler2D bilateral;
uniform sampler2D intermediate;
uniform sampler2D hist;

uniform sampler2D noiseTex;

uniform float histFactor;

// Out
out vec3 processed;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    vec3 intermediateValXyz = texelFetch(intermediate, xy, 0).xyz;
    vec3 bilateralValXyz = texelFetch(bilateral, xy, 0).xyz;

    float intermediateVal = intermediateValXyz.z;
    float bilateralVal = bilateralValXyz.z;

    float noiseLevel = texelFetch(noiseTex, xy, 0).x;

    // Reduce intermediate noise using noise texture.
    intermediateVal = mix(intermediateVal, bilateralVal, min(noiseLevel * 1.5f, 1.f));

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

    // Reduce xy noise.
    noiseLevel *= 0.5f;
    if (z < noiseLevel) {
        // Shift towards D50 white
        processed.xy = mix(bilateralValXyz.xy,
            vec2(0.345703f, 0.358539f),
            1.f - z / noiseLevel);
    } else {
        // Copy chroma from background.
        processed.xy = bilateralValXyz.xy;
    }

    // Set new luma.
    processed.z = clamp(z, 0.f, 1.f);
}
