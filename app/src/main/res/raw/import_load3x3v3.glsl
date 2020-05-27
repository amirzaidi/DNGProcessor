vec3[9] load3x3(ivec2 xy, int n, sampler2D buf) {
    vec3 outputArray[9];
    for (int i = 0; i < 9; i++) {
        outputArray[i] = texelFetch(buf, xy + n * ivec2((i % 3) - 1, (i / 3) - 1), 0).xyz;
    }
    return outputArray;
}
