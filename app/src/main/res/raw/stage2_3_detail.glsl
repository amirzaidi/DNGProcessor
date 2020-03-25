#version 300 es

precision mediump float;

uniform sampler2D bilateral;
uniform sampler2D intermediate;
uniform sampler2D hist;

uniform vec3 detail;

// Out
out vec3 processed;

float histEq(float inVal) {
    return texture(hist, vec2(inVal, 0.5f)).x;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    vec3 intermediateValXyz = texelFetch(intermediate, xy, 0).xyz;
    vec3 bilateralValXyz = texelFetch(bilateral, xy, 0).xyz;

    float intermediateVal = intermediateValXyz.z;
    float bilateralVal = bilateralValXyz.z;
    float detailVal = intermediateVal / max(0.0001f, bilateralVal);

    // Corrected Background * Detail
    float strength = clamp(intermediateVal * detail.x, 0.f, 1.f);
    float z = mix(bilateralVal, histEq(bilateralVal), strength);
    if (detailVal > 0.0001f) {
        detailVal = pow(detailVal, detail.y + detail.z * strength);
    }
    z *= detailVal;

    // Copy chroma from background.
    processed.xy = bilateralValXyz.xy;

    // Set new luma.
    processed.z = z;
}
