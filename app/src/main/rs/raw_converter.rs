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

typedef uchar3 yuvx_444; // interleaved YUV. (y,u,v) per pixel. use .x/.y/.z to read
typedef uchar3 yuvf_420; // flexible YUV (4:2:0). use rsGetElementAtYuv to read.

#define convert_yuvx_444 convert_uchar3
#define convert_yuvf_420 __error_cant_output_flexible_yuv__
#define rsGetElementAt_yuvx_444 rsGetElementAt_uchar3
#define rsGetElementAt_yuvf_420 __error_cant_output_flexible_yuv__
#define RS_KERNEL __attribute__((kernel))
#define LOGD(string, expr) rsDebug((string), (expr))

// This file includes a conversion kernel for RGGB, GRBG, GBRG, and BGGR Bayer patterns.
// Applying this script also will apply black-level subtraction, rescaling, clipping, tonemapping,
// and color space transforms along with the Bayer demosaic.  See RawConverter.java
// for more information.
// Input globals

rs_allocation inputRawBuffer; // RAW16 buffer of dimensions (raw image stride) * (raw image height)
rs_allocation intermediateBuffer; // Float32 buffer of dimensions (raw image stride) * (raw image height) * 3

bool hasGainMap; // Does gainmap exist?
rs_allocation gainMap; // Gainmap to apply to linearized raw sensor data.
uint gainMapWidth;  // The width of the gain map
uint gainMapHeight;  // The height of the gain map

uint cfaPattern; // The Color Filter Arrangement pattern used
rs_matrix3x3 sensorToIntermediate; // Color transform from sensor to a wide-gamut colorspace
rs_matrix3x3 intermediateToSRGB; // Color transform from wide-gamut colorspace to sRGB
ushort4 blackLevelPattern; // Blacklevel to subtract for each channel, given in CFA order
int whiteLevel;  // Whitelevel of sensor
float3 neutralPoint; // The camera neutral

uint offsetX; // X offset into inputRawBuffer
uint offsetY; // Y offset into inputRawBuffer
uint rawWidth; // Width of raw buffer
uint rawHeight; // Height of raw buffer

float4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve
float saturationFactor;
float sharpenFactor;

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

