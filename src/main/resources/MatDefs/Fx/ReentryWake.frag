#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform float m_Intensity;
uniform vec4  m_RimColor;

varying vec3  vPosView;
varying vec3  vRoot;
varying vec3  vAxis;
varying float vRadius;
varying float vS;

// Set on screen, not measured, first on a band facing the eye, then on the tube, then on the far
// relay alone, which is all the wake now is: a line a few pixels wide past the icon switch.
// - ACROSS_EDGE: where the line starts fading, from its axis (0) to its edge (1). A profile peaked
//   on the axis, as up close, left one lit row of pixels in a line three pixels wide.
// - FADE_POWER: how the light dies from the root to the tail, so a bright head trails a long faint
//   train.
// - EMISSION_GAIN: how much light per unit of intensity: at its root the line is wholly the rim's
//   colour from a third of the full intensity up.
// The wake does not whiten: it streams back from the sheath's rim in the rim's colour, and a
// whitened root outshone the sheath's apex, the hottest point of the envelope. Nor does it add its
// light to what lies behind: over a bright sky and over the trace it runs along, every channel
// saturated and the orange read as white. It hides what it crosses as much as it shines.
const float ACROSS_EDGE = 0.5;
const float FADE_POWER = 4.0;
const float EMISSION_GAIN = 3.0;

void main() {
    // How much of the wake the line of sight crosses, read as how close it passes to the wake's
    // axis: 0 through the axis, 1 grazing the edge. Only the far wall is drawn, so each pixel is lit
    // once. Seen from the side this is the band's distance to the axis; seen from behind it makes a
    // disc bright in its middle, where a shading by the wall's own normal would go dark, every wall
    // being seen edge-on.
    vec3 ray = normalize(vPosView);
    vec3 axis = normalize(vAxis);
    vec3 normalToBoth = cross(ray, axis);
    float sinAngle = length(normalToBoth);
    float distance = sinAngle > 1e-4
        ? abs(dot(vRoot, normalToBoth)) / sinAngle
        : length(vRoot - dot(vRoot, ray) * ray);
    float u = clamp(distance / max(vRadius, 1e-6), 0.0, 1.0);

    float across = 1.0 - smoothstep(ACROSS_EDGE, 1.0, u);
    float fade = pow(1.0 - vS, FADE_POWER);
    float level = clamp(m_Intensity * across * fade * EMISSION_GAIN, 0.0, 1.0);

    // Premultiplied, as the sheath: the colour is the light added, the alpha the share of the
    // background hidden. Both follow the one level, so the hue stays the rim's at any brightness.
    gl_FragColor = vec4(m_RimColor.rgb * level, level);
}
