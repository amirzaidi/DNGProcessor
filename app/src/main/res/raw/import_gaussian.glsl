float unscaledGaussian(float d, float s) {
    return exp(-0.5f * pow(d / s, 2.f));
}

vec3 unscaledGaussian(vec3 d, vec3 s) {
    return exp(-0.5f * pow(d / s, vec3(2.f)));
}
