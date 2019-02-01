#version 320 es

precision mediump float;

uniform usampler2D raw;
uniform int rawWidth;
uniform int rawHeight;

// Sensor and picture variables
uniform uint cfaPattern; // The Color Filter Arrangement pattern used
uniform vec4 blackLevel; // Blacklevel to subtract for each channel, given in CFA order
uniform float whiteLevel;  // Whitelevel of sensor
uniform vec3 neutralPoint; // The camera neutral
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve

// Transform
uniform mat3 sensorToIntermediate; // Color transform from sensor to XYZ.
uniform mat3 intermediateToProPhoto; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 proPhotoToSRGB; // Color transform from wide-gamut colorspace to sRGB

// Post processing
uniform vec3 postProcCurve;
uniform float saturationFactor;

// Size
uniform ivec2 outOffset;
uniform int outWidth;
uniform int outHeight;

// Out
out vec4 color;

float[9] load3x3(ivec2 xy) {
    float outputArray[9];

    outputArray[0] = float(texelFetch(raw, xy + ivec2(-1, -1), 0).r);
    outputArray[1] = float(texelFetch(raw, xy + ivec2(0, -1), 0).r);
    outputArray[2] = float(texelFetch(raw, xy + ivec2(1, -1), 0).r);
    outputArray[3] = float(texelFetch(raw, xy + ivec2(-1, 0), 0).r);
    outputArray[4] = float(texelFetch(raw, xy, 0).r);
    outputArray[5] = float(texelFetch(raw, xy + ivec2(1, 0), 0).r);
    outputArray[6] = float(texelFetch(raw, xy + ivec2(1, -1), 0).r);
    outputArray[7] = float(texelFetch(raw, xy + ivec2(1, 0), 0).r);
    outputArray[8] = float(texelFetch(raw, xy + ivec2(1, 1), 0).r);

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
    switch (index) {
        case 0:
        case 5:
        case 10:
        case 15:  // Red centered
                  // B G B
                  // G R G
                  // B G B
            pRGB.x = inputArray[4];
            pRGB.y = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4.f;
            pRGB.z = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4.f;
            break;
        case 1:
        case 4:
        case 11:
        case 14: // Green centered w/ horizontally adjacent Red
                 // G B G
                 // R G R
                 // G B G
            pRGB.x = (inputArray[3] + inputArray[5]) / 2.f;
            pRGB.y = inputArray[4];
            pRGB.z = (inputArray[1] + inputArray[7]) / 2.f;
            break;
        case 2:
        case 7:
        case 8:
        case 13: // Green centered w/ horizontally adjacent Blue
                 // G R G
                 // B G B
                 // G R G
            pRGB.x = (inputArray[1] + inputArray[7]) / 2.f;
            pRGB.y = inputArray[4];
            pRGB.z = (inputArray[3] + inputArray[5]) / 2.f;
            break;
        case 3:
        case 6:
        case 9:
        case 12: // Blue centered
                 // R G R
                 // G B G
                 // R G R
            pRGB.x = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4.f;
            pRGB.y = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4.f;
            pRGB.z = inputArray[4];
            break;
    }
    return pRGB;
}

vec3 tonemap(vec3 rgb) {
    vec3 sorted = rgb;
    float tmp;
    int permutation = 0;

    // Sort the RGB channels by value
    if (sorted.z < sorted.y) {
        tmp = sorted.z;
        sorted.z = sorted.y;
        sorted.y = tmp;
        permutation |= 1;
    }
    if (sorted.y < sorted.x) {
        tmp = sorted.y;
        sorted.y = sorted.x;
        sorted.x = tmp;
        permutation |= 2;
    }
    if (sorted.z < sorted.y) {
        tmp = sorted.z;
        sorted.z = sorted.y;
        sorted.y = tmp;
        permutation |= 4;
    }

    vec2 minmax;
    minmax.x = sorted.x;
    minmax.y = sorted.z;

    // Apply tonemapping curve to min, max RGB channel values
    minmax = minmax*minmax*minmax * toneMapCoeffs.x +
        minmax*minmax * toneMapCoeffs.y +
        minmax * toneMapCoeffs.z +
        toneMapCoeffs.w;

    // Rescale middle value
    float newMid;
    if (sorted.z == sorted.x) {
        newMid = minmax.y;
    } else {
        newMid = minmax.x + ((minmax.y - minmax.x) * (sorted.y - sorted.x) /
                (sorted.z - sorted.x));
    }

    vec3 finalRGB;
    switch (permutation) {
        case 0: // b >= g >= r
            finalRGB.x = minmax.x;
            finalRGB.y = newMid;
            finalRGB.z = minmax.y;
            break;
        case 1: // g >= b >= r
            finalRGB.x = minmax.x;
            finalRGB.z = newMid;
            finalRGB.y = minmax.y;
            break;
        case 2: // b >= r >= g
            finalRGB.y = minmax.x;
            finalRGB.x = newMid;
            finalRGB.z = minmax.y;
            break;
        case 3: // g >= r >= b
            finalRGB.z = minmax.x;
            finalRGB.x = newMid;
            finalRGB.y = minmax.y;
            break;
        case 6: // r >= b >= g
            finalRGB.y = minmax.x;
            finalRGB.z = newMid;
            finalRGB.x = minmax.y;
            break;
        case 7: // r >= g >= b
            finalRGB.z = minmax.x;
            finalRGB.y = newMid;
            finalRGB.x = minmax.y;
            break;
        case 4: // impossible
        case 5: // impossible
        default:
            finalRGB.x = 0.f;
            finalRGB.y = 0.f;
            finalRGB.z = 0.f;
            break;
    }
    return finalRGB;
}