// Apply a colorspace transform to the intermediate colorspace, apply
// a tonemapping curve, apply a colorspace transform to a final colorspace,
// and apply a gamma correction curve.
static float3 applyColorspace(float3 pRGB) {
    pRGB.x = clamp(pRGB.x, 0.f, neutralPoint.x);
    pRGB.y = clamp(pRGB.y, 0.f, neutralPoint.y);
    pRGB.z = clamp(pRGB.z, 0.f, neutralPoint.z);
    float3 intermediate = rsMatrixMultiply(&sensorToIntermediate, pRGB);
    intermediate = tonemap(intermediate);
    return gammaCorrectPixel(rsMatrixMultiply(&intermediateToSRGB, intermediate));
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

// Load a 3x3 patch of pixels into the output.
static void load3x3float3(uint x, uint y, rs_allocation buf, /*out*/float3* outputArray) {
    outputArray[0] = *((float3 *) rsGetElementAt(buf, x - 1, y - 1));
    outputArray[1] = *((float3 *) rsGetElementAt(buf, x, y - 1));
    outputArray[2] = *((float3 *) rsGetElementAt(buf, x + 1, y - 1));
    outputArray[3] = *((float3 *) rsGetElementAt(buf, x - 1, y));
    outputArray[4] = *((float3 *) rsGetElementAt(buf, x, y));
    outputArray[5] = *((float3 *) rsGetElementAt(buf, x + 1, y));
    outputArray[6] = *((float3 *) rsGetElementAt(buf, x - 1, y + 1));
    outputArray[7] = *((float3 *) rsGetElementAt(buf, x, y + 1));
    outputArray[8] = *((float3 *) rsGetElementAt(buf, x + 1, y + 1));
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

static float3 rgbToHsv(float3 rgb) {
    float3 hsv;

    float r = rgb.r;
    float g = rgb.g;
    float b = rgb.b;

    float h, s, v;
	float minV, maxV, delta;

	minV = fmin(fmin(r, g), b);
	maxV = fmax(fmax(r, g), b);
	v = maxV;
	delta = maxV - minV;

	if(maxV == 0) {
		s = 0;
		h = -1;

        hsv.x = h;
        hsv.y = s;
        hsv.z = v;
        return hsv;
	}
	s = delta / maxV;

	if(r == maxV) {
		h = (g - b) / delta;
	} else if(g == maxV) {
		h = 2 + (b - r) / delta;
	} else {
		h = 4 + (r - g) / delta;
    }

	h /= 6;
	if (h < 0) {
		h += 1;
    }

    hsv.x = h;
    hsv.y = s;
    hsv.z = v;

    return hsv;
}

static float3 hsvToRgb(float3 hsv) {
    float3 rgb;

    float h = hsv.x;
    float s = hsv.y;
    float v = hsv.z;

    float r, g, b;
	int i;
	float f, p, q, t;

	if(s == 0) {
        rgb.r = v;
        rgb.g = v;
        rgb.b = v;

        return rgb;
	}

	h *= 6;
	i = (int) floor(h);
	f = h - i;
	p = v * (1 - s);
	q = v * (1 - s * f);
	t = v * (1 - s * (1 - f));

	switch (i) {
		case 0:
			r = v;
			g = t;
			b = p;
			break;
		case 1:
			r = q;
			g = v;
			b = p;
			break;
		case 2:
			r = p;
			g = v;
			b = t;
			break;
		case 3:
			r = p;
			g = q;
			b = v;
			break;
		case 4:
			r = t;
			g = p;
			b = v;
			break;
		default:
			r = v;
			g = p;
			b = q;
			break;
	}

    rgb.r = r;
    rgb.g = g;
    rgb.b = b;

	return rgb;
}

// Gets unprocessed sRGB image
float3 RS_KERNEL convert_RAW_To_Intermediate(ushort in, uint x, uint y) {
    float3 pRGB, sRGB;

    uint xP = x;
    uint yP = y;

    if (xP == 0) xP = 1;
    if (yP == 0) yP = 1;
    if (xP == rawWidth - 1) xP = rawWidth - 2;
    if (yP == rawHeight - 1) yP = rawHeight  - 2;

    float patch[9];

    load3x3ushort(xP, yP, inputRawBuffer, /*out*/ patch);
    linearizeAndGainmap(xP, yP, blackLevelPattern, whiteLevel, cfaPattern, /*inout*/patch);

    pRGB = demosaic(xP, yP, cfaPattern, patch);
    sRGB = applyColorspace(pRGB);

    return sRGB;
}

// Applies post-processing on intermediate sRGB image
uchar4 RS_KERNEL convert_Intermediate_To_ARGB(uint x, uint y) {
    float3 HSV, sRGB;

    uint xP = x + offsetX;
    uint yP = y + offsetY;

    // Should never happen
    if (xP == 0) xP = 1;
    if (yP == 0) yP = 1;
    if (xP == rawWidth - 1) xP = rawWidth - 2;
    if (yP == rawHeight - 1) yP = rawHeight  - 2;

    float3 patch[9];
    float value[9];

    load3x3float3(xP, yP, intermediateBuffer, /*out*/ patch);

    // Save pixel values
    for (int i = 0; i < 9; i++) {
        value[i] = rgbToHsv(patch[i]).z;
    }

    // Average pixels
    sRGB = patch[0] + patch[1] + patch[2];
    sRGB += patch[3] + patch[4] + patch[5];
    sRGB += patch[6] + patch[7] + patch[8];
    sRGB /= 9;

    // Sharpen value
    float deltaMidValue = 9 * value[4];
    for (int i = 0; i < 9; i++) {
        deltaMidValue -= value[i];
    }
    HSV = rgbToHsv(sRGB);
    HSV.z = clamp(value[4] + sharpenFactor * deltaMidValue, 0.f, 1.f);
    sRGB = hsvToRgb(HSV);

    // Apply additional saturation
    sRGB = saturate(sRGB);
    sRGB = clamp(sRGB, 0.f, 1.f);

    return rsPackColorTo8888(sRGB);
}
