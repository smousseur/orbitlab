#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Lighting.glsllib"

uniform vec4 g_LightData[NB_LIGHTS];

uniform float m_FallOffFactor;

// Eclipse occulter (docs/eclipses/01-decoupage.md): all in this geometry's own world space, the
// same space vPosWorld is in — never the camera's, so no view-space conversion is needed CPU-side.
// OccluderRadius defaults to 0, which the branch structure below always resolves to full
// illumination without ever reaching the partial-overlap division (see EclipseGeometry.java, whose
// Java implementation this mirrors and is tested against Orekit's own EclipseDetector).
uniform vec3 m_OccluderPosition;
uniform float m_OccluderRadius;
uniform vec3 m_SunDirection;
uniform float m_SunApparentRadius;

#ifdef DIFFUSEMAP
uniform sampler2D m_DiffuseMap;
#endif

varying vec2 texCoord;
varying vec3 vNormal;
varying vec3 vPos;
varying vec3 vPosWorld;
varying vec3 AmbientSum;
varying vec4 DiffuseSum;

// Lambert cosine, with a Hermite ramp that only softens the last stretch before
// the shadow. The cosine is what makes a lit sphere read as a sphere: without it
// the disc is flat out to acos(fallOff) and every degree of shading is crowded
// into the rest, which moves the light/dark edge the eye sees well inside the
// geometric terminator. The ramp multiplies rather than replaces it, so the
// boundary stays anchored at N.L = 0 and fallOff only says how wide the softened
// band is — keep it small (0.1 - 0.3), since it darkens the twilight zone on top
// of the cosine that is already darkening it.
float lightFalloff(in vec3 n, in vec3 l, in float fallOff) {
    float ndotl = dot(n, l);
    // Guard against smoothstep(0, 0, x) which is undefined in GLSL.
    return ndotl * smoothstep(0.0, max(fallOff, 1e-5), ndotl);
}

// Fraction of the Sun's disk still visible past the occulter, from the area of intersection of two
// circles (the occulter's apparent disk and the Sun's) separated by the angle between them as seen
// from this fragment. 1.0 = fully lit, 0.0 = totality, in between = penumbra.
float eclipseIllumination(in vec3 fragPosWorld) {
    vec3 toOccluder = m_OccluderPosition - fragPosWorld;
    float occluderDist = length(toOccluder);
    if (occluderDist < 1e-6) {
        return 1.0;
    }
    vec3 occluderDir = toOccluder / occluderDist;
    float ro = asin(clamp(m_OccluderRadius / occluderDist, 0.0, 1.0));
    float rs = m_SunApparentRadius;
    float d = acos(clamp(dot(occluderDir, m_SunDirection), -1.0, 1.0));

    if (d >= rs + ro) {
        return 1.0;
    }
    if (d <= abs(rs - ro)) {
        return ro >= rs ? 0.0 : 1.0 - (ro * ro) / (rs * rs);
    }

    float d2 = d * d;
    float rs2 = rs * rs;
    float ro2 = ro * ro;
    float part1 = rs2 * acos(clamp((d2 + rs2 - ro2) / (2.0 * d * rs), -1.0, 1.0));
    float part2 = ro2 * acos(clamp((d2 + ro2 - rs2) / (2.0 * d * ro), -1.0, 1.0));
    float part3 = 0.5 * sqrt(max(0.0, (-d + rs + ro) * (d + rs - ro) * (d - rs + ro) * (d + rs + ro)));
    float overlapArea = part1 + part2 - part3;
    float occludedFraction = overlapArea / (3.14159265359 * rs2);

    return clamp(1.0 - occludedFraction, 0.0, 1.0);
}

void main() {
    vec3 normal = normalize(vNormal);
    if (!gl_FrontFacing) {
        normal = -normal;
    }

#ifdef DIFFUSEMAP
    vec4 diffuseColor = texture2D(m_DiffuseMap, texCoord);
#else
    vec4 diffuseColor = vec4(1.0);
#endif

    vec3 color = AmbientSum * diffuseColor.rgb;
    float eclipse = eclipseIllumination(vPosWorld);

    for (int i = 0; i < NB_LIGHTS; i += 3) {
        vec4 lightColor = g_LightData[i];
        vec4 lightData1 = g_LightData[i + 1];

        vec4 lightDir;
        vec3 lightVec;
        lightComputeDir(vPos, lightColor.w, lightData1, lightDir, lightVec);

        float diff = lightFalloff(normal, lightDir.xyz, m_FallOffFactor);
        color += DiffuseSum.rgb * lightColor.rgb * diffuseColor.rgb * diff * lightDir.w * eclipse;
    }

    gl_FragColor = vec4(color, DiffuseSum.a * diffuseColor.a);
}
