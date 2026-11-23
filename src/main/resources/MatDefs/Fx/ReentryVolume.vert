#import "Common/ShaderLib/GLSLCompat.glsllib"

// The plasma volume's box. Only its far faces are drawn; each fragment marches the ray from the
// camera to it through the box, in the box's own axes, where the body is one radius wide.

attribute vec3 inPosition;

uniform mat4 g_WorldViewProjectionMatrix;

varying vec3 vObjectPosition;

void main() {
    vObjectPosition = inPosition;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
