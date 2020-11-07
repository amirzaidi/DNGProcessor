#version 300 es

precision mediump float;

uniform sampler2D buf;
uniform ivec2 bufEdge;
uniform float blendY;
uniform vec2 sigma;

out vec3 result;

#include gaussian

// Difference
vec3 fr(vec3 diffi) {
    return unscaledGaussian(abs(diffi), sigma.y);
}

// Distance
float gs(ivec2 diffxy) {
    return unscaledGaussian(length(vec2(diffxy.x, diffxy.y)), sigma.x);
}

vec3[9] load3x3(ivec2 xy) {
    vec3 outputArray[9];
    ivec2 xyPx;
    for (int i = 0; i < 9; i++) {
        xyPx = xy + 2 * ivec2((i % 3) - 1, (i / 3) - 1);
        xyPx = clamp(xyPx, ivec2(0), bufEdge);
        outputArray[i] = texelFetch(buf, xyPx, 0).xyz;
    }
    return outputArray;
}

void main() {
    ivec2 xyPos = ivec2(gl_FragCoord.xy);

    vec3[9] impatch = load3x3(xyPos);

    vec3 I = vec3(0.f);
    float W = 0.f;

    ivec2 xyPixelDiff;
    vec3 XYZPixel, XYZScale;
    float XYZScalef;
    for (int i = 0; i < 9; i++) {
        xyPixelDiff.x = (i % 3) - 1;
        xyPixelDiff.y = (i / 3) - 1;
        XYZPixel = impatch[i];
        XYZScale = fr(XYZPixel - impatch[4]) * gs(xyPixelDiff);
        XYZScalef = length(XYZScale);
        I += XYZPixel * XYZScalef;
        W += XYZScalef;
    }

    vec3 tmp;
    if (W < 0.0001f) {
        tmp = impatch[4];
    } else {
        tmp = I / W;
        tmp.z = mix(tmp.z, impatch[4].z, blendY);
    }

    result = tmp;
}
