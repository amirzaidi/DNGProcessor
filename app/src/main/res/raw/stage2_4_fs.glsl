#version 300 es

// Blurring shader

precision mediump float;

uniform sampler2D buf;
uniform ivec2 bufSize;
uniform int mode;
uniform ivec2 dir;

const float s = 3.f;
const int w = 61;
const int j = 16;

// Out
out vec3 result;

float unscaledGaussian(int dx, float s) {
    return exp(-0.5f * pow(float(dx) / s, 2.f));
}

ivec2 clamppos(ivec2 pos) {
    return clamp(pos, ivec2(4, 4), bufSize - 5);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    float totalGauss;
    vec3 distribution;
    for (int i = 0; i < w; i++) {
        ivec2 pos = clamppos(xy + (i - w / 2) * dir * j);
        float gauss = unscaledGaussian(i - w / 2, s);
        totalGauss += gauss;
        vec3 v = texelFetch(buf, pos, 0).xyz;
        distribution += gauss * v;
    }
    result = distribution / totalGauss;
}
