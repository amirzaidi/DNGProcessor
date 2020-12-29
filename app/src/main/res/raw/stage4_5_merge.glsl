#version 300 es

#define TARGET_Z 0.5f
#define GAUSS_Z 0.3f

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
#include sigmoid
#include gamma

// From hdr-plus repo.
float dist(float z) {
    return unscaledGaussian(z - TARGET_Z, GAUSS_Z);
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

    float gaussUnderValDev = dist(gaussUnderVal);
    float gaussOverValDev = dist(gaussOverVal);

    float blend = gaussOverValDev / (gaussUnderValDev + gaussOverValDev); // [0, 1]
    float blendVal = mix(blendUnderVal, blendOverVal, blend);

    if (level == 0) {
        //blendVal *= 2.5f;
    } else if (level == 1) {
        //blendVal *= 1.5f;
    } else {
        //blendVal *= max(1.f, 1.22f - 0.022f * float(level));
    }

    float res = base + blendVal;
    if (level == 0) {
        res = gammaDecode(res);
    }
    result = res;
}
