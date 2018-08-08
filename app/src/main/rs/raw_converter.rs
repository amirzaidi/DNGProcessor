/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma version(1)
#pragma rs java_package_name(amirz.dngprocessor)
#pragma rs_fp_relaxed

#define RS_KERNEL __attribute__((kernel))
#define LOGD(string, expr) rsDebug((string), (expr))

// This file includes a conversion kernel for RGGB, GRBG, GBRG, and BGGR Bayer patterns.
// Applying this script also will apply black-level subtraction, rescaling, clipping, tonemapping,
// and color space transforms along with the Bayer demosaic.

// Buffers
rs_allocation inputRawBuffer; // RAW16 buffer of dimensions (raw image stride) * (raw image height)
rs_allocation intermediateBuffer; // Float32 buffer of dimensions (raw image stride) * (raw image height) * 3

// Gain map
bool hasGainMap; // Does gainmap exist?
rs_allocation gainMap; // Gainmap to apply to linearized raw sensor data.
uint gainMapWidth;  // The width of the gain map
uint gainMapHeight;  // The height of the gain map

// Transformations
rs_matrix3x3 sensorToIntermediate; // Color transform from sensor to a wide-gamut colorspace

rs_matrix3x3 intermediateToProPhoto;
rs_matrix3x3 proPhotoToSRGB; // Color transform from wide-gamut colorspace to sRGB

// Sensor and picture variables
uint cfaPattern; // The Color Filter Arrangement pattern used
ushort4 blackLevelPattern; // Blacklevel to subtract for each channel, given in CFA order
int whiteLevel;  // Whitelevel of sensor
float3 neutralPoint; // The camera neutral
float4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve

// Size
uint offsetX; // X offset into inputRawBuffer
uint offsetY; // Y offset into inputRawBuffer
uint rawWidth; // Width of raw buffer
uint rawHeight; // Height of raw buffer

// Custom variables
float4 postProcCurve;
float saturationFactor;
float sharpenFactor;
int denoiseFactor;

// Interpolate gain map to find per-channel gains at a given pixel
static float4 getGain(uint x, uint y) {
    float interpX = (((float) x) / rawWidth) * gainMapWidth;
    float interpY = (((float) y) / rawHeight) * gainMapHeight;
    uint gX = (uint) interpX;
    uint gY = (uint) interpY;
    uint gXNext = (gX + 1 < gainMapWidth) ? gX + 1 : gX;
    uint gYNext = (gY + 1 < gainMapHeight) ? gY + 1 : gY;
    float4 tl = *((float4 *) rsGetElementAt(gainMap, gX, gY));
    float4 tr = *((float4 *) rsGetElementAt(gainMap, gXNext, gY));
    float4 bl = *((float4 *) rsGetElementAt(gainMap, gX, gYNext));
    float4 br = *((float4 *) rsGetElementAt(gainMap, gXNext, gYNext));
    float fracX = interpX - (float) gX;
    float fracY = interpY - (float) gY;
    float invFracX = 1.f - fracX;
    float invFracY = 1.f - fracY;
    return tl * invFracX * invFracY + tr * fracX * invFracY +
            bl * invFracX * fracY + br * fracX * fracY;
}

// Apply gamma correction using sRGB gamma curve
static float gammaEncode(float x) {
    return (x <= 0.0031308f) ? x * 12.92f : 1.055f * pow(x, 0.4166667f) - 0.055f;
}

// Apply gamma correction to each color channel in RGB pixel
static float3 gammaCorrectPixel(float3 rgb) {
    float3 ret;
    ret.x = gammaEncode(rgb.x);
    ret.y = gammaEncode(rgb.y);
    ret.z = gammaEncode(rgb.z);
    return ret;
}

// Apply polynomial tonemapping curve to each color channel in RGB pixel.
// This attempts to apply tonemapping without changing the hue of each pixel,
// i.e.:
//
// For some RGB values:
// M = max(R, G, B)
// m = min(R, G, B)
// m' = mid(R, G, B)
// chroma = M - m
// H = m' - m / chroma
//
// The relationship H=H' should be preserved, where H and H' are calculated from
// the RGB and RGB' value at this pixel before and after this tonemapping
// operation has been applied, respectively.
static float3 tonemap(float3 rgb) {
    float3 sorted = rgb;
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
    float2 minmax;
    minmax.x = sorted.x;
    minmax.y = sorted.z;
    // Apply tonemapping curve to min, max RGB channel values
    minmax = native_powr(minmax, 3.f) * toneMapCoeffs.x +
            native_powr(minmax, 2.f) * toneMapCoeffs.y +
            minmax * toneMapCoeffs.z + toneMapCoeffs.w;
    // Rescale middle value
    float newMid;
    if (sorted.z == sorted.x) {
        newMid = minmax.y;
    } else {
        newMid = minmax.x + ((minmax.y - minmax.x) * (sorted.y - sorted.x) /
                (sorted.z - sorted.x));
    }
    float3 finalRGB;
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
            LOGD("raw_converter.rs: Logic error in tonemap.", 0);
            break;
    }
    return finalRGB;
}

