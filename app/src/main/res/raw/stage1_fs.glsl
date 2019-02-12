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

float[9] load3x3(int x, int y) {
    float outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = float(texelFetch(rawBuffer, ivec2(x + (i % 3) - 1, y + (i / 3) - 1), 0).x);
    }
    return outputArray;
}

void linearizeAndGainmap(int x, int y, inout float[9] outputArray) {
    uint kk = uint(0);
    for (int j = y - 1; j <= y + 1; j++) {
        for (int i = x - 1; i <= x + 1; i++) {
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
vec3 demosaic(int x, int y, float[9] inputArray) {
    uint index = uint((x & 1) | ((y & 1) << 1));
    index |= (cfaPattern << 2);
    vec3 pRGB;
    float gMin, gMax;
    switch (index) {
        case 0:
        case 5:
        case 10:
        case 15:  // Red centered
                  // B G B
                  // G R G
                  // B G B
            pRGB.r = inputArray[4];
            pRGB.g = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4.f;
            pRGB.b = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4.f;
            break;
        case 1:
        case 4:
        case 11:
        case 14: // Green centered w/ horizontally adjacent Red
                 // G B G
                 // R G R
                 // G B G
            pRGB.r = (inputArray[3] + inputArray[5]) / 2.f;
            gMin = min(min(inputArray[0], inputArray[8]), min(inputArray[2], inputArray[8]));
            gMax = max(max(inputArray[0], inputArray[8]), max(inputArray[2], inputArray[8]));
            pRGB.g = clamp(inputArray[4], gMin, gMax);
            pRGB.b = (inputArray[1] + inputArray[7]) / 2.f;
            break;
        case 2:
        case 7:
        case 8:
        case 13: // Green centered w/ horizontally adjacent Blue
                 // G R G
                 // B G B
                 // G R G
            pRGB.r = (inputArray[1] + inputArray[7]) / 2.f;
            gMin = min(min(inputArray[0], inputArray[8]), min(inputArray[2], inputArray[8]));
            gMax = max(max(inputArray[0], inputArray[8]), max(inputArray[2], inputArray[8]));
            pRGB.g = clamp(inputArray[4], gMin, gMax);
            pRGB.b = (inputArray[3] + inputArray[5]) / 2.f;
            break;
        case 3:
        case 6:
        case 9:
        case 12: // Blue centered
                 // R G R
                 // G B G
                 // R G R
            pRGB.r = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4.f;
            pRGB.g = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4.f;
            pRGB.b = inputArray[4];
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

    float[9] inoutPatch = load3x3(x, y);
    linearizeAndGainmap(x, y, inoutPatch);
    vec3 sensor = demosaic(x, y, inoutPatch);
    intermediate = convertSensorToIntermediate(sensor);
}
