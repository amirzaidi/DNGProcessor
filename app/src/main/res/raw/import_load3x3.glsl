float[9] load3x3(ivec2 xy, sampler2D buf) {
    float outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = texelFetch(buf, xy + ivec2((i % 3) - 1, (i / 3) - 1), 0).x;
    }
    return outputArray;
}
