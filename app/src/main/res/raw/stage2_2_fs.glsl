#version 300 es

// Blurring shader

precision mediump float;

uniform sampler2D buf;
uniform ivec2 bufSize;
uniform int lod;
uniform ivec2 dir;
uniform vec2 ch;

// Out
out float blurred;

// Total size of 15
float gauss[8] = float[8](
    0.000489f,
    0.002403f,
    0.009246f,
    0.02784f,
    0.065602f,
    0.120999f,
    0.174697f,
    0.197448f
);

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec2 res;
    for (int i = 0; i < 15; i++) {
        int j = i < 8 ? i : 14 - i;
        ivec2 pos = clamp(xy + (i - 7) * dir, ivec2(1, 1), bufSize - 2);
        res += gauss[j] * texelFetch(buf, pos, lod).xz;
    }
    blurred = dot(ch, res);
}
