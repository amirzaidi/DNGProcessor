#version 300 es
#define PI 3.1415926535897932384626433832795f
#define hPI 1.57079632679489661923f
#define qPI 0.785398163397448309616f

precision mediump float;

uniform sampler2D intermediateBuffer;
uniform int intermediateWidth;
uniform int intermediateHeight;

uniform sampler2D weakBlur;
uniform sampler2D strongBlur;

uniform int yOffset;

uniform int radiusDenoise;
uniform bool lce;
uniform vec2 adaptiveSaturation;

// Sensor and picture variables
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve

// Transform
uniform mat3 XYZtoProPhoto; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 proPhotoToSRGB; // Color transform from wide-gamut colorspace to sRGB

uniform vec3 sigma;

// Post processing
uniform float sharpenFactor;
uniform sampler2D saturation;
uniform float satLimit;

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

float[9] load3x3z(ivec2 xy) {
    float outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = texelFetch(intermediateBuffer, xy + ivec2((i % 3) - 1, (i / 3) - 1), 0).z;
    }
    return outputArray;
}

float sigmoid(float val, float transfer) {
    if (val > transfer) {
        // This variable maps the cut off point in the linear curve to the sigmoid
        float a = log((1.f + transfer) / (1.f - transfer)) / transfer;

        // Transform val using the sigmoid curve
        val = 2.f / (1.f + exp(-a * val)) - 1.f;
    }
    return val;
}

vec3 processPatch(ivec2 xyPos) {
    vec3[9] impatch = load3x3(xyPos, 2);
    vec3 mid = impatch[4];

    // Take unfiltered xy and z as starting point.
    vec2 xy = mid.xy;
    float z = mid.z;

    // Calculate stddev for patch
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

    // Thresholds
    float thExclude = 1.5f;
    float thStop = 2.25f;

    // Expand in a plus
    vec3 midDivSigma = mid / sigmaLocal;
    vec3 neighbour;
    vec3 sum = mid;
    int coord, bound, count, totalCount = 1, shiftFactor = 16;
    float dist;

    float lastMinAngle = 0.f;
    for (float radius = 1.f; radius <= float(radiusDenoise); radius += 1.f) {
        float expansion = radius * 2.f;
        float maxDist = 0.f;

        float minAngle = lastMinAngle;
        float minDist = thStop;

        // Four samples for every radius
        for (float angle = -hPI; angle < hPI; angle += qPI) {
            // Reduce angle as radius grows
            float realAngle = lastMinAngle + angle / pow(radius, 0.5f);
            ivec2 c = xyPos + ivec2(
            int(round(expansion * cos(realAngle))),
            int(round(expansion * sin(realAngle)))
            );

            // Don't go out of bounds
            if (c.x >= 0 && c.x < intermediateWidth && c.y >= 0 && c.y < intermediateHeight - 1) {
                neighbour = texelFetch(intermediateBuffer, c, 0).xyz;
                dist = distance(midDivSigma, neighbour / sigmaLocal);
                if (dist < minDist) {
                    minDist = dist;
                    minAngle = realAngle;
                }
                if (dist < thExclude) {
                    sum += neighbour;
                    totalCount++;
                }
            }
        }
        // No direction left to continue, stop averaging
        if (minDist >= thStop) {
            break;
        }
        // Keep track of the best angle
        lastMinAngle = minAngle;
    }

    xy = sum.xy / float(totalCount);
    z = sum.z / float(totalCount);

    /**
    LUMA DENOISE AND SHARPEN
    **/
    if (sharpenFactor > 0.f) {
        float[9] impz = load3x3z(xyPos);
        float lx = impz[0] - impz[2] + (impz[3] - impz[5]) * 2.f + impz[6] - impz[8];
        float ly = impz[0] - impz[6] + (impz[1] - impz[7]) * 2.f + impz[2] - impz[8];
        float l = sqrt(lx * lx + ly * ly);

        // Sum of difference with all pixels nearby
        float dz = mid.z * 13.f;
        for (int i = 0; i < 9; i++) {
            if (i % 2 == 0) {
                dz -= impz[i];
            } else {
                dz -= 2.f * impz[i];
            }
        }

        dz = sign(dz) * sigmoid(abs(dz) * 0.5f, 0.25f) * 2.f;
        z += sharpenFactor * (0.05f + min(l, 0.4f)) * dz;

        if (lce) {
            // Local contrast enhancement
            float zWeakBlur = texelFetch(weakBlur, xyPos, 0).x;
            float zStrongBlur = texelFetch(strongBlur, xyPos, 0).x;
            z += sharpenFactor * 7.5f * (zWeakBlur - zStrongBlur);
        }
    } else if (sharpenFactor < 0.f) {
        z += sharpenFactor * (z - sum.z / float(totalCount));
    }

    /**
    DENOISE BY DESATURATION
    **/
    if (radiusDenoise > 0) {
        // Grayshift xy based on noise level
        float shiftFactor = clamp((distxy - 0.15f) * 1.75f, 0.f, 1.f);

        // Shift towards D50 white
        xy = mix(xy, vec2(0.345703f, 0.358539f), shiftFactor);

        // Reduce z by at most a third
        z *= clamp(1.1f - shiftFactor, 0.67f, 1.f);
    }

    return clamp(vec3(xy, z), 0.f, 1.f);
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
    minmax = pow(minmax, vec2(3.f)) * toneMapCoeffs.x +
    pow(minmax, vec2(2.f)) * toneMapCoeffs.y +
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

// Source: https://lolengine.net/blog/2013/07/27/rgb-to-hsv-in-glsl
// All components are in the range [0…1], including hue.
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.f, -1.f / 3.f, 2.f / 3.f, -1.f);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.f * d + e)), d / (q.x + e), q.x);
}

// All components are in the range [0…1], including hue.
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.f, 2.f / 3.f, 1.f / 3.f, 3.f);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.f - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.f, 1.f), c.y);
}

vec3 saturate(vec3 rgb) {
    float maxv = max(max(rgb.r, rgb.g), rgb.b);
    float minv = min(min(rgb.r, rgb.g), rgb.b);
    if (maxv > minv) {
        vec3 hsv = rgb2hsv(rgb);
        // Assume saturation map is either constant or has 8+1 values, where the last wraps around
        float f = texture(saturation, vec2(hsv.x * (16.f / 18.f) + (1.f / 18.f), 0.5f)).x;
        hsv.y = sigmoid(hsv.y * f, satLimit);
        hsv.z = mix(hsv.z, 0.5f, adaptiveSaturation.x * hsv.z * pow(hsv.y, adaptiveSaturation.y));
        rgb = hsv2rgb(hsv);
    }
    return rgb;
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

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) + outOffset;
    xy.y += yOffset;

    // Sharpen and denoise value
    vec3 intermediate = processPatch(xy);

    // Convert to XYZ space
    vec3 XYZ = xyYtoXYZ(intermediate);

    // Convert to ProPhoto space
    vec3 proPhoto = clamp(XYZtoProPhoto * XYZ, 0.f, 1.f);

    // Convert to sRGB space
    vec3 sRGB = clamp(proPhotoToSRGB * proPhoto, 0.f, 1.f);

    // Add saturation
    sRGB = saturate(sRGB);

    // Apply tonemap after gamma correction
    color = vec4(tonemap(gammaCorrectPixel(sRGB)), 1.f);
}
