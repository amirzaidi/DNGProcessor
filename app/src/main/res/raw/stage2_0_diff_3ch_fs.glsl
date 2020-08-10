#version 300 es

precision mediump float;

uniform sampler2D highResBuf;
uniform sampler2D lowResBuf;
uniform int samplingFactor;

out vec3 result;

vec3 getValBilinear(sampler2D tex, ivec2 xyPos, int factor) {
    ivec2 xyPos00 = xyPos / factor;
    ivec2 xyPos01 = xyPos00 + ivec2(1, 0);
    ivec2 xyPos10 = xyPos00 + ivec2(0, 1);
    ivec2 xyPos11 = xyPos00 + ivec2(1, 1);

    vec3 xyVal00 = texelFetch(tex, xyPos00, 0).xyz;
    vec3 xyVal01 = texelFetch(tex, xyPos01, 0).xyz;
    vec3 xyVal10 = texelFetch(tex, xyPos10, 0).xyz;
    vec3 xyVal11 = texelFetch(tex, xyPos11, 0).xyz;

    ivec2 xyShift = xyPos % factor;
    vec2 xyShiftf = vec2(xyShift.x, xyShift.y) / float(factor);

    vec3 xyVal0 = mix(xyVal00, xyVal01, xyShiftf.x);
    vec3 xyVal1 = mix(xyVal10, xyVal11, xyShiftf.x);

    return mix(xyVal0, xyVal1, xyShiftf.y);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);

    vec3 highRes = texelFetch(highResBuf, xy, 0).xyz;
    //vec3 lowRes = texture(lowResBuf, (vec2(xy.x, xy.y) + 0.5f) / highResBufSize).xyz;
    vec3 lowRes = getValBilinear(lowResBuf, xy, samplingFactor);
    //vec3 lowRes = texelFetch(lowResBuf, xy / 2, 0).xyz;

    result = highRes - lowRes;
    //result = highRes;
}
