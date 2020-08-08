#version 300 es

#define PI 3.1415926535897932384626433832795f
#define hPI 1.57079632679489661923f
#define qPI 0.785398163397448309616f

precision mediump float;

uniform sampler2D intermediateBuffer;
uniform int intermediateWidth;
uniform int intermediateHeight;

uniform sampler2D noiseTex;

uniform int radiusDenoise;
uniform vec3 sigma;
uniform float sharpenFactor;

out vec3 result;

#include load3x3v3

void main() {
    ivec2 xyPos = ivec2(gl_FragCoord.xy);

    vec3[9] impatch = load3x3(xyPos, 2, intermediateBuffer);
    vec3 mid = impatch[4];
    result = mid;
    return;

    // Take unfiltered xy and z as starting point.
    vec2 xy = mid.xy;
    float z = mid.z;

    // Calculate stddev for patch
    vec3 mean;
    for (int i = 0; i < 9; i++) {
        mean += impatch[i];
    }
    mean /= 9.f;
    vec3 sigmaLocal;
    for (int i = 0; i < 9; i++) {
        vec3 diff = mean - impatch[i];
        sigmaLocal += diff * diff;
    }
    sigmaLocal = max(sqrt(sigmaLocal / 9.f), sigma);

    vec3 minxyz = impatch[0].xyz, maxxyz = minxyz;
    for (int i = 1; i < 9; i++) {
        minxyz = min(minxyz, impatch[i]);
        maxxyz = max(maxxyz, impatch[i]);
    }
    float distxy = distance(minxyz.xy, maxxyz.xy);
    float distz = distance(minxyz.z, maxxyz.z);

    /**
    CHROMA NOISE REDUCE
    **/

    // Thresholds
    float thExclude = 1.5f;
    float thStop = 2.25f;

    // Expand in a plus
    vec3 midDivSigma = mid / sigmaLocal;
    vec3 neighbour;
    vec3 sum = mid;
    int coord, bound, count, totalCount = 1, shiftFactor = 16;
    float dist;

    float lastMinAngle = 0.f;
    for (float radius = 1.f; radius <= float(radiusDenoise); radius += 1.f) {
        float expansion = radius * 2.f;
        float maxDist = 0.f;

        float minAngle = lastMinAngle;
        float minDist = thStop;

        // Four samples for every radius
        for (float angle = -hPI; angle < hPI; angle += qPI) {
            // Reduce angle as radius grows
            float realAngle = lastMinAngle + angle / pow(radius, 0.5f);
            ivec2 c = xyPos + ivec2(
            int(round(expansion * cos(realAngle))),
            int(round(expansion * sin(realAngle)))
            );

            // Don't go out of bounds
            if (c.x >= 0 && c.x < intermediateWidth && c.y >= 0 && c.y < intermediateHeight - 1) {
                neighbour = texelFetch(intermediateBuffer, c, 0).xyz;
                dist = distance(midDivSigma, neighbour / sigmaLocal);
                if (dist < minDist) {
                    minDist = dist;
                    minAngle = realAngle;
                }
                if (dist < thExclude) {
                    sum += neighbour;
                    totalCount++;
                }
            }
        }
        // No direction left to continue, stop averaging
        if (minDist >= thStop) {
            break;
        }
        // Keep track of the best angle
        lastMinAngle = minAngle;
    }

    float noise = texelFetch(noiseTex, xyPos, 0).x;
    result.xy = sum.xy / float(totalCount);
    result.z = mix(z, sum.z / float(totalCount), min(noise * 1.65f, 1.f));
}
