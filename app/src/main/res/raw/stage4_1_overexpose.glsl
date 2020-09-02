#version 300 es

precision mediump float;

uniform sampler2D buf;

uniform float factor;

out vec3 result;

#include sigmoid
#include xyztoxyy
#include xyytoxyz

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    vec3 XYZ = texelFetch(buf, xyCenter, 0).xyz;
    vec3 xyY = XYZtoxyY(XYZ);
    xyY.z *= factor;
    //xyY.z = min(xyY.z, 1.f);
    xyY.z = sigmoid(xyY.z, 0.9f);
    result = xyYtoXYZ(xyY);
}
