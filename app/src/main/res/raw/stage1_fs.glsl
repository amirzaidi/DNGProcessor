#version 300 es

precision mediump float;

uniform usampler2D rawBuffer;
uniform int rawWidth;
uniform int rawHeight;

// Sensor and picture variables
uniform uint cfaPattern; // The Color Filter Arrangement pattern used
uniform vec4 blackLevel; // Blacklevel to subtract for each channel, given in CFA order
uniform float whiteLevel; // Whitelevel of sensor
uniform vec3 neutralPoint; // The camera neutral

// Transform
uniform mat3 sensorToXYZ; // Color transform from sensor to XYZ.

// Out
out vec3 intermediate;

float[25] load5x5(int x, int y) {
    float outputArray[25];
    for (int i = 0; i < 25; i++) {
        outputArray[i] = float(texelFetch(rawBuffer, ivec2(x + (i % 5) - 2, y + (i / 5) - 2), 0).x);
    }
    return outputArray;
}

void linearizeAndGainmap(int x, int y, inout float[25] outputArray) {
    uint kk = uint(0);
    for (int j = y - 2; j <= y + 2; j++) {
        for (int i = x - 2; i <= x + 2; i++) {
            uint index = uint((i & 1) | ((j & 1) << 1));  // bits [0,1] are blacklevel offset
            index |= (cfaPattern << 2);  // bits [2,3] are cfa
            float bl = 0.f;
            switch (index) {
                // RGGB
                case 0: bl = blackLevel.x; break;
                case 1: bl = blackLevel.y; break;
                case 2: bl = blackLevel.z; break;
                case 3: bl = blackLevel.w; break;
                // GRBG
                case 4: bl = blackLevel.x; break;
                case 5: bl = blackLevel.y; break;
                case 6: bl = blackLevel.z; break;
                case 7: bl = blackLevel.w; break;
                // GBRG
                case 8: bl = blackLevel.x; break;
                case 9: bl = blackLevel.y; break;
                case 10: bl = blackLevel.z; break;
                case 11: bl = blackLevel.w; break;
                // BGGR
                case 12: bl = blackLevel.x; break;
                case 13: bl = blackLevel.y; break;
                case 14: bl = blackLevel.z; break;
                case 15: bl = blackLevel.w; break;
            }

            outputArray[kk] = (outputArray[kk] - bl) / (whiteLevel - bl);
            kk++;
        }
    }
}


