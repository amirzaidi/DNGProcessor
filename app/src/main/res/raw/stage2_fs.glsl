#version 300 es

precision mediump float;

uniform sampler2D intermediateBuffer;
uniform int intermediateWidth;
uniform int intermediateHeight;

uniform int maxRadiusDenoise;
const int histBins = 513;
uniform float intermediateHist[histBins];

// Sensor and picture variables
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve

// Transform
uniform mat3 intermediateToProPhoto; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 proPhotoToSRGB; // Color transform from wide-gamut colorspace to sRGB

// Post processing
uniform float sharpenFactor;
uniform vec2 saturationCurve;
uniform float histFactor;
uniform vec2 histCurve;

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

vec3 processPatch(ivec2 xyPos) {
    vec3[9] impatch = load3x3(xyPos);
    vec2 xy = impatch[4].xy;

    /**
    CHROMA NOISE REDUCE
    **/
    // Use logistics to determine a noise level
    vec2 mean;
    for (int i = 0; i < 9; i++) {
        mean += impatch[i].xy;
    }
    mean /= 9.f;

    float s;
    for (int i = 0; i < 9; i++) {
        vec2 diff = mean - impatch[i].xy;
        s += diff.x * diff.x + diff.y * diff.y;
    }
    s = sqrt(s);

    // Threshold that needs to be reached to abort averaging.
    vec2 minxy = impatch[0].xy, maxxy = minxy;
    for (int i = 1; i < 9; i++) {
        minxy = min(impatch[i].xy, minxy);
        maxxy = max(impatch[i].xy, maxxy);
    }

    float threshold = distance(minxy, maxxy) * (1.f + min(5.f * s, 1.f));
    int radiusDenoise = min(50 + int(200.f * s), maxRadiusDenoise);

    // Expand in a plus
    vec2 neighbour, sum = xy;
    int coord, bound, count = 1;

    // Left
    bound = max(xyPos.x - radiusDenoise, 0);
    coord = xyPos.x;
    while (coord-- > bound) {
        neighbour = texelFetch(intermediateBuffer, ivec2(coord, xyPos.y), 0).xy;
        if (distance(mean, neighbour) <= threshold) {
            sum += neighbour;
            count++;
        } else {
            break;
        }
    }

    // Right
    bound = min(xyPos.x + radiusDenoise, intermediateWidth - 1);
    coord = xyPos.x;
    while (coord++ < bound) {
        neighbour = texelFetch(intermediateBuffer, ivec2(coord, xyPos.y), 0).xy;
        if (distance(mean, neighbour) <= threshold) {
            sum += neighbour;
            count++;
        } else {
            break;
        }
    }

    // Up
    bound = max(xyPos.y - radiusDenoise, 0);
    coord = xyPos.y;
    while (coord-- > bound) {
        neighbour = texelFetch(intermediateBuffer, ivec2(xyPos.x, coord), 0).xy;
        if (distance(mean, neighbour) <= threshold) {
            sum += neighbour;
            count++;
        } else {
            break;
        }
    }

    // Down
    bound = min(xyPos.y + radiusDenoise, intermediateHeight - 1);
    coord = xyPos.y;
    while (coord++ < bound) {
        neighbour = texelFetch(intermediateBuffer, ivec2(xyPos.x, coord), 0).xy;
        if (distance(mean, neighbour) <= threshold) {
            sum += neighbour;
            count++;
        } else {
            break;
        }
    }

    xy = sum / float(count);

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
        z = z + sharpenFactor * dz;
    }

    // Histogram equalization
    int bin = clamp(int(z * float(histBins)), 0, histBins - 1);
    z = histCurve.x * z*z + histCurve.y * z;
    z = (1.f - histFactor) * z + histFactor * intermediateHist[bin];

    return vec3(xy, z);
}

vec3 xyYtoXYZ(vec3 xyY) {
    vec3 result = vec3(0.f, xyY.z, 0.f);
    if (xyY.y > 0.f) {
        result.x = xyY.x * xyY.z / xyY.y;
        result.z = (1.f - xyY.x - xyY.y) * xyY.z / xyY.y;
    }
    return clamp(result, 0.f, 1.f);
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
    minmax = pow(minmax, vec2(3.f, 3.f)) * toneMapCoeffs.x +
        pow(minmax, vec2(2.f, 2.f)) * toneMapCoeffs.y +
        minmax * toneMapCoeffs.z +
        toneMapCoeffs.w;

    // Rescale middle value
    float newMid;
    if (sorted.z == sorted.x) {
        newMid = minmax.y;
    } else {
        float yprog = (sorted.y - sorted.x) / (sorted.z - sorted.x);
        newMid = minmax.x + (minmax.y - minmax.x) * yprog;
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
    ret.r = gammaEncode(rgb.r);
    ret.g = gammaEncode(rgb.g);
    ret.b = gammaEncode(rgb.b);
    return ret;
}

vec3 applyColorspace(vec3 intermediate) {
    vec3 proPhoto, sRGB;

    intermediate = xyYtoXYZ(intermediate);

    proPhoto = clamp(intermediateToProPhoto * intermediate, 0.f, 1.f);
    proPhoto = tonemap(proPhoto);

    sRGB = clamp(proPhotoToSRGB * proPhoto, 0.f, 1.f);
    sRGB = gammaCorrectPixel(sRGB);

    return sRGB;
}

const vec3 gMonoMult = vec3(0.299f, 0.587f, 0.114f);
vec3 saturate(vec3 rgb) {
    float maxv = max(max(rgb.r, rgb.g), rgb.b);
    float minv = min(min(rgb.r, rgb.g), rgb.b);
    if (maxv > minv) {
        float s = maxv - minv; // [0,1]
        float saturation = saturationCurve.x - saturationCurve.y * pow(s, 1.25f);
        rgb = rgb * saturation + dot(rgb, gMonoMult) * (1.f - saturation);
    }
    return rgb;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) + outOffset;

    // Sharpen and denoise value
    vec3 intermediate = processPatch(xy);

    // Convert to final colorspace
    vec3 sRGB = applyColorspace(intermediate);

    // Add saturation
    sRGB = saturate(sRGB);
    sRGB = clamp(sRGB, 0.f, 1.f);

    color = vec4(sRGB, 1.f);
}
