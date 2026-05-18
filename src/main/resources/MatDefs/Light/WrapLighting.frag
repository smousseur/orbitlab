#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Lighting.glsllib"

uniform vec4 g_LightData[NB_LIGHTS];

uniform float m_FallOffFactor;

#ifdef DIFFUSEMAP
uniform sampler2D m_DiffuseMap;
#endif

varying vec2 texCoord;
varying vec3 vNormal;
varying vec3 vPos;
varying vec3 AmbientSum;
varying vec4 DiffuseSum;

// Twilight falloff on the LIT side only. Shadow boundary stays anchored at
// N.L = 0 for any fallOff: smoothstep(0, fallOff, N.L) returns 0 for N.L <= 0
// (strict shadow), ramps via Hermite over N.L in [0, fallOff] (twilight band),
// and saturates at 1 beyond. fallOff = 0 collapses to a hard step.
float lightFalloff(in vec3 n, in vec3 l, in float fallOff) {
    float ndotl = dot(n, l);
    // Guard against smoothstep(0, 0, x) which is undefined in GLSL.
    return smoothstep(0.0, max(fallOff, 1e-5), ndotl);
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

    for (int i = 0; i < NB_LIGHTS; i += 3) {
        vec4 lightColor = g_LightData[i];
        vec4 lightData1 = g_LightData[i + 1];

        vec4 lightDir;
        vec3 lightVec;
        lightComputeDir(vPos, lightColor.w, lightData1, lightDir, lightVec);

        float diff = lightFalloff(normal, lightDir.xyz, m_FallOffFactor);
        color += DiffuseSum.rgb * lightColor.rgb * diffuseColor.rgb * diff * lightDir.w;
    }

    gl_FragColor = vec4(color, DiffuseSum.a * diffuseColor.a);
}
