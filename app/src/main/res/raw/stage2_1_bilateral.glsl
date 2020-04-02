#version 300 es

// Bilateral filter
precision mediump float;

// Use buf to blur luma while keeping chroma.
uniform sampler2D buf;
uniform ivec2 bufSize;

uniform vec2 sigma;
uniform ivec2 radius;

// Out
out vec3 result;

float unscaledGaussian(float d, float s) {
    return exp(-0.5f * pow(d / s, 2.f));
}

// Difference
float fr(float diffi) {
    //return unscaledGaussian(diffi, 0.06f);
    return unscaledGaussian(diffi, sigma.x);
}

// Distance
float gs(float diffx) {
    //return 1.f / (diffx * diffx + 1.f);
    return unscaledGaussian(diffx, sigma.y);
}

float pixDiff(vec3 pix1, vec3 pix2) {
    float z = 5.f * min(pix1.z, pix2.z);
    return length((pix2 - pix1) * vec3(z, z, 1.f));
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    vec3 XYZCenter = texelFetch(buf, xyCenter, 0).xyz;

    ivec2 minxy = max(ivec2(0, 0), xyCenter - radius.x);
    ivec2 maxxy = min(bufSize - 1, xyCenter + radius.x);

    vec3 I = vec3(0.f);
    float W = 0.f;

    for (int y = minxy.y; y <= maxxy.y; y += radius.y) {
        for (int x = minxy.x; x <= maxxy.x; x += radius.y) {
            ivec2 xyPixel = ivec2(x, y);

            vec3 XYZPixel = texelFetch(buf, xyPixel, 0).xyz;

            vec2 dxy = vec2(xyPixel - xyCenter);

            float scale = fr(pixDiff(XYZPixel, XYZCenter)) * gs(length(dxy));
            I += XYZPixel * scale;
            W += scale;
        }
    }

    if (W < 0.0001f) {
        result = XYZCenter;
    } else {
        result = I / W;
    }
}
