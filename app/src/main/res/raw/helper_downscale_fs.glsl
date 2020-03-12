#version 300 es

precision mediump float;

uniform sampler2D buf;
uniform ivec2 bufSize;

uniform int scale;

// Out
out float outPixel;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) * scale;
    float res;

    for (int i = 0; i < (scale * scale); i++) {
        ivec2 pos = xy + ivec2(i % scale, i / scale);
        res += texelFetch(inBuffer, pos, 0).z;
    }

    outPixel = res / float(scale * scale);
}
