#import "Common/ShaderLib/GLSLCompat.glsllib"

// Camera-facing ribbon, expanded on the GPU (spec docs/graphics-effects/ribbon-lines.md §4.2).
//
// The mesh holds each polyline point twice, with inTexCoord.x = +1 / -1, and this shader pushes the
// two copies apart by half the requested pixel width. Doing it here rather than in Java is what
// keeps a planetary orbit's buffer static: the geometry depends only on the data, and only the
// uniforms depend on the camera.
//
// inNormal carries the *tangent*, not a normal. It is the only three-component attribute available
// without moving to TexCoord2..8, and the only shader that reads it is this one.

attribute vec3 inPosition;
attribute vec3 inNormal;
attribute vec2 inTexCoord;   // (side = ±1, normalised arc length)

#ifdef HAS_VERTEXCOLOR
attribute vec4 inColor;
varying vec4 vColor;
#endif

uniform mat4  g_WorldViewMatrix;
uniform mat4  g_ProjectionMatrix;
uniform vec2  g_Resolution;
uniform float m_WidthPx;

varying float vSide;
varying float vArc;

void main() {
    vec4 posView = g_WorldViewMatrix * vec4(inPosition, 1.0);
    vec3 tanView = normalize(mat3_sub(g_WorldViewMatrix) * inNormal);
    vec3 eyeDir  = normalize(-posView.xyz);

    // Perpendicular to the trace *and* to the line of sight: the ribbon turns to face the camera.
    vec3  side = cross(tanView, eyeDir);
    float len  = length(side);
    // Looking straight along the trace, the cross product vanishes and normalising it would give a
    // NaN — a ribbon that disappears, or a triangle sent to infinity. At that angle the ribbon is
    // seen edge-on, so the orientation picked here has no visible consequence; the guard does.
    side = (len > 1e-6) ? side / len : vec3(1.0, 0.0, 0.0);

    // Depth is -z in view space, not the radial distance. Using the distance would widen the ribbon
    // towards the edges of the screen, where the same depth sits further from the eye.
    //
    // Behind the camera -z goes negative and the half-width with it, which would flip the two edges
    // and twist the quad that straddles the near plane. That case is not hypothetical: in spacecraft
    // view the camera sits a few hundred metres from the vehicle and the trace runs through it. The
    // near plane, recovered from the projection matrix, is the natural floor — a vertex closer than
    // that is never drawn anyway, and the pipeline's clip at the near plane interpolates the rest.
    float near  = g_ProjectionMatrix[3][2] / (g_ProjectionMatrix[2][2] - 1.0);
    float depth = max(-posView.z, near);

    // +1 px of geometry beyond the nominal half-width: that margin is where the fragment shader
    // puts the fade, so the fade never eats into the ribbon itself.
    float halfPx    = 0.5 * m_WidthPx + 1.0;
    float halfWorld = halfPx * depth * 2.0
                    / (g_Resolution.y * g_ProjectionMatrix[1][1]);

    // Offset applied in view space, before projection — never in NDC after the divide by w, which
    // is the version found most often and the one that explodes when w <= 0.
    posView.xyz += side * (inTexCoord.x * halfWorld);
    gl_Position  = g_ProjectionMatrix * posView;

    vSide = inTexCoord.x;
    vArc  = inTexCoord.y;
#ifdef HAS_VERTEXCOLOR
    vColor = inColor;
#endif
}
