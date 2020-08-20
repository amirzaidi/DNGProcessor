#version 300 es

precision mediump float;

uniform sampler2D bilateral;
uniform sampler2D intermediate;

uniform sampler2D hist;
uniform vec2 histOffset;
uniform float histFactor;
uniform float gamma;

uniform sampler2D noiseTex;

// Out
out vec3 processed;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    vec3 intermediateValXyz = texelFetch(intermediate, xy, 0).xyz;
    vec3 bilateralValXyz = texelFetch(bilateral, xy, 0).xyz;

    float intermediateVal = intermediateValXyz.z;
    float bilateralVal = bilateralValXyz.z;

    float z = intermediateVal;
    if (bilateralVal > 0.0001f) {
        // (Original Reflectance * Original Luminosity)
        // * (Corrected Luminosity / Original Luminosity)

        float correctLuminanceGamma = pow(bilateralVal, gamma);
        z *= correctLuminanceGamma / bilateralVal;

        float texCoord = histOffset.x + histOffset.y * bilateralVal;
        float correctLuminanceHistEq = texture(hist, vec2(texCoord, 0.5f)).x;
        z *= pow(correctLuminanceHistEq / bilateralVal, histFactor);
    }

    processed.xy = mix(intermediateValXyz.xy, bilateralValXyz.xy, 0.67f);
    processed.z = clamp(z, 0.f, 1.f);
}
