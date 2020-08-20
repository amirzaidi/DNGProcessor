#version 300 es

precision mediump float;

uniform sampler2D bufDenoisedHighRes;
uniform sampler2D bufDenoisedMediumRes;
uniform sampler2D bufDenoisedLowRes;
uniform sampler2D bufNoisyMediumRes;
uniform sampler2D bufNoisyLowRes;
uniform sampler2D noiseTexMediumRes;
uniform sampler2D noiseTexLowRes;

out vec3 result;

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    vec3 highRes, mediumRes, lowRes;

    highRes = texelFetch(bufDenoisedHighRes, xyCenter, 0).xyz;

    mediumRes = texelFetch(bufDenoisedMediumRes, xyCenter, 0).xyz;
    mediumRes -= texelFetch(bufNoisyMediumRes, xyCenter, 0).xyz;

    lowRes = texelFetch(bufDenoisedLowRes, xyCenter, 0).xyz;
    lowRes -= texelFetch(bufNoisyLowRes, xyCenter, 0).xyz;

    mediumRes *= min(texelFetch(noiseTexMediumRes, xyCenter / 4, 0).xyz * 32.f, 1.f);
    lowRes *= min(texelFetch(noiseTexLowRes, xyCenter / 4, 0).xyz * 64.f, 1.f);

    result = highRes + mediumRes + lowRes;
}
