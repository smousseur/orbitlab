#import "Common/ShaderLib/GLSLCompat.glsllib"

attribute vec3 inPosition;
attribute vec2 inTexCoord;

varying vec2 vTexCoord;

uniform mat4 g_WorldViewProjectionMatrix;

void main() {
    vTexCoord = inTexCoord;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