vec3 XYZtoxyY(vec3 XYZ) {
    vec3 result;
    float sum = XYZ.x + XYZ.y + XYZ.z;
    if (sum == 0.f) {
        result.x = 0.f;
        result.y = 0.f;
        result.z = 0.f;
    } else {
        result.x = XYZ.x / sum;
        result.y = XYZ.y / sum;
        result.z = XYZ.y;
    }
    return result;
}

vec3 convertSensorToIntermediate(vec3 sensor) {
    vec3 intermediate;

    sensor.x = clamp(sensor.x, 0.f, neutralPoint.x);
    sensor.y = clamp(sensor.y, 0.f, neutralPoint.y);
    sensor.z = clamp(sensor.z, 0.f, neutralPoint.z);

    intermediate = sensorToIntermediate * sensor;
    intermediate = XYZtoxyY(intermediate);

    return intermediate;
}

vec3 xyYtoXYZ(vec3 xyY) {
    vec3 result;
    if (xyY.y == 0.f) {
        result.x = 0.f;
        result.y = 0.f;
        result.z = 0.f;
    } else {
        result.x = xyY.x * xyY.z / xyY.y;
        result.y = xyY.z;
        result.z = (1.f - xyY.x - xyY.y) * xyY.z / xyY.y;
    }
    return result;
}


// Apply gamma correction using sRGB gamma curve
float gammaEncode(float x) {
    return x <= 0.0031308f
        ? x * 12.92f
        : 1.055f * pow(x, 0.4166667f) - 0.055f;
}

// Apply gamma correction to each color channel in RGB pixel
vec3 gammaCorrectPixel(vec3 rgb) {
    vec3 ret;
    ret.x = gammaEncode(rgb.x);
    ret.y = gammaEncode(rgb.y);
    ret.z = gammaEncode(rgb.z);
    return ret;
}

vec3 applyColorspace(vec3 intermediate) {
    vec3 proPhoto, sRGB;

    intermediate = xyYtoXYZ(intermediate);

    proPhoto = intermediateToProPhoto * intermediate;
    proPhoto = tonemap(proPhoto);

    sRGB = proPhotoToSRGB * proPhoto;
    sRGB = gammaCorrectPixel(sRGB);

    return sRGB;
}

// Applies post processing curve to all channels
vec3 applyCurve(vec3 inValue) {
    return inValue*inValue*inValue * postProcCurve.x
        + inValue*inValue * postProcCurve.y
        + inValue * postProcCurve.z;
}

const vec3 gMonoMult = vec3(0.299f, 0.587f, 0.114f);

vec3 saturate(vec3 rgb) {
    return dot(rgb, gMonoMult) * (1.f - saturationFactor)
        + rgb * saturationFactor;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) + outOffset;
    float[9] inoutPatch = load3x3(xy);

    int x = xy.x;
    int y = xy.y;
    linearizeAndGainmap(x, y, inoutPatch);

    vec3 sensor = demosaic(x, y, inoutPatch);
    vec3 intermediate = convertSensorToIntermediate(sensor);
    vec3 sRGB = applyColorspace(intermediate);

    sRGB = applyCurve(sRGB);
    sRGB = saturate(sRGB);
    sRGB = clamp(sRGB, 0.f, 1.f);

    color = vec4(sRGB, 1.f);
}
