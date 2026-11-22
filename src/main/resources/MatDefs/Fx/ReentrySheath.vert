#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"

attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec2 inTexCoord;

varying vec3 vNormal;
varying vec3 vToEye;
varying float vRadial;

void main() {
    vec4 viewPosition = TransformWorldView(vec4(inPosition, 1.0));
    vNormal = TransformNormal(inNormal);
    vToEye = -viewPosition.xyz;
    vRadial = inTexCoord.x;
    gl_Position = TransformWorldViewProjection(vec4(inPosition, 1.0));
}
