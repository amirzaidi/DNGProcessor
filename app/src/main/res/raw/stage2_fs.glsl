#version 320 es

precision highp float;

uniform sampler2D intermediateBuffer;
uniform int intermediateWidth;
uniform int intermediateHeight;

const int histBins = 513;
uniform float intermediateHist[histBins];

// Sensor and picture variables
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve

// Transform
uniform mat3 intermediateToProPhoto; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 proPhotoToSRGB; // Color transform from wide-gamut colorspace to sRGB

// Post processing
uniform vec3 postProcCurve;
uniform vec3 saturationCurve;
uniform float sharpenFactor;
uniform float histoFactor;

// Size
uniform ivec2 outOffset;

// Out
out vec4 color;

vec3[9] load3x3(ivec2 xy) {
    vec3 outputArray[9];

    outputArray[0] = texelFetch(intermediateBuffer, xy + ivec2(-1, -1), 0).xyz;
    outputArray[1] = texelFetch(intermediateBuffer, xy + ivec2(0, -1), 0).xyz;
    outputArray[2] = texelFetch(intermediateBuffer, xy + ivec2(1, -1), 0).xyz;
    outputArray[3] = texelFetch(intermediateBuffer, xy + ivec2(-1, 0), 0).xyz;
    outputArray[4] = texelFetch(intermediateBuffer, xy, 0).xyz;
    outputArray[5] = texelFetch(intermediateBuffer, xy + ivec2(1, 0), 0).xyz;
    outputArray[6] = texelFetch(intermediateBuffer, xy + ivec2(-1, 1), 0).xyz;
    outputArray[7] = texelFetch(intermediateBuffer, xy + ivec2(0, 1), 0).xyz;
    outputArray[8] = texelFetch(intermediateBuffer, xy + ivec2(1, 1), 0).xyz;

    return outputArray;
}

vec3 processPatch(ivec2 xy) {
    vec3[9] impatch = load3x3(xy);

    /**
    CHROMA NOISE REDUCE
    **/

    // Get denoising threshold
    vec2 minxy = impatch[0].xy, maxxy = minxy;
    for (int i = 1; i < 9; i++) {
        minxy = min(impatch[i].xy, minxy);
        maxxy = max(impatch[i].xy, maxxy);
    }

    // Threshold that needs to be reached to abort averaging.
    float threshold = distance(minxy, maxxy) * 1.1f;

    // Expand in a plus
    const int radiusDenoise = 50;
    vec2 neighbour, px = impatch[4].xy, sum = px;
    int coord, bound, count = 1;

    // Left
    bound = max(xy.x - radiusDenoise, 0);
    coord = xy.x;
    while (coord-- > bound) {
        neighbour = texelFetch(intermediateBuffer, ivec2(coord, xy.y), 0).xy;
        if (distance(px, neighbour) <= threshold) {
            sum += neighbour;
            count++;
        } else {
            break;
        }
    }

    // Right
    bound = min(xy.x + radiusDenoise, intermediateWidth - 1);
    coord = xy.x;
    while (coord++ < bound) {
        neighbour = texelFetch(intermediateBuffer, ivec2(coord, xy.y), 0).xy;
        if (distance(px, neighbour) <= threshold) {
            sum += neighbour;
            count++;
        } else {
            break;
        }
    }

    // Up
    bound = max(xy.y - radiusDenoise, 0);
    coord = xy.y;
    while (coord-- > bound) {
        neighbour = texelFetch(intermediateBuffer, ivec2(xy.x, coord), 0).xy;
        if (distance(px, neighbour) <= threshold) {
            sum += neighbour;
            count++;
        } else {
            break;
        }
    }

    // Down
    bound = min(xy.y + radiusDenoise, intermediateHeight - 1);
    coord = xy.y;
    while (coord++ < bound) {
        neighbour = texelFetch(intermediateBuffer, ivec2(xy.x, coord), 0).xy;
        if (distance(px, neighbour) <= threshold) {
            sum += neighbour;
            count++;
        } else {
            break;
        }
    }

    /**
    SHARPEN
    **/

    float z = impatch[4].z;
    if (sharpenFactor > 0.f) {
        // Sum of difference with all pixels nearby
        float dz = impatch[4].z * 8.f;
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                dz -= impatch[i].z;
            }
        }

        // Sort impatch z
        float tmp;
        for (int i = 0; i < 5; i++) {
            for (int j = i + 1; j < 9; j++) {
                if (impatch[j].z < impatch[i].z) {
                    tmp = impatch[j].z;
                    impatch[j].z = impatch[i].z;
                    impatch[i].z = tmp;
                }
            }
        }

        // Use this difference to boost sharpness
        z = clamp(z + sharpenFactor * dz, 0.f, 1.f);
    }

    // Histogram equalization
    int bin = int(z * float(histBins));
    if (bin >= histBins) bin = histBins - 1;
    z = (1.f - histoFactor) * z + histoFactor * intermediateHist[bin];

    return vec3(sum / float(count), z);
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
            finalRGB.r = minmax.x;
            finalRGB.g = newMid;
            finalRGB.b = minmax.y;
            break;
        case 1: // g >= b >= r
            finalRGB.r = minmax.x;
            finalRGB.b = newMid;
            finalRGB.g = minmax.y;
            break;
        case 2: // b >= r >= g
            finalRGB.g = minmax.x;
            finalRGB.r = newMid;
            finalRGB.b = minmax.y;
            break;
        case 3: // g >= r >= b
            finalRGB.b = minmax.x;
            finalRGB.r = newMid;
            finalRGB.g = minmax.y;
            break;
        case 6: // r >= b >= g
            finalRGB.g = minmax.x;
            finalRGB.b = newMid;
            finalRGB.r = minmax.y;
            break;
        case 7: // r >= g >= b
            finalRGB.b = minmax.x;
            finalRGB.g = newMid;
            finalRGB.r = minmax.y;
            break;
    }
    return finalRGB;
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
    proPhoto = tonemap(proPhoto); // Broken?

    sRGB = proPhotoToSRGB * proPhoto;
    sRGB = gammaCorrectPixel(sRGB);

    return sRGB;
}

const vec3 gMonoMult = vec3(0.299f, 0.587f, 0.114f);
vec3 saturate(vec3 rgb, float z) {
    float saturationFactor = z*z * saturationCurve[0]
        + z * saturationCurve[1]
        + saturationCurve[2];

    return rgb * saturationFactor
        - dot(rgb, gMonoMult) * (saturationFactor - 1.f);
}

// Applies post processing curve to all channels
vec3 applyCurve(vec3 inValue) {
    return inValue*inValue*inValue * postProcCurve.x
        + inValue*inValue * postProcCurve.y
        + inValue * postProcCurve.z;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) + outOffset;

    // Sharpen and denoise value
    vec3 intermediate = processPatch(xy);

    // Convert to final colorspace
    vec3 sRGB = applyColorspace(intermediate);

    // Apply additional contrast and saturation
    sRGB = applyCurve(sRGB);
    sRGB = saturate(sRGB, intermediate.z);
    sRGB = clamp(sRGB, 0.f, 1.f);

    color = vec4(sRGB, 1.f);
}
