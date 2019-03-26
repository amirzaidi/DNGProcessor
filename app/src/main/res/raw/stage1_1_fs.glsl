#version 300 es

precision mediump float;
precision mediump usampler2D;

uniform usampler2D rawBuffer;
uniform int rawWidth;
uniform int rawHeight;

uniform sampler2D gainMap;
uniform bool hasGainMap;

// Sensor and picture variables
uniform uint cfaPattern; // The Color Filter Arrangement pattern used
uniform vec4 blackLevel; // Blacklevel to subtract for each channel, given in CFA order
uniform float whiteLevel; // Whitelevel of sensor
uniform bool oneDotFive;

// Out
out float intermediate;

vec4 getGainMap(int x, int y) {
    if (hasGainMap) {
        float interpX = float(x) / float(rawWidth);
        float interpY = float(y) / float(rawHeight);
        return texture(gainMap, vec2(interpX, interpY));
    }
    return vec4(1.f, 1.f, 1.f, 1.f);
}

float linearizeAndGainmap(int x, int y, float v) {
    vec4 gains = getGainMap(x, y);

    int index = (x & 1) | ((y & 1) << 1);  // bits [0,1] are blacklevel offset
    float bl = 0.f;
    float g = 1.f;
    switch (index) {
        case 0: bl = blackLevel.x; g = gains.x; break;
        case 1: bl = blackLevel.y; g = gains.y; break;
        case 2: bl = blackLevel.z; g = gains.z; break;
        case 3: bl = blackLevel.w; g = gains.w; break;
    }

    return g * (v - bl) / (whiteLevel - bl);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float v;
    if (oneDotFive && ((xy.x % 8 == 6 && xy.y % 16 == 1) || (xy.x % 8 == 2 && xy.y % 16 == 9))) {
        // OnePlus 5 Dot-Fix: Bilinear interpolate this green pixel in a cross
        uint vx;
        for (int j = 0; j < 4; j++) {
            ivec2 pos = xy + ivec2(2 * (j % 2) - 1, 2 * (j / 2) - 1);
            vx += texelFetch(rawBuffer, pos, 0).x;
        }
        v = float(vx) * 0.25f;
    } else {
        v = float(texelFetch(rawBuffer, xy, 0).x);
    }
    intermediate = linearizeAndGainmap(xy.x, xy.y, v);
}
