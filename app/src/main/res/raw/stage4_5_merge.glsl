#version 300 es

#define TARGET_Z 0.4f
#define GAUSS_Z 0.7f

precision mediump float;

uniform bool useUpscaled;
uniform sampler2D upscaled;

// Weighting is done using these.
uniform sampler2D gaussUnder;
uniform sampler2D gaussOver;

// Blending is done using these.
uniform sampler2D blendUnder;
uniform sampler2D blendOver;

uniform int level;

out float result;

#include gaussian

float compress(float z, int lvl) {
    return z / (sqrt(sqrt(float(11 - lvl) * abs(z))) + 1.f);
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    // If this is the lowest layer, start with zero.
    float base = useUpscaled
        ? texelFetch(upscaled, xyCenter, 0).x
        : 0.f;

    // How are we going to blend these two?
    float blendUnderVal = texelFetch(blendUnder, xyCenter, 0).x;
    float blendOverVal = texelFetch(blendOver, xyCenter, 0).x;

    // Look at result to compute weights.
    float gaussUnderVal = texelFetch(gaussUnder, xyCenter, 0).x;
    float gaussOverVal = texelFetch(gaussOver, xyCenter, 0).x;
    float gaussUnderValDev = sqrt(unscaledGaussian(gaussUnderVal - TARGET_Z, GAUSS_Z));
    float gaussOverValDev = sqrt(unscaledGaussian(gaussOverVal - TARGET_Z, GAUSS_Z));

    float blend = gaussOverValDev / (gaussUnderValDev + gaussOverValDev); // [0, 1]
    result = base + mix(blendUnderVal, blendOverVal, blend);
}
