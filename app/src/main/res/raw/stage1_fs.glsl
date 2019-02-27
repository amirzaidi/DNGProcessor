#version 300 es

precision mediump float;

uniform usampler2D rawBuffer;
uniform int rawWidth;
uniform int rawHeight;

uniform sampler2D gainMap;
uniform bool hasGainMap;

// Sensor and picture variables
uniform uint cfaPattern; // The Color Filter Arrangement pattern used
uniform vec4 blackLevel; // Blacklevel to subtract for each channel, given in CFA order
uniform float whiteLevel; // Whitelevel of sensor
uniform vec4 neutralLevel; // Neutrallevel of sensor
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

vec4 getGainMap(int x, int y) {
    if (hasGainMap) {
        float interpX = float(x) / float(rawWidth);
        float interpY = float(y) / float(rawHeight);
        return texelFetch(gainMap, ivec2(interpX, interpY), 0);
    }
    return vec4(1.f, 1.f, 1.f, 1.f);
}

void linearizeAndGainmap(int x, int y, inout float[25] outputArray) {
    uint kk = uint(0);
    for (int j = y - 2; j <= y + 2; j++) {
        for (int i = x - 2; i <= x + 2; i++) {
            vec4 gains = getGainMap(i, j);

            int index = (i & 1) | ((j & 1) << 1);  // bits [0,1] are blacklevel offset
            float bl = 0.f;
            float n = 1.f;
            float g = 1.f;
            switch (index) {
                case 0: bl = blackLevel.x; n = neutralLevel.x; g = gains.x; break;
                case 1: bl = blackLevel.y; n = neutralLevel.y; g = gains.y; break;
                case 2: bl = blackLevel.z; n = neutralLevel.z; g = gains.z; break;
                case 3: bl = blackLevel.w; n = neutralLevel.w; g = gains.w; break;
            }

            outputArray[kk] = g * (outputArray[kk] - bl) / (whiteLevel - bl);
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
    float gDiffThres = 0.025f; // Prevent FP rounding errors
    vec2 g, gDiff;
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

            g = vec2((inputArray[7] + inputArray[17]), (inputArray[11] + inputArray[13])) * 0.5f;
            gDiff = abs(vec2(inputArray[13] - inputArray[11], inputArray[17] - inputArray[7]));
            if (length(gDiff) > gDiffThres) {
                pRGB.g = length(g * gDiff / length(gDiff));
            } else {
                gMin = min(min(inputArray[7], inputArray[17]), min(inputArray[11], inputArray[13]));
                gMax = max(max(inputArray[7], inputArray[17]), max(inputArray[11], inputArray[13]));
                pRGB.g = (inputArray[7] + inputArray[11] + inputArray[13] + inputArray[17] - gMin - gMax) * 0.5f;
            }

            pRGB.b = (inputArray[6] + inputArray[8] + inputArray[16] + inputArray[18]) * 0.25f;
            break;
        case 1:
        case 4:
        case 11:
        case 14: // Green centered w/ horizontally adjacent Red
                 // Mean R
                 // Clamped G
                 // Mean B
                 // # # # # #
                 // # # B # #
                 // # R G R #
                 // # # B # #
                 // # # # # #
            pRGB.r = (inputArray[11] + inputArray[13]) * 0.5f;
            pRGB.g = inputArray[12];
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
                 // # # R # #
                 // # B G B #
                 // # # R # #
                 // # # # # #
            pRGB.r = (inputArray[7] + inputArray[17]) * 0.5f;
            pRGB.g = inputArray[12];
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
            pRGB.r = (inputArray[6] + inputArray[8] + inputArray[16] + inputArray[18]) * 0.25f;

            g = vec2((inputArray[7] + inputArray[17]), (inputArray[11] + inputArray[13])) * 0.5f;
            gDiff = abs(vec2(inputArray[13] - inputArray[11], inputArray[17] - inputArray[7]));
            if (length(gDiff) > gDiffThres) {
                pRGB.g = length(g * gDiff / length(gDiff));
            } else {
                gMin = min(min(inputArray[7], inputArray[17]), min(inputArray[11], inputArray[13]));
                gMax = max(max(inputArray[7], inputArray[17]), max(inputArray[11], inputArray[13]));
                pRGB.g = (inputArray[7] + inputArray[11] + inputArray[13] + inputArray[17] - gMin - gMax) * 0.5f;
            }

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
    sensor = max(sensor, 0.f);
    vec3 npf = sensor / neutralPoint;
    sensor = min(sensor, neutralPoint);

    // When both red and blue channels are above white point, assume green is too
    // So extend dynamic range by scaling white point
    // Use a bias so only high green values become higher
    // In highlights, bias should be one
    float bias = pow(sensor.g, 3.f);
    sensor *= (1.f - bias) + max(npf.r + npf.b, 2.f) * 0.5f * bias;

    vec3 XYZ = sensorToXYZ * sensor;
    vec3 intermediate = XYZtoxyY(XYZ);

    // Sigmoid mapped so 0 -> 0; 0.5 -> 0.5; inf -> 1
    intermediate.z = 2.f / (1.f + exp(-2.197f * intermediate.z)) - 1.f;

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