// Color conversion pipeline step one.
static float3 convertSensorToIntermediate(float3 sensor) {
    /*sensor.x = clamp(sensor.x, 0.f, neutralPoint.x);
    sensor.y = clamp(sensor.y, 0.f, neutralPoint.y);
    sensor.z = clamp(sensor.z, 0.f, neutralPoint.z);
    return rsMatrixMultiply(&sensorToIntermediate, sensor);*/


    float3 intermediate;

    sensor.x = clamp(sensor.x, 0.f, neutralPoint.x);
    sensor.y = clamp(sensor.y, 0.f, neutralPoint.y);
    sensor.z = clamp(sensor.z, 0.f, neutralPoint.z);

    intermediate = rsMatrixMultiply(&sensorToIntermediate, sensor);

    return intermediate;
}

// Apply a colorspace transform to the intermediate colorspace, apply
// a tonemapping curve, apply a colorspace transform to a final colorspace,
// and apply a gamma correction curve.
static float3 applyColorspace(float3 intermediate) {
    float3 proPhoto, sRGB;

    proPhoto = rsMatrixMultiply(&intermediateToProPhoto, intermediate);
    proPhoto = tonemap(proPhoto);

    sRGB = rsMatrixMultiply(&proPhotoToSRGB, proPhoto);
    sRGB = gammaCorrectPixel(sRGB);

    return sRGB;
}

// Load a 3x3 patch of pixels into the output.
static void load3x3ushort(uint x, uint y, rs_allocation buf, /*out*/float* outputArray) {
    outputArray[0] = *((ushort *) rsGetElementAt(buf, x - 1, y - 1));
    outputArray[1] = *((ushort *) rsGetElementAt(buf, x, y - 1));
    outputArray[2] = *((ushort *) rsGetElementAt(buf, x + 1, y - 1));
    outputArray[3] = *((ushort *) rsGetElementAt(buf, x - 1, y));
    outputArray[4] = *((ushort *) rsGetElementAt(buf, x, y));
    outputArray[5] = *((ushort *) rsGetElementAt(buf, x + 1, y));
    outputArray[6] = *((ushort *) rsGetElementAt(buf, x - 1, y + 1));
    outputArray[7] = *((ushort *) rsGetElementAt(buf, x, y + 1));
    outputArray[8] = *((ushort *) rsGetElementAt(buf, x + 1, y + 1));
}

// Load a NxN patch of pixels into the output.
static void loadNxNfloat3(uint x, uint y, int n, rs_allocation buf, /*out*/float3* outputArray) {
    // n is uneven so this will keep one pixel centered.
    int offset = n / 2;
    int index = 0;
    for (int xDelta = -offset; xDelta <= offset; xDelta++) {
        for (int yDelta = -offset; yDelta <= offset; yDelta++) {
            outputArray[index++] = *((float3 *) rsGetElementAt(buf, x + xDelta, y + yDelta));
        }
    }
}

// Blacklevel subtract, and normalize each pixel in the outputArray, and apply the
// gain map.
static void linearizeAndGainmap(uint x, uint y, ushort4 blackLevel, int whiteLevel,
        uint cfa, /*inout*/float* outputArray) {
    uint kk = 0;
    for (uint j = y - 1; j <= y + 1; j++) {
        for (uint i = x - 1; i <= x + 1; i++) {
            uint index = (i & 1) | ((j & 1) << 1);  // bits [0,1] are blacklevel offset
            index |= (cfa << 2);  // bits [2,3] are cfa
            float bl = 0.f;
            float g = 1.f;
            float4 gains = 1.f;
            if (hasGainMap) {
                gains = getGain(i, j);
            }
            switch (index) {
                // RGGB
                case 0:
                    bl = blackLevel.x;
                    g = gains.x;
                    break;
                case 1:
                    bl = blackLevel.y;
                    g = gains.y;
                    break;
                case 2:
                    bl = blackLevel.z;
                    g = gains.z;
                    break;
                case 3:
                    bl = blackLevel.w;
                    g = gains.w;
                    break;
                // GRBG
                case 4:
                    bl = blackLevel.x;
                    g = gains.y;
                    break;
                case 5:
                    bl = blackLevel.y;
                    g = gains.x;
                    break;
                case 6:
                    bl = blackLevel.z;
                    g = gains.w;
                    break;
                case 7:
                    bl = blackLevel.w;
                    g = gains.z;
                    break;
                // GBRG
                case 8:
                    bl = blackLevel.x;
                    g = gains.y;
                    break;
                case 9:
                    bl = blackLevel.y;
                    g = gains.w;
                    break;
                case 10:
                    bl = blackLevel.z;
                    g = gains.x;
                    break;
                case 11:
                    bl = blackLevel.w;
                    g = gains.z;
                    break;
                // BGGR
                case 12:
                    bl = blackLevel.x;
                    g = gains.w;
                    break;
                case 13:
                    bl = blackLevel.y;
                    g = gains.y;
                    break;
                case 14:
                    bl = blackLevel.z;
                    g = gains.z;
                    break;
                case 15:
                    bl = blackLevel.w;
                    g = gains.x;
                    break;
            }

            outputArray[kk] = g * (outputArray[kk] - bl) / (whiteLevel - bl);
            kk++;
        }
    }
}

