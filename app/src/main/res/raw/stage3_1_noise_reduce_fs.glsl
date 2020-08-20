#version 300 es

precision mediump float;

uniform sampler2D inBuffer;
uniform sampler2D noiseTex;
uniform ivec2 bufSize;

uniform ivec2 radius;
uniform vec2 sigma;
uniform float blendY;

out vec3 result;

#include gaussian

// Difference
vec3 fr(vec3 diffi, vec3 s) {
    return unscaledGaussian(abs(diffi), sigma.y * s);
}

// Distance
float gs(ivec2 diffxy) {
    return unscaledGaussian(length(vec2(diffxy.x, diffxy.y)), sigma.x);
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    vec3 XYZCenter = texelFetch(inBuffer, xyCenter, 0).xyz;
    vec3 noiseLevel = texelFetch(noiseTex, xyCenter / 4, 0).xyz;

    ivec2 minxy = max(ivec2(0, 0), xyCenter - radius.x);
    ivec2 maxxy = min(bufSize - 1, xyCenter + radius.x);

    vec3 I = vec3(0.f);
    float W = 0.f;

    ivec2 xyPixel;
    vec3 XYZPixel, XYZScale;
    float XYZScalef;
    for (int y = minxy.y; y <= maxxy.y; y += radius.y) {
        for (int x = minxy.x; x <= maxxy.x; x += radius.y) {
            xyPixel = ivec2(x, y);
            XYZPixel = texelFetch(inBuffer, xyPixel, 0).xyz;

            XYZScale = fr(XYZPixel - XYZCenter, noiseLevel) * gs(xyPixel - xyCenter);
            XYZScalef = length(XYZScale);
            I += XYZPixel * XYZScalef;
            W += XYZScalef;
        }
    }

    vec3 tmp;
    if (W < 0.0001f) {
        tmp = XYZCenter;
    } else {
        tmp = I / W;
        tmp.z = mix(tmp.z, XYZCenter.z, blendY);
    }

    // Desaturate noisy patches.
    tmp.xy = mix(tmp.xy, vec2(0.345703f, 0.358539f), min(0.02f * length(noiseLevel.xy) - 0.01f, 0.25f));
    result = tmp;
}
