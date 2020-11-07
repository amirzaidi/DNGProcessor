#version 300 es

precision mediump float;

uniform sampler2D upscaled;
uniform sampler2D downscaled;
uniform sampler2D laplace;
uniform int level;

out vec3 result;

float compress(float z, int lvl) {
    return z / (0.5f * sqrt(float(11 - lvl) * abs(z)) + 1.f);
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    vec3 base = texelFetch(upscaled, xyCenter, 0).xyz;
    vec3 diff = texelFetch(laplace, xyCenter, 0).xyz;

    // Largest feature scale.
    if (level == 8) {
        base.z = compress(base.z, 9);
    }

    // Each following feature scale.
    diff.z = compress(diff.z, level);

    vec3 tmp2 = base + diff;
    if (level == 0) {
        // Make everything brighter after compressing down.
        tmp2.z = clamp(tmp2.z * 1.5f, 0.f, 1.f);
    }
    result = tmp2;
}
