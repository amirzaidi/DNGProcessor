#version 300 es

precision mediump float;

uniform sampler2D intermediateBuffer;
uniform ivec2 minxy;
uniform ivec2 maxxy;

// Out
out vec3 intermediate;

ivec2 mirrorOOBCoords(ivec2 coords) {
    if (coords.x < minxy.x)
        coords.x = 2 * minxy.x - coords.x;
    else if (coords.x > maxxy.x)
        coords.x = 2 * maxxy.x - coords.x;

    if (coords.y < minxy.y)
        coords.y = 2 * minxy.y - coords.y;
    else if (coords.y > maxxy.y)
        coords.y = 2 * maxxy.y - coords.y;

    return coords;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    intermediate = texelFetch(intermediateBuffer, mirrorOOBCoords(xy), 0).xyz;
}
