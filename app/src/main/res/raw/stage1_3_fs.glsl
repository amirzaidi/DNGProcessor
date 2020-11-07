#version 300 es

precision mediump float;

uniform sampler2D rawBuffer;
uniform sampler2D greenBuffer;
uniform int rawWidth;
uniform int rawHeight;

// Sensor and picture variables
uniform sampler2D gainMap;
uniform int cfaPattern; // The Color Filter Arrangement pattern used
uniform vec4 neutralLevel; // Neutrallevel of sensor
uniform vec3 neutralPoint; // The camera neutral

// Transform
uniform mat3 sensorToXYZ; // Color transform from sensor to XYZ.

const int demosaicArray[16] = int[](
    0, 1, 2, 3,
    1, 0, 3, 2,
    2, 3, 0, 1,
    3, 2, 1, 0
);

// Out
out vec3 intermediate;

#include xyztoxyy

vec4 getCross(sampler2D buf, ivec2 xy) {
    return vec4(
        texelFetch(buf, xy + ivec2(-1, -1), 0).x,
        texelFetch(buf, xy + ivec2(1, -1), 0).x,
        texelFetch(buf, xy + ivec2(-1, 1), 0).x,
        texelFetch(buf, xy + ivec2(1, 1), 0).x
    );
}

vec2 getHorz(sampler2D buf, ivec2 xy) {
    return vec2(
        texelFetch(buf, xy + ivec2(-1, 0), 0).x,
        texelFetch(buf, xy + ivec2(1, 0), 0).x
    );
}

vec2 getVert(sampler2D buf, ivec2 xy) {
    return vec2(
        texelFetch(buf, xy + ivec2(0, -1), 0).x,
        texelFetch(buf, xy + ivec2(0, 1), 0).x
    );
}

float getScale(vec2 raw, vec2 green, float minG) {
    return dot(raw / max(green, minG), vec2(0.5f));
}

float getScale(vec4 raw, vec4 green, float minG) {
    return dot(raw / max(green, minG), vec4(0.25f));
}

// Apply bilinear-interpolation to demosaic
vec3 demosaic(ivec2 xy) {
    int x = xy.x;
    int y = xy.y;

    int index = (x & 1) | ((y & 1) << 1);
    index |= (cfaPattern << 2);
    vec3 pRGB;
    int pxType = demosaicArray[index];

    // We already computed green
    pRGB.g = texelFetch(greenBuffer, xy, 0).x;
    float minG = 0.01f;
    float g = max(pRGB.g, minG);

    if (pxType == 0 || pxType == 3) {
        float p = texelFetch(rawBuffer, xy, 0).x;
        float cross = g * getScale(
            getCross(rawBuffer, xy),
            getCross(greenBuffer, xy),
            minG
        );
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
        float horz = g * getScale(
            getHorz(rawBuffer, xy),
            getHorz(greenBuffer, xy),
            minG
        );
        float vert = g * getScale(
            getVert(rawBuffer, xy),
            getVert(greenBuffer, xy),
            minG
        );
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


vec3 convertSensorToIntermediate(ivec2 xy, vec3 sensor) {
    // Use gainmap to increase dynamic range.
    vec2 xyInterp = vec2(float(xy.x) / float(rawWidth), float(xy.y) / float(rawHeight));
    vec4 gains = texture(gainMap, xyInterp);
    vec3 neutralScaled = min(min(gains.x, gains.y), min(gains.z, gains.w)) * neutralPoint;

    vec3 npf = sensor / neutralScaled;
    sensor = min(sensor, neutralScaled);

    // When both red and blue channels are above white point, assume green is too
    // So extend dynamic range by scaling white point
    // Use a bias so only high green values become higher
    // In highlights, bias should be one
    float bias = npf.g * npf.g * npf.g;
    sensor *= mix(1.f, max(npf.r + npf.b, 2.f) * 0.5f, bias);

    vec3 XYZ = sensorToXYZ * sensor;
    vec3 intermediate = XYZtoxyY(XYZ);

    return intermediate;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 xyClamped = clamp(xy, ivec2(1), ivec2(rawWidth, rawHeight) - 2);

    vec3 sensor = demosaic(xyClamped);
    intermediate = convertSensorToIntermediate(xy, sensor);
}
