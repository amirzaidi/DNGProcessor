#version 300 es

precision mediump float;

uniform sampler2D rawBuffer;
uniform int rawWidth;
uniform int rawHeight;

// Sensor and picture variables
uniform int cfaPattern; // The Color Filter Arrangement pattern used

// Out
out float intermediate;

int ind(int x, int y) {
    int dim = 5;
    return x + dim / 2 + (y + dim / 2) * dim;
}

float fetch(ivec2 xy, int dx, int dy) {
    return texelFetch(rawBuffer, xy + ivec2(dx, dy), 0).x;
}

float demosaicG(ivec2 xy) {
    int index = (xy.x & 1) | ((xy.y & 1) << 1);
    index |= (cfaPattern << 2);
    float p = fetch(xy, 0, 0);
    switch (index) {
        // RGR
        case 1:     //  R[G] G B
        case 4:     // [G]R  B G
        case 11:    //  G B  R[G]
        case 14:    //  B G [G]R
        // BGB
        case 2:     //  R G [G]B
        case 7:     //  G R  B[G]
        case 8:     // [G]B  R G
        case 13:    //  B[G] G R
            return p;
    }

    float l = fetch(xy, -1, 0),
        r = fetch(xy, 1, 0),
        t = fetch(xy, 0, -1),
        b = fetch(xy, 0, 1);

    // Laroche and Prescott
    float p2 = 2.f * p;

    float dxp = p2 - fetch(xy, -2, 0) - fetch(xy, 2, 0);
    float dx = abs(l - r) + abs(dxp);

    float dyp = p2 - fetch(xy, 0, -2) - fetch(xy, 0, 2);
    float dy = abs(t - b) + abs(dyp);

    // Su
    float gx = (l + r) * 0.5f + dxp * 0.25f;
    float gy = (t + b) * 0.5f + dyp * 0.25f;

    float w1 = 0.87f;
    float w2 = 0.13f;

    if (dx < dy) {
        p = w1 * gx + w2 * gy;
    } else if (dx > dy) {
        p = w1 * gy + w2 * gx;
    } else {
        p = (gx + gy) * 0.5f;
    }

    return max(p, 0.f);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int x = clamp(xy.x, 2, rawWidth - 3);
    int y = clamp(xy.y, 2, rawHeight - 3);
    intermediate = demosaicG(ivec2(x, y));
}
