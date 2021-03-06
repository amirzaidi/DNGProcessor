#version 300 es
#define PI 3.1415926535897932384626433832795f

precision mediump float;
precision mediump usampler2D;

uniform sampler2D highRes;
uniform int intermediateWidth;
uniform int intermediateHeight;

uniform sampler2D weakBlur;
uniform sampler2D mediumBlur;
uniform sampler2D strongBlur;

uniform int yOffset;

uniform bool lce;
uniform vec2 adaptiveSaturation;

// Sensor and picture variables
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve

// Transform
uniform mat3 XYZtoProPhoto; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 proPhotoToSRGB; // Color transform from wide-gamut colorspace to sRGB

// Post processing
uniform float sharpenFactor;
uniform sampler2D saturation;
uniform float satLimit;

// Dithering
uniform usampler2D ditherTex;
uniform int ditherSize;

// Size
uniform ivec2 outOffset;

// Out
out vec4 color;

#include sigmoid
#include xyytoxyz
#include xyztoxyy
#include gamma

vec3 processPatch(ivec2 xyPos) {
    vec3 xyY = texelFetch(highRes, xyPos, 0).xyz;

    if (lce) {
        float zMediumBlur = texelFetch(mediumBlur, xyPos, 0).x;
        if (zMediumBlur > 0.0001f) {
            float zWeakBlur = texelFetch(weakBlur, xyPos, 0).x;
            xyY.z *= zWeakBlur / zMediumBlur;
        }

        float zStrongBlur = texelFetch(strongBlur, xyPos, 0).x;
        if (zStrongBlur > 0.0001f) {
            xyY.z *= sqrt(zMediumBlur / zStrongBlur);
        }
    }

    return clamp(xyY, 0.f, 1.f);
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

float saturateToneMap(float inSat) {
    return inSat < 0.001f
        ? inSat
        : max(inSat, 1.03f * pow(inSat, 1.02f));
}

vec3 saturate(vec3 rgb) {
    float maxv = max(max(rgb.r, rgb.g), rgb.b);
    float minv = min(min(rgb.r, rgb.g), rgb.b);
    if (maxv > minv) {
        vec3 hsv = rgb2hsv(rgb);
        // Assume saturation map is either constant or has 8+1 values, where the last wraps around
        float f = texture(saturation, vec2(hsv.x * (16.f / 18.f) + (1.f / 18.f), 0.5f)).x;
        hsv.y = sigmoid(saturateToneMap(hsv.y) * f, satLimit);
        //float adaptStrength = adaptiveSaturation.x * (hsv.z * (1.f - hsv.z))
        //    * pow(hsv.y, adaptiveSaturation.y);
        //hsv.z = mix(hsv.z, 0.5f, min(adaptStrength, 0.1f));
        rgb = hsv2rgb(hsv);
    }
    return rgb;
}

// Apply gamma correction to each color channel in RGB pixel
vec3 gammaCorrectPixel(vec3 rgb) {
    return vec3(gammaEncode(rgb.r), gammaEncode(rgb.g), gammaEncode(rgb.b));
}

uint hash(uint x) {
    x += (x << 10u);
    x ^= (x >> 6u);
    x += (x << 3u);
    x ^= (x >> 11u);
    x += (x << 15u);
    return x;
}

int hash(int x) {
    return int(hash(uint(x)) >> 1);
}

ivec2 hash(ivec2 xy) {
    int hashTogether = hash(xy.x ^ xy.y);
    return ivec2(hash(xy.x ^ hashTogether), hash(xy.y ^ hashTogether));
}

vec3 dither(vec3 rgb, ivec2 xy) {
    int dither = int(texelFetch(ditherTex, hash(xy) % ditherSize, 0).x);
    float noise = float(dither >> 8) / 255.f; // [0, 1]
    noise = (noise - 0.5f) / 255.f; // At most half a RGB value of noise.
    return clamp(rgb + noise, 0.f, 1.f);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) + outOffset;
    xy.y += yOffset;

    // Sharpen and denoise value
    vec3 intermediate = processPatch(xy);

    // Convert to XYZ space
    vec3 XYZ = xyYtoXYZ(intermediate);

    // Convert to ProPhoto space
    vec3 proPhoto = XYZtoProPhoto * XYZ;

    // Convert to sRGB space
    vec3 sRGB = clamp(proPhotoToSRGB * proPhoto, 0.f, 1.f);

    // Add saturation and tonemap.
    sRGB = saturate(sRGB);
    sRGB = tonemap(sRGB);

    // Gamma correct and dither at the end.
    color = vec4(dither(gammaCorrectPixel(sRGB), xy), 1.f);
}
