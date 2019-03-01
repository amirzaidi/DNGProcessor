#version 300 es

precision mediump float;

uniform sampler2D rawBuffer;
uniform int rawWidth;
uniform int rawHeight;

// Sensor and picture variables
uniform uint cfaPattern; // The Color Filter Arrangement pattern used

// Out
out float intermediate;

float[25] load5x5(int x, int y) {
    float outputArray[25];
    for (int i = 0; i < 25; i++) {
        outputArray[i] = float(texelFetch(rawBuffer, ivec2(x + (i % 5) - 2, y + (i / 5) - 2), 0).x);
    }
    return outputArray;
}

// Apply bilinear-interpolation to demosaic
float demosaicg(int x, int y, float[25] inputArray) {
    uint index = uint((x & 1) | ((y & 1) << 1));
    index |= (cfaPattern << 2);
    int pxType = -1;
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
            return inputArray[12];
    }

    float p = inputArray[12];

    float dxp = p * 2.f - inputArray[10] - inputArray[14];
    float dx = abs(inputArray[11] - inputArray[13]) + abs(dxp);
    float gx = (inputArray[11] + inputArray[13]) * 0.5f + dxp * 0.25f;

    float dyp = p * 2.f - inputArray[2] - inputArray[22];
    float dy = abs(inputArray[7] - inputArray[17]) + abs(dyp);
    float gy = (inputArray[7] + inputArray[17]) * 0.5f + dyp * 0.25f;

    float w = 0.5f, w1 = 0.87f;
    if (dx < dy) {
        w = w1;
    } else if (dx > dy) {
        w = 1.f - w1;
    }

    return gx * w + gy * (1.f - w);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int x = clamp(xy.x, 2, rawWidth - 3);
    int y = clamp(xy.y, 2, rawHeight - 3);

    float[25] inoutPatch = load5x5(x, y);
    intermediate = demosaicg(x, y, inoutPatch);
}
