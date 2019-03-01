#version 300 es

precision mediump float;

uniform sampler2D rawBuffer;
uniform sampler2D greenBuffer;
uniform int rawWidth;
uniform int rawHeight;

// Sensor and picture variables
uniform uint cfaPattern; // The Color Filter Arrangement pattern used
uniform vec4 neutralLevel; // Neutrallevel of sensor
uniform vec3 neutralPoint; // The camera neutral

// Transform
uniform mat3 sensorToXYZ; // Color transform from sensor to XYZ.

// Out
out vec3 intermediate;

float[25] load5x5(int x, int y, sampler2D buf) {
    float outputArray[25];
    for (int i = 0; i < 25; i++) {
        outputArray[i] = float(texelFetch(buf, ivec2(x + (i % 5) - 2, y + (i / 5) - 2), 0).x);
    }
    return outputArray;
}

// Apply bilinear-interpolation to demosaic
vec3 demosaic(int x, int y, float[25] inputArray, float[25] greenArray) {
    uint index = uint((x & 1) | ((y & 1) << 1));
    index |= (cfaPattern << 2);
    vec3 pRGB;
    int pxType = -1;
    switch (index) {
        case 0:
        case 5:
        case 10:
        case 15:
            pxType = 0; // GRG
            break;
        case 1:
        case 4:
        case 11:
        case 14:
            pxType = 1; // RGR
            break;
        case 2:
        case 7:
        case 8:
        case 13:
            pxType = 2; // BGB
            break;
        case 3:
        case 6:
        case 9:
        case 12:
            pxType = 3; // GBG
            break;
    }

    if (pxType == 0 || pxType == 3) {
        float p = inputArray[12];
        float cross = (inputArray[6] + inputArray[8] + inputArray[16] + inputArray[18]) * 0.25f;
        if (pxType == 0) {
            // Red centered
            // # # R # #
            // # B G B #
            // R G R G R
            // # B G B #
            // # # R # #
            pRGB.r = p;
            pRGB.b = cross;
        } else {
            // Blue centered
            // # # B # #
            // # R G R #
            // B G B G B
            // # R G R #
            // # # B # #
            pRGB.r = cross;
            pRGB.b = p;
        }
    } else if (pxType == 1 || pxType == 2) {
        float horz = (inputArray[11] + inputArray[13]) * 0.5f;
        float vert = (inputArray[7] + inputArray[17]) * 0.5f;
        if (pxType == 1) {
            // Green centered w/ horizontally adjacent Red
            // # # # # #
            // # # B # #
            // # R G R #
            // # # B # #
            // # # # # #
            pRGB.r = horz;
            pRGB.b = vert;
        } else {
            // Green centered w/ horizontally adjacent Blue
            // # # # # #
            // # # R # #
            // # B G B #
            // # # R # #
            // # # # # #
            pRGB.r = vert;
            pRGB.b = horz;
        }
    }

    pRGB.g = greenArray[12];
    return pRGB;
}

vec3 XYZtoxyY(vec3 XYZ) {
    vec3 result = vec3(0.345703f, 0.358539f, 0.f);
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
    int x = clamp(xy.x, 2, rawWidth - 3);
    int y = clamp(xy.y, 2, rawHeight - 3);

    float[25] rawPatch = load5x5(x, y, rawBuffer);
    float[25] greenPatch = load5x5(x, y, greenBuffer);
    vec3 sensor = demosaic(x, y, rawPatch, greenPatch);
    intermediate = convertSensorToIntermediate(sensor);
}
