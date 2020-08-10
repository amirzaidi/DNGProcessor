#version 300 es

precision mediump float;

uniform sampler2D buf;
uniform ivec2 minxy;
uniform ivec2 maxxy;

uniform float sigma;
uniform ivec2 radius;

uniform ivec2 dir;

// Out
out vec3 result;

#include gaussian

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    vec3 I = vec3(0.f);
    float W = 0.f;

    for (int i = -radius.x; i <= radius.x; i += radius.y) {
        ivec2 xy = xyCenter + i * dir;
        if (xy.x >= minxy.x && xy.y >= minxy.y && xy.x <= maxxy.x && xy.y <= maxxy.y) {
            vec3 XYZ = texelFetch(buf, xyCenter + i * dir, 0).xyz;
            float scale = unscaledGaussian(float(i), sigma);
            I += XYZ * scale;
            W += scale;
        }
    }

    result = I / W;
}
