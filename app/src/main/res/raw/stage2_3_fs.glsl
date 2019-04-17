#version 300 es

// Blurring shader

precision mediump float;

uniform sampler2D buf;
uniform ivec2 bufSize;
uniform int mode;
uniform ivec2 dir;

const float s = 15.f;
const int w = 61;
const int b = 32;
const int j = 8;

const float c1 = 0.25f, c2 = 0.5f, c3 = 0.75f;

// Out
out vec3 result;

float unscaledGaussian(int dx, float s) {
    return exp(-0.5f * pow(float(dx) / s, 2.f));
}

ivec2 clamppos(ivec2 pos) {
    return clamp(pos, ivec2(4, 4), bufSize - 5);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    float totalGauss, minv = 999.f, maxv = 0.f;
    if (mode == 0) {
        float lv;
        for (int i = 0; i < w; i++) {
            ivec2 pos = clamppos(xy + (i - w / 2) * dir * j);
            lv = texelFetch(buf, pos, 0).z;
            minv = min(lv, minv);
            maxv = max(lv, maxv);
        }

        float h[b], bins = float(b + 1), vrange = maxv - minv;

        for (int i = 0; i < w; i++) {
            float gauss = unscaledGaussian(i - w / 2, s);
            totalGauss += gauss;

            ivec2 pos = clamppos(xy + (i - w / 2) * dir * j);
            lv = texelFetch(buf, pos, 0).z;

            float normv = (lv - minv) / vrange;
            int bin = min(b - 1, int(normv * bins));
            h[bin] += gauss;
        }

        // Normalize
        for (int i = 0; i < b; i++) {
            h[i] /= totalGauss;
        }

        // Transform into cumulative
        for (int i = 1; i < b; i++) {
            h[i] = h[i] + h[i - 1];
        }

        float pv, cv, dv, iv, b1, b2, b3;
        for (int i = 0; i < b; i++) {
            if (i > 0) {
                pv = h[i - 1];
            }
            cv = h[i];
            dv = cv - pv;
            iv = float(i);
            if (pv < c1 && cv >= c1) {
                b1 = iv + (c1 - pv) / dv;
            }
            if (pv < c2 && cv >= c2) {
                b2 = iv + (c2 - pv) / dv;
            }
            if (pv < c3 && cv >= c3) {
                b3 = iv + (c3 - pv) / dv;
            }
        }

        result.xyz = vec3(b1, b2, b3) / bins * vrange + minv;
    } else {
        vec3 distribution;
        for (int i = 0; i < w; i++) {
            ivec2 pos = clamppos(xy + (i - w / 2) * dir * j);
            float gauss = unscaledGaussian(i - w / 2, s);
            totalGauss += gauss;
            distribution += gauss * texelFetch(buf, pos, 0).xyz;
        }
        result = distribution / totalGauss;
    }
}
