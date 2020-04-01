#version 300 es

precision mediump float;
precision mediump usampler2D;

uniform usampler2D rawBuffer;
uniform int rawWidth;
uniform int rawHeight;

uniform sampler2D gainMap;
uniform usampler2D hotPixels;
uniform ivec2 hotPixelsSize;

// Sensor and picture variables
uniform int cfaPattern; // The Color Filter Arrangement pattern used
uniform vec4 blackLevel; // Blacklevel to subtract for each channel, given in CFA order
uniform float whiteLevel; // Whitelevel of sensor

// Out
out float intermediate;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    float v;
    int pxInfo = int(texelFetch(hotPixels, xy % hotPixelsSize, 0).x);
    if (pxInfo == 0) {
        v = float(texelFetch(rawBuffer, xy, 0).x);
    } else {
        uint vx;
        int c;
        if ((pxInfo & 1) > 0) {
            // HORIZONTAL INTERPOLATE
            for (int j = -2; j <= 2; j += 4) {
                vx += texelFetch(rawBuffer, xy + ivec2(j, 0), 0).x;
            }
            c += 2;
        }
        if ((pxInfo & 2) > 0) {
            // VERTICAL INTERPOLATE
            for (int j = -2; j <= 2; j += 4) {
                vx += texelFetch(rawBuffer, xy + ivec2(0, j), 0).x;
            }
            c += 2;
        }
        if ((pxInfo & 4) > 0) {
            // CROSS INTERPOLATE
            for (int j = 0; j < 4; j++) {
                vx += texelFetch(rawBuffer, xy + ivec2(2 * (j % 4) - 1, 2 * (j / 4) - 1), 0).x;
            }
            c += 4;
        }
        v = float(vx) / float(c);
    }

    vec2 xyInterp = vec2(float(xy.x) / float(rawWidth), float(xy.y) / float(rawHeight));
    vec4 gains = texture(gainMap, xyInterp);
    int index = (xy.x & 1) | ((xy.y & 1) << 1);  // bits [0,1] are blacklevel offset
    //index |= (cfaPattern << 2);
    float bl = 0.f;
    float g = 1.f;
    switch (index) {
        // RGGB
        case 0: bl = blackLevel.x; g = gains.x; break;
        case 1: bl = blackLevel.y; g = gains.y; break;
        case 2: bl = blackLevel.z; g = gains.z; break;
        case 3: bl = blackLevel.w; g = gains.w; break;
        /*
        // GRBG
        case 4: bl = blackLevel.x; g = gains.y; break;
        case 5: bl = blackLevel.y; g = gains.x; break;
        case 6: bl = blackLevel.z; g = gains.w; break;
        case 7: bl = blackLevel.w; g = gains.z; break;
        // GBRG
        case 8: bl = blackLevel.x; g = gains.y; break;
        case 9: bl = blackLevel.y; g = gains.w; break;
        case 10: bl = blackLevel.z; g = gains.x; break;
        case 11: bl = blackLevel.w; g = gains.z; break;
        // BGGR
        case 12: bl = blackLevel.x; g = gains.w; break;
        case 13: bl = blackLevel.y; g = gains.y; break;
        case 14: bl = blackLevel.z; g = gains.z; break;
        case 15: bl = blackLevel.w; g = gains.x; break;
        */
    }

    intermediate = g * (v - bl) / (whiteLevel - bl);
}
