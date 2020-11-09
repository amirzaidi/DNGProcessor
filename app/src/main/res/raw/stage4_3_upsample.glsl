#version 300 es

precision mediump float;

uniform sampler2D buf;

out float result;

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    ivec2 xyDownscaled = xyCenter / 2;
    ivec2 xyAlign = xyCenter % 2;

    // We always upsample from a texture that is larger or the proper size,
    // so do not worry about clamping coordinates.
    float topLeft = texelFetch(buf, xyDownscaled, 0).x;
    float topRight, bottomLeft;
    if (xyAlign.x == 1) {
        topRight = texelFetch(buf, xyDownscaled + ivec2(1, 0), 0).x;
    }
    if (xyAlign.y == 1) {
        bottomLeft = texelFetch(buf, xyDownscaled + ivec2(0, 1), 0).x;
    }

    // Linear interpolation over 2x upscaling is the same as bicubic or cosine interpolation,
    // as all are the same: x=0 -> y=0, x=0.5 -> y=0.5, x=1 -> y=1.
    // Therefore this should not introduce artifacts.
    int pxFour = 2 * xyAlign.y + xyAlign.x;
    switch (pxFour) {
        case 0: // TL
            result = topLeft;
            break;
        case 1: // TR
            result = (topLeft + topRight) * 0.5f;
            break;
        case 2: // BL
            result = (topLeft + bottomLeft) * 0.5f;
            break;
        case 3: // BR
            float bottomRight = texelFetch(buf, xyDownscaled + ivec2(1, 1), 0).x;
            result = (topLeft + topRight + bottomLeft + bottomRight) * 0.25f;
            break;
    }
}
