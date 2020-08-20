#version 300 es

#define WEIGHTS vec3(vec2(96.f), 3.f)

precision mediump float;

uniform sampler2D intermediate;
uniform ivec2 bufSize;

uniform int radius;

// Out
out vec3 result;

#include load3x3v3

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy) * 4;

    ivec2 minxy = xyCenter - 1;
    ivec2 maxxy = xyCenter + 1;

    ivec2 xyPixel;
    vec3[9] impatch;
    int i = 0;
    for (int y = minxy.y; y <= maxxy.y; y += 1) {
        for (int x = minxy.x; x <= maxxy.x; x += 1) {
            impatch[i++] = texelFetch(intermediate, clamp(ivec2(x, y), ivec2(0), bufSize - 1), 0).xyz;
        }
    }

    vec3 gradientHor = abs(impatch[4] - impatch[3]) + abs(impatch[4] - impatch[5]);
    vec3 gradientVert = abs(impatch[4] - impatch[1]) + abs(impatch[4] - impatch[7]);
    vec3 gradientNE = abs(impatch[4] - impatch[2]) + abs(impatch[4] - impatch[6]);
    vec3 gradientNW = abs(impatch[4] - impatch[0]) + abs(impatch[4] - impatch[8]);

    vec3 gradientMax = max(max(gradientHor, gradientVert), max(gradientNE, gradientNW));
    vec3 gradientMin = min(min(gradientHor, gradientVert), min(gradientNE, gradientNW));

    result = WEIGHTS * max(3.f * gradientMin - gradientMax, 0.f);
}
