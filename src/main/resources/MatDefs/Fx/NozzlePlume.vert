#import "Common/ShaderLib/GLSLCompat.glsllib"

// The hull a nozzle cluster's plume is marched in: a truncated cone around the widest shape the
// jet takes. Each fragment marches the ray from the camera through the jet, in the cluster's own
// frame, where the exit plane is at the origin and the jet runs along -Y.

attribute vec3 inPosition;

uniform mat4 g_WorldViewProjectionMatrix;

varying vec3 vObjectPosition;

void main() {
    vObjectPosition = inPosition;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
