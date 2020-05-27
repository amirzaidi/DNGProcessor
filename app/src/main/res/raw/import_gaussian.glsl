float unscaledGaussian(float d, float s) {
    return exp(-0.5f * pow(d / s, 2.f));
}
