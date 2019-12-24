#version 300 es

// Bilateral filter

precision mediump float;

uniform sampler2D buf;
uniform ivec2 bufSize;

// Out
out vec4 result;

float unscaledGaussian(float d, float s) {
    return exp(-0.5f * pow(d / s, 2.f));
}

float fr(float diffi) {
    return unscaledGaussian(diffi, 0.06f);
}

float gs(float diffx) {
    return 1.f / (diffx * diffx + 1.f);
    //return unscaledGaussian(diffx, 0.5f);
}

float pixDiff(vec3 pix1, vec3 pix2) {
    return length(pix2 - pix1);
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    vec3 XYZCenter = texelFetch(buf, xyCenter, 0).xyz;

    int radius = 7;

    ivec2 minxy = max(ivec2(0, 0), xyCenter - radius);
    ivec2 maxxy = min(bufSize - 1, xyCenter + radius);

    vec3 I = vec3(0.f);
    float W = 0.f;

    for (int y = minxy.y; y <= maxxy.y; y++) {
        for (int x = minxy.x; x <= maxxy.x; x++) {
            ivec2 xyPixel = ivec2(x, y);
            vec3 XYZPixel = texelFetch(buf, xyPixel, 0).xyz;
            vec2 dxy = vec2(xyPixel - xyCenter);

            float scale = fr(pixDiff(XYZPixel, XYZCenter)) * gs(length(dxy));
            I += XYZPixel * scale;
            W += scale;
        }
    }

    result.xyz = I / W;
    //result.xyz = XYZCenter;
    result.z = XYZCenter.z;
}