// Apply bilinear-interpolation to demosaic
vec3 demosaic(int x, int y, float[25] inputArray) {
    uint index = uint((x & 1) | ((y & 1) << 1));
    index |= (cfaPattern << 2);
    vec3 pRGB;
    float rMin, rMax, gMin, gMax, bMin, bMax;
    float extremaFactor = 1.1f;
    // Denoise green subpixels, as the human eye is most sensitive to green luminance.
    switch (index) {
        case 0:
        case 5:
        case 10:
        case 15:  // Red centered
                  // Clamped R
                  // Median G, B
                  // # # R # #
                  // # B G B #
                  // R G R G R
                  // # B G B #
                  // # # R # #
            rMin = (min(inputArray[2], inputArray[22]) + min(inputArray[10], inputArray[14])) * 0.5f;
            rMax = (max(inputArray[2], inputArray[22]) + max(inputArray[10], inputArray[14])) * 0.5f;
            pRGB.r = clamp(inputArray[12], rMin / extremaFactor, rMax * extremaFactor);

            gMin = min(min(inputArray[7], inputArray[17]), min(inputArray[11], inputArray[13]));
            gMax = max(max(inputArray[7], inputArray[17]), max(inputArray[11], inputArray[13]));
            pRGB.g = (inputArray[7] + inputArray[11] + inputArray[13] + inputArray[17] - gMin - gMax) * 0.5f;

            bMin = min(min(inputArray[6], inputArray[18]), min(inputArray[8], inputArray[16]));
            bMax = max(max(inputArray[6], inputArray[18]), max(inputArray[8], inputArray[16]));
            pRGB.b = (inputArray[6] + inputArray[8] + inputArray[16] + inputArray[18] - bMin - bMax) * 0.5f;
            break;
        case 1:
        case 4:
        case 11:
        case 14: // Green centered w/ horizontally adjacent Red
                 // Mean R
                 // Clamped G
                 // Mean B
                 // # # # # #
                 // # G B G #
                 // # R G R #
                 // # G B G #
                 // # # # # #
            pRGB.r = (inputArray[11] + inputArray[13]) * 0.5f;
            gMin = (min(inputArray[6], inputArray[18]) + min(inputArray[8], inputArray[16])) * 0.5f;
            gMax = (max(inputArray[6], inputArray[18]) + max(inputArray[8], inputArray[16])) * 0.5f;
            pRGB.g = clamp(inputArray[12], gMin / extremaFactor, gMax * extremaFactor);
            pRGB.b = (inputArray[7] + inputArray[17]) * 0.5f;
            break;
        case 2:
        case 7:
        case 8:
        case 13: // Green centered w/ horizontally adjacent Blue
                 // Mean R
                 // Clamped G
                 // Mean B
                 // # # # # #
                 // # G R G #
                 // # B G B #
                 // # G R G #
                 // # # # # #
            pRGB.r = (inputArray[7] + inputArray[17]) * 0.5f;
            gMin = (min(inputArray[6], inputArray[18]) + min(inputArray[8], inputArray[16])) * 0.5f;
            gMax = (max(inputArray[6], inputArray[18]) + max(inputArray[8], inputArray[16])) * 0.5f;
            pRGB.g = clamp(inputArray[12], gMin / extremaFactor, gMax * extremaFactor);
            pRGB.b = (inputArray[11] + inputArray[13]) * 0.5f;
            break;
        case 3:
        case 6:
        case 9:
        case 12: // Blue centered
                 // Median R, G
                 // Clamped B
                 // # # B # #
                 // # R G R #
                 // B G B G B
                 // # R G R #
                 // # # B # #
            rMin = min(min(inputArray[6], inputArray[18]), min(inputArray[8], inputArray[16]));
            rMax = max(max(inputArray[6], inputArray[18]), max(inputArray[8], inputArray[16]));
            pRGB.r = (inputArray[6] + inputArray[8] + inputArray[16] + inputArray[18] - rMin - rMax) * 0.5f;

            gMin = min(min(inputArray[7], inputArray[17]), min(inputArray[11], inputArray[13]));
            gMax = max(max(inputArray[7], inputArray[17]), max(inputArray[11], inputArray[13]));
            pRGB.g = (inputArray[7] + inputArray[11] + inputArray[13] + inputArray[17] - gMin - gMax) * 0.5f;

            bMin = (min(inputArray[2], inputArray[22]) + min(inputArray[10], inputArray[14])) * 0.5f;
            bMax = (max(inputArray[2], inputArray[22]) + max(inputArray[10], inputArray[14])) * 0.5f;
            pRGB.b = clamp(inputArray[12], bMin / extremaFactor, bMax * extremaFactor);
            break;
    }
    return pRGB;
}

vec3 XYZtoxyY(vec3 XYZ) {
    vec3 result = vec3(0.f, 0.f, 0.f);
    float sum = XYZ.x + XYZ.y + XYZ.z;
    if (sum > 0.f) {
        result.x = XYZ.x / sum;
        result.y = XYZ.y / sum;
        result.z = XYZ.y;
    }
    return result;
}

vec3 convertSensorToIntermediate(vec3 sensor) {
    sensor = min(max(sensor, 0.f), neutralPoint); // [0, neutralPoint]
    vec3 XYZ = sensorToXYZ * sensor;
    vec3 intermediate = XYZtoxyY(XYZ);
    return intermediate;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int x = clamp(xy.x, 1, rawWidth - 2);
    int y = clamp(xy.y, 1, rawHeight - 2);

    float[25] inoutPatch = load5x5(x, y);
    linearizeAndGainmap(x, y, inoutPatch);
    vec3 sensor = demosaic(x, y, inoutPatch);
    intermediate = convertSensorToIntermediate(sensor);
}
