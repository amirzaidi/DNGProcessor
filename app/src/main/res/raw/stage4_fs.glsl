#version 300 es

precision mediump float;

uniform sampler2D intermediateBuffer;
uniform sampler2D intermediateDownscale;
uniform int intermediateWidth;
uniform int intermediateHeight;

uniform int yOffset;

uniform vec2 zRange;
uniform int radiusDenoise;
uniform vec2 noiseProfile;

// Sensor and picture variables
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve

// Transform
uniform mat3 XYZtoProPhoto; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 proPhotoToSRGB; // Color transform from wide-gamut colorspace to sRGB

uniform vec3 sigma;

// Post processing
uniform float sharpenFactor;
uniform vec3 saturationCurve;
uniform float histFactor;

// Size
uniform ivec2 outOffset;

// Out
out vec4 color;

vec3[9] load3x3(ivec2 xy, int n) {
    vec3 outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = texelFetch(intermediateBuffer, xy + n * ivec2((i % 3) - 1, (i / 3) - 1), 0).xyz;
    }
    return outputArray;
}

vec3 processPatch(ivec2 xyPos) {
    vec3[9] impatch = load3x3(xyPos, 2);
    vec3 mid = impatch[4];

    // Take unfiltered xy and z as starting point.
    vec2 xy = mid.xy;
    float z = mid.z;

    vec3 mean;
    for (int i = 0; i < 9; i++) {
        mean += impatch[i];
    }
    mean /= 9.f;
    vec3 sigmaLocal;
    for (int i = 0; i < 9; i++) {
        vec3 diff = mean - impatch[i];
        sigmaLocal += diff * diff;
    }
    sigmaLocal = max(sqrt(sigmaLocal / 9.f), sigma);
    sigmaLocal.z *= clamp(0.25f / mean.z, 0.5f, 10.f);

    vec3 minxyz = impatch[0].xyz, maxxyz = minxyz;
    for (int i = 1; i < 9; i++) {
        minxyz = min(minxyz, impatch[i]);
        maxxyz = max(maxxyz, impatch[i]);
    }
    float distxy = distance(minxyz.xy, maxxyz.xy);
    float distz = distance(minxyz.z, maxxyz.z);

    /**
    CHROMA NOISE REDUCE
    **/

    //float Npx = pow(noiseProfile.x * z + noiseProfile.y, 2.f);

    // Thresholds
    float thExclude = 3.f;
    float thStop = 4.f;

    // Expand in a plus
    vec3 midDivSigma = mid / sigmaLocal;
    vec3 neighbour;
    vec3 sum = mid;
    int coord, bound, count, totalCount = 1, shiftFactor = 16;
    float dist;

    // Left
    coord = xyPos.x;
    bound = max(coord - radiusDenoise, 0);
    count = 0;
    dist = 0.f;
    while (coord > bound && dist < thStop) {
        neighbour = texelFetch(intermediateDownscale, ivec2(coord, xyPos.y) / 2, 0).xyz;
        coord -= 2 << (count / shiftFactor);
        dist = distance(midDivSigma, neighbour / sigmaLocal);
        if (dist < thExclude) {
            sum += neighbour;
            count++;
        }
    }
    totalCount += count;

    // Right
    coord = xyPos.x;
    bound = min(coord + radiusDenoise, intermediateWidth - 1);
    count = 0;
    dist = 0.f;
    while (coord < bound && dist < thStop) {
        neighbour = texelFetch(intermediateDownscale, ivec2(coord, xyPos.y) / 2, 0).xyz;
        coord += 2 << (count / shiftFactor);
        dist = distance(midDivSigma, neighbour / sigmaLocal);
        if (dist < thExclude) {
            sum += neighbour;
            count++;
        }
    }
    totalCount += count;

    // Up
    coord = xyPos.y;
    bound = max(coord - radiusDenoise, 0);
    count = 0;
    dist = 0.f;
    while (coord > bound && dist < thStop) {
        neighbour = texelFetch(intermediateDownscale, ivec2(xyPos.x, coord) / 2, 0).xyz;
        coord -= 2 << (count / shiftFactor);
        dist = distance(midDivSigma, neighbour / sigmaLocal);
        if (dist < thExclude) {
            sum += neighbour;
            count++;
        }
    }
    totalCount += count;

    // Down
    coord = xyPos.y;
    bound = min(coord + radiusDenoise, intermediateHeight - 1);
    count = 0;
    dist = 0.f;
    while (coord < bound && dist < thStop) {
        neighbour = texelFetch(intermediateDownscale, ivec2(xyPos.x, coord) / 2, 0).xyz;
        coord += 2 << (count / shiftFactor);
        dist = distance(midDivSigma, neighbour / sigmaLocal);
        if (dist < thExclude) {
            sum += neighbour;
            count++;
        }
    }
    totalCount += count;

    xy = sum.xy / float(totalCount);
    //z = sum.z / float(totalCount);

    /**
    LUMA DENOISE AND SHARPEN
    **/
    impatch = load3x3(xyPos, 1);
    if (sharpenFactor > 0.f) {
        // Sum of difference with all pixels nearby
        float dz = mid.z * 12.f;
        for (int i = 0; i < 9; i++) {
            if (i % 2 == 0) {
                if (i != 4) {
                    dz -= impatch[i].z;
                }
            } else {
                dz -= 2.f * impatch[i].z;
            }
        }

        // Use this difference to boost sharpness
        z += sharpenFactor * dz;
    } else if (sharpenFactor < 0.f) {
        // Use median as z
        float tmp;
        for (int i = 0; i < 5; i++) {
            for (int j = i + 1; j < 9; j++) {
                if (impatch[i].z > impatch[j].z) {
                    tmp = impatch[j].z;
                    impatch[j].z = impatch[i].z;
                    impatch[i].z = tmp;
                }
            }
        }
        z = (1.f + sharpenFactor) * z - sharpenFactor * impatch[4].z;
    }

    // Histogram equalization and contrast stretching
    z = clamp((z - zRange.x) / zRange.y, 0.f, 1.f);

    if (radiusDenoise > 0) {
        // Grayshift xy based on noise level
        float shiftFactor = clamp((distxy - 0.1f) * 2.f, 0.f, 1.f);

        // Shift towards D50 white
        xy = shiftFactor * vec2(0.345703f, 0.358539f) + (1.f - shiftFactor) * xy;

        // Reduce z by at most a third
        z *= clamp(1.1f - shiftFactor, 0.67f, 1.f);
    }

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
    vec3 XYZ = xyYtoXYZ(intermediate);

    vec3 proPhoto = clamp(XYZtoProPhoto * XYZ, 0.f, 1.f);
    proPhoto = tonemap(proPhoto);

    vec3 sRGB = clamp(proPhotoToSRGB * proPhoto, 0.f, 1.f);
    sRGB = gammaCorrectPixel(sRGB);

    return sRGB;
}

const vec3 gMonoMult = vec3(0.299f, 0.587f, 0.114f);
vec3 saturate(vec3 rgb) {
    float maxv = max(max(rgb.r, rgb.g), rgb.b);
    float minv = min(min(rgb.r, rgb.g), rgb.b);
    if (maxv > minv) {
        float s = maxv - minv; // [0,1]
        float saturation = max(saturationCurve.x - saturationCurve.y * pow(s, saturationCurve.z), 1.f);
        rgb = rgb * saturation + dot(rgb, gMonoMult) * (1.f - saturation);
    }
    return rgb;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) + outOffset;
    xy.y += yOffset;

    // Sharpen and denoise value
    vec3 intermediate = processPatch(xy);

    // Convert to final colorspace
    vec3 sRGB = applyColorspace(intermediate);

    // Add saturation
    sRGB = saturate(sRGB);
    sRGB = clamp(sRGB, 0.f, 1.f);

    color = vec4(sRGB, 1.f);
}
