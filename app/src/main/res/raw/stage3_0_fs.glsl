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
    return unscaledGaussian(diffi, 0.5f);
}

float gs(float diffx) {
    return unscaledGaussian(diffx, 1.5f);
}

float dist2d(float x1, float y1, float x2, float y2) {
    float dx = x2 - x1;
    float dy = y2 - y1;
    return sqrt(dx * dx + dy * dy);
}

float dist2d(ivec2 xy1, ivec2 xy2) {
    return dist2d(float(xy1.x), float(xy1.y), float(xy2.x), float(xy2.y));
}

float pixDiff(vec3 pix1, vec3 pix2) {
    float dx = pix2.x - pix1.x;
    float dy = pix2.y - pix1.y;
    float dz = pix2.z - pix1.z;
    return sqrt(dx * dx + dy * dy + dz * dz);
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    vec3 XYZCenter = texelFetch(buf, xyCenter, 0).xyz;

    int radius = 5;

    ivec2 minxy = max(ivec2(0, 0), xyCenter - radius);
    ivec2 maxxy = min(bufSize - 1, xyCenter + radius);

    vec3 I = vec3(0.f);
    float W = 0.f;

    for (int y = minxy.y; y <= maxxy.y; y++) {
        for (int x = minxy.x; x <= maxxy.x; x++) {
            ivec2 xyPixel = ivec2(x, y);
            vec3 XYZPixel = texelFetch(buf, xyPixel, 0).xyz;

            float scale = fr(pixDiff(XYZPixel, XYZCenter)) * gs(dist2d(xyPixel, xyCenter));
            I += XYZPixel * scale;
            W += scale;
        }
    }

    result.xyz = I / W;
    result.z = XYZCenter.z;
}
