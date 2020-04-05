#version 300 es

precision mediump float;

uniform sampler2D buf;
uniform ivec2 minxy;
uniform ivec2 maxxy;

uniform float sigma;
uniform ivec2 radius;

uniform ivec2 dir;
uniform vec2 ch;

// Out
out float result;

float unscaledGaussian(float d, float s) {
    return exp(-0.5f * pow(d / s, 2.f));
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    float I = 0.f;
    float W = 0.f;

    for (int i = -radius.x; i <= radius.x; i += radius.y) {
        ivec2 xy = xyCenter + i * dir;
        if (xy.x >= minxy.x && xy.y >= minxy.y && xy.x <= maxxy.x && xy.y <= maxxy.y) {
            float z = dot(ch, texelFetch(buf, xyCenter + i * dir, 0).xz);
            float scale = unscaledGaussian(float(i), sigma);
            I += z * scale;
            W += scale;
        }
    }

    result = I / W;
}
