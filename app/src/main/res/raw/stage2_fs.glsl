#version 320 es

precision mediump float;

uniform sampler2D intermediateBuffer;
uniform int intermediateWidth;
uniform int intermediateHeight;

// Sensor and picture variables
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve

// Transform
uniform mat3 intermediateToProPhoto; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 proPhotoToSRGB; // Color transform from wide-gamut colorspace to sRGB

// Post processing
uniform vec3 postProcCurve;
uniform float saturationFactor;

// Size
uniform ivec2 outOffset;

// Out
out vec4 color;

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

    vec3 intermediate = texelFetch(intermediateBuffer, xy, 0).rgb;
    vec3 sRGB = applyColorspace(intermediate);

    sRGB = applyCurve(sRGB);
    sRGB = saturate(sRGB);
    sRGB = clamp(sRGB, 0.f, 1.f);

    color = vec4(sRGB, 1.f);
}
