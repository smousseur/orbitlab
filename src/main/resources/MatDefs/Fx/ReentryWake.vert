#import "Common/ShaderLib/GLSLCompat.glsllib"

// The re-entry wake: a unit tube (ReentryWakeMesh) stretched along its pivot's +Y, each ring
// scaled to the wake's radius there, in view space before projection. The sizing is Ribbon.vert's
// pixel arithmetic, applied around the axis instead of across a band facing the eye, so the wake is
// a solid of revolution from every side yet never thinner than a floor in pixels far away.
//
// Precondition: the pivot's world transform carries no scale (its anchor is only translated, and
// the wake view sets a unit scale), so a view-space length is a length in world units, which is
// what LengthUnits, RefLengthUnits and WidthUnits are given in.

attribute vec3 inPosition;   // (cos phi, s, sin phi): the radial direction, s from root 0 to tail 1

uniform mat4  g_WorldViewMatrix;
uniform mat4  g_ProjectionMatrix;
uniform vec2  g_Resolution;
uniform float m_LengthUnits;
uniform float m_RefLengthUnits;
uniform float m_WidthUnits;
uniform float m_MinLengthPx;
uniform float m_MinWidthPx;

varying vec3  vPosView;
varying vec3  vRoot;
varying vec3  vAxis;
varying float vRadius;
varying float vS;

// Set on screen, not measured: how the tube narrows from its root to its tail. Cubed: a linear
// taper kept it over 70 % of its root width for the first third of a 1.4 km wake.
const float TAPER_POWER = 3.0;

// The view-space length one screen pixel spans at a depth, on the near-plane floor Ribbon.vert
// uses: a vertex closer than the near plane is never drawn, and behind the eye the depth would go
// negative.
float pixelAt(float depth, float near) {
    return 2.0 * max(depth, near) / (g_Resolution.y * g_ProjectionMatrix[1][1]);
}

void main() {
    float near = g_ProjectionMatrix[3][2] / (g_ProjectionMatrix[2][2] - 1.0);
    mat3 basis = mat3_sub(g_WorldViewMatrix);
    vec3 root = (g_WorldViewMatrix * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 axis = normalize(basis * vec3(0.0, 1.0, 0.0));
    vec3 radial = normalize(basis * vec3(inPosition.x, 0.0, inPosition.z));

    // The physical length, magnified as a whole once a wake of RefLengthUnits would fall under
    // MinLengthPx: far away every wake keeps its proportion to the others. Taken once, at the
    // root's depth, so the tube stays straight.
    float magnify = max(1.0, m_MinLengthPx * pixelAt(-root.z, near) / m_RefLengthUnits);
    float s = inPosition.y;
    vec3 centre = root + axis * (s * m_LengthUnits * magnify);

    // Never narrower than MinWidthPx at the ring's own depth, all along: in metres it falls under a
    // pixel as soon as the piece turns into its icon. Floored before the taper, it held at the root
    // only and the line fell under a pixel a fifth of the way back; the tail fades by its light.
    float pixel = pixelAt(-centre.z, near);
    float physical = 0.5 * m_WidthUnits * pow(1.0 - s, TAPER_POWER);
    float radius = max(physical, 0.5 * m_MinWidthPx * pixel);
    vec3 posView = centre + radial * radius;
    gl_Position = g_ProjectionMatrix * vec4(posView, 1.0);

    vPosView = posView;
    vRoot = root;
    vAxis = axis;
    vRadius = radius;
    vS = s;
}
