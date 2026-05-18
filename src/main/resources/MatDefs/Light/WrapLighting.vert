#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"

uniform vec4 g_AmbientLightColor;

#ifdef MATERIAL_COLORS
uniform vec4 m_Ambient;
uniform vec4 m_Diffuse;
#endif

attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec2 inTexCoord;

varying vec2 texCoord;
varying vec3 vNormal;
varying vec3 vPos;
varying vec3 AmbientSum;
varying vec4 DiffuseSum;

void main() {
    vec4 modelSpacePos = vec4(inPosition, 1.0);

    gl_Position = TransformWorldViewProjection(modelSpacePos);
    texCoord = inTexCoord;

    vPos = TransformWorldView(modelSpacePos).xyz;
    vNormal = normalize(TransformNormal(inNormal));

#ifdef MATERIAL_COLORS
    AmbientSum = m_Ambient.rgb * g_AmbientLightColor.rgb;
    DiffuseSum = m_Diffuse;
#else
    AmbientSum = g_AmbientLightColor.rgb;
    DiffuseSum = vec4(1.0);
#endif
}
