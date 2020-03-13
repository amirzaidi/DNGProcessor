#version 300 es

precision mediump float;

uniform sampler2D buf;

// Out
out float filtered;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float unfiltered[9];
    float tmp;
    int j;

    for (int i = 0; i < 9; i++) {
        tmp = texelFetch(buf, xy + ivec2((i % 3) - 1, (i / 3) - 1), 0).x;
        j = i;
        // Shift larger values forward, starting from the right.
        while (j > 0 && tmp < unfiltered[j - 1]) {
            unfiltered[j] = unfiltered[--j];
        }
        unfiltered[j] = tmp;
    }

    filtered = unfiltered[4];
}