// Apply bilinear-interpolation to demosaic
static float3 demosaic(uint x, uint y, uint cfa, float* inputArray) {
    uint index = (x & 1) | ((y & 1) << 1);
    index |= (cfa << 2);
    float3 pRGB;
    switch (index) {
        case 0:
        case 5:
        case 10:
        case 15:  // Red centered
                  // B G B
                  // G R G
                  // B G B
            pRGB.x = inputArray[4];
            pRGB.y = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4;
            pRGB.z = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4;
            break;
        case 1:
        case 4:
        case 11:
        case 14: // Green centered w/ horizontally adjacent Red
                 // G B G
                 // R G R
                 // G B G
            pRGB.x = (inputArray[3] + inputArray[5]) / 2;
            pRGB.y = inputArray[4];
            pRGB.z = (inputArray[1] + inputArray[7]) / 2;
            break;
        case 2:
        case 7:
        case 8:
        case 13: // Green centered w/ horizontally adjacent Blue
                 // G R G
                 // B G B
                 // G R G
            pRGB.x = (inputArray[1] + inputArray[7]) / 2;
            pRGB.y = inputArray[4];
            pRGB.z = (inputArray[3] + inputArray[5]) / 2;
            break;
        case 3:
        case 6:
        case 9:
        case 12: // Blue centered
                 // R G R
                 // G B G
                 // R G R
            pRGB.x = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4;
            pRGB.y = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4;
            pRGB.z = inputArray[4];
            break;
    }
    return pRGB;
}

// POST PROCESSING STARTS HERE
const static float3 gMonoMult = {0.299f, 0.587f, 0.114f};
static float3 saturate(float3 rgb) {
    return mix(dot(rgb, gMonoMult), rgb, saturationFactor);
}

// Gets unprocessed XYZ pixel
float3 RS_KERNEL convert_RAW_To_Intermediate(ushort in, uint x, uint y) {
    float3 sensor;

    uint xP = x;
    uint yP = y;

    if (xP == 0) xP = 1;
    if (yP == 0) yP = 1;
    if (xP == rawWidth - 1) xP = rawWidth - 2;
    if (yP == rawHeight - 1) yP = rawHeight  - 2;

    float patch[9];

    load3x3ushort(xP, yP, inputRawBuffer, /*out*/ patch);
    linearizeAndGainmap(xP, yP, blackLevelPattern, whiteLevel, cfaPattern, /*inout*/patch);

    sensor = demosaic(xP, yP, cfaPattern, patch);
    return convertSensorToIntermediate(sensor);
}

static float applyCurve(float in) {
    return native_powr(in, 3.f) * postProcCurve.x +
            native_powr(in, 2.f) * postProcCurve.y +
            in * postProcCurve.z + postProcCurve.w;
}

// Applies post-processing on intermediate XYZ image
uchar4 RS_KERNEL convert_Intermediate_To_ARGB(uint x, uint y) {
    float3 intermediate, sRGB;
    float tmp;
    const int radius = 1, size = 3, area = 9;
    uint xP, yP;

    xP = x + offsetX;
    yP = y + offsetY;

    // Ensure within bounds
    if (xP < radius) xP = radius;
    if (yP < radius) yP = radius;
    if (xP > rawWidth - radius - 1) xP = rawWidth - radius - 1;
    if (yP > rawHeight - radius - 1) yP = rawHeight  - radius - 1;

    float3 patch[area];
    float value[area];

    loadNxNfloat3(xP, yP, size, intermediateBuffer, patch);

    // Save pixel values
    /*for (int i = 0; i < area; i++) {
        value[i] = patch[i].x;
    }*/

    // Sort all pixels in the patch
    /*for (int i = 0; i < area; i++) {
        for (int j = i; j < area; j++) {
            if (patch[i].y > patch[j].y) {
                tmp = patch[i].y;
                patch[i].y = patch[j].y;
                patch[j].y = tmp;
            }
            if (patch[i].z > patch[j].z) {
                tmp = patch[i].z;
                patch[i].z = patch[j].z;
                patch[j].z = tmp;
            }
        }
    }*/

    // Take median pixel value
    intermediate = patch[area / 2];

    // Sharpen value
    /*tmp = area * value[area / 2];
    for (int i = 0; i < area; i++) {
        tmp -= value[i];
    }

    XYZ.x = clamp(value[4] + sharpenFactor * tmp / area, 0.f, 1.f);*/
    sRGB = applyColorspace(intermediate);

    // Apply additional contrast and saturation
    sRGB.r = applyCurve(sRGB.r);
    sRGB.g = applyCurve(sRGB.g);
    sRGB.b = applyCurve(sRGB.b);
    sRGB = saturate(sRGB);
    sRGB = clamp(sRGB, 0.f, 1.f);

    return rsPackColorTo8888(sRGB);
}
