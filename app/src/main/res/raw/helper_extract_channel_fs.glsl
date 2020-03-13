#version 300 es

precision mediump float;

uniform sampler2D buf;
uniform vec4 mult;

// Out
out float outPixel;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    outPixel = dot(mult, texelFetch(buf, xy, 0));
}
