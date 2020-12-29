/* sRGB Gamma Function */
float gammaEncode(float x) {
    return x <= 0.0031308f
        ? x * 12.92f
        : 1.055f * pow(x, 0.4166667f) - 0.055f;
}

/* Inverse */
float gammaDecode(float x) {
    return x <= 0.0404500f
        ? x * 0.0773994f
        : pow(0.9478673f * (x + 0.055f), 2.4f);
}
