#version 300 es

precision mediump float;

uniform sampler2D upscaled;
uniform sampler2D downscaled;
uniform sampler2D laplace;
uniform int level;

out vec3 result;

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    vec3 base = texelFetch(upscaled, xyCenter, 0).xyz;
    vec3 diff = texelFetch(laplace, xyCenter, 0).xyz;

    // Largest feature scale.
    if (level == 8) {
        float fullz = texelFetch(downscaled, xyCenter, 0).z;
        base.z *= 1.f - fullz * 0.2f; // Bring down bright areas.
    }

    if (diff.z > 0.001f) {
        float sgn = sign(diff.z);
        float powf = 1.f - 0.02f * float(level + 1);
        diff.z = sgn * pow(10.f * abs(diff.z), powf) * 0.1f; // Compression of lows.
        diff.z *= (1.4f - 0.1f * float(abs(level - 4))); // Increase mids.
    }

    result = base + diff;
}
