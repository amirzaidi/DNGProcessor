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

float[9] load3x3(int x, int y, sampler2D buf) {
    float outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = texelFetch(buf, ivec2(x + (i % 3) - 1, y + (i / 3) - 1), 0).x;
    }
    return outputArray;
}

int ind(int x, int y) {
    int dim = 3;
    return x + dim / 2 + (y + dim / 2) * dim;
}

// Apply bilinear-interpolation to demosaic
vec3 demosaic(int x, int y, float[9] inputArray, float[9] greenArray) {
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

    // We already computed green
    pRGB.g = greenArray[ind(0, 0)];
    float minG = 0.01f;

    if (pxType == 0 || pxType == 3) {
        float p = inputArray[ind(0, 0)];

        float crosstl = inputArray[ind(-1, -1)] / max(greenArray[ind(-1, -1)], minG),
            crosstr = inputArray[ind(1, -1)] / max(greenArray[ind(1, -1)], minG),
            crossbl = inputArray[ind(-1, 1)] / max(greenArray[ind(-1, 1)], minG),
            crossbr = inputArray[ind(1, 1)] / max(greenArray[ind(1, 1)], minG);

        float cross = 0.25f * max(pRGB.g, minG) * (crosstl + crosstr + crossbl + crossbr);
        if (pxType == 0) {
            // Red centered
            // B # B
            // # R #
            // B # B
            pRGB.r = p;
            pRGB.b = cross;
        } else {
            // Blue centered
            // R # R
            // # B #
            // R # R
            pRGB.r = cross;
            pRGB.b = p;
        }
    } else if (pxType == 1 || pxType == 2) {
        float horzl = inputArray[ind(-1, 0)] / max(greenArray[ind(-1, 0)], minG),
            horzr = inputArray[ind(1, 0)] / max(greenArray[ind(1, 0)], minG);
        float horz = 0.5f * max(pRGB.g, minG) * (horzl + horzr);

        float vertt = inputArray[ind(0, -1)] / max(greenArray[ind(0, -1)], minG),
            vertb = inputArray[ind(0, 1)] / max(greenArray[ind(0, 1)], minG);
        float vert = 0.5f * max(pRGB.g, minG) * (vertt + vertb);

        if (pxType == 1) {
            // Green centered w/ horizontally adjacent Red
            // # B #
            // R # R
            // # B #
            pRGB.r = horz;
            pRGB.b = vert;
        } else {
            // Green centered w/ horizontally adjacent Blue
            // # R #
            // B # B
            // # R #
            pRGB.r = vert;
            pRGB.b = horz;
        }
    }

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
    int x = clamp(xy.x, 1, rawWidth - 2);
    int y = clamp(xy.y, 1, rawHeight - 2);

    float[9] rawPatch = load3x3(x, y, rawBuffer);
    float[9] greenPatch = load3x3(x, y, greenBuffer);
    vec3 sensor = demosaic(x, y, rawPatch, greenPatch);
    intermediate = convertSensorToIntermediate(sensor);
}
