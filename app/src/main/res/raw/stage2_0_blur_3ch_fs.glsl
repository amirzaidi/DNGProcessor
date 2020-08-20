#version 300 es

precision mediump float;

uniform sampler2D buf;
uniform ivec2 bufSize;

uniform float sigma;
uniform int radius;
uniform ivec2 dir;

out vec3 result;

#include gaussian

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    vec3 I = vec3(0.f);
    float W = 0.f;
    vec3 xyY;
    float scale;

    for (int i = -radius; i <= radius; i++) {
        ivec2 xy = xyCenter + i * dir;
        if (xy.x >= 0 && xy.y >= 0 && xy.x < bufSize.x && xy.y < bufSize.y) {
            xyY = texelFetch(buf, xyCenter + i * dir, 0).xyz;
            scale = unscaledGaussian(float(i), sigma);
            I += xyY * scale;
            W += scale;
        }
    }

    result = I / W;
}
