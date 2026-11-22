#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform float m_Intensity;
uniform vec4 m_RimColor;
uniform vec4 m_HotCoreColor;
uniform float m_Coverage;

varying vec3 vNormal;
varying vec3 vToEye;
varying float vRadial;

// Set on screen, not measured, over three looks at a Falcon Heavy booster's re-entry:
// - RIM_POWER: how tightly the edge glow hugs the silhouette.
// - CORE_POWER: how far the stagnation glow spreads back from the apex. Linear: squared, it
//   collapsed to a dim tip and the face of the shell read as matte brown.
// - WHITEN_POWER: how far the whitening spreads. Kept apart from the glow and cubed: tied to the
//   linear glow it turned the whole shell peach, and the orange edge was lost.
// - EMISSION_GAIN: how much light the shell gives per unit of intensity. At one, it peaked at a
//   mid-tone and read as a beige surface rather than as light, on dark and bright backgrounds alike.
// - RIM_FADE_START: where the shell starts fading toward its open rim, so it never ends on a cut.
const float RIM_POWER = 2.0;
const float CORE_POWER = 1.0;
const float WHITEN_POWER = 3.0;
const float EMISSION_GAIN = 2.0;
const float RIM_FADE_START = 0.7;

void main() {
    vec3 normal = normalize(vNormal);
    vec3 toEye = normalize(vToEye);

    // Grazing view on the shell: bright where it is seen edge-on, which outlines it whatever the
    // angle it is looked at from.
    float edge = pow(1.0 - abs(dot(normal, toEye)), RIM_POWER);
    // Stagnation point: brightest at the apex, where the flow is stopped.
    float core = pow(1.0 - vRadial, CORE_POWER);
    float whiten = pow(1.0 - vRadial, WHITEN_POWER) * m_Intensity;
    float rimFade = 1.0 - smoothstep(RIM_FADE_START, 1.0, vRadial);

    vec3 color = mix(m_RimColor.rgb, m_HotCoreColor.rgb, whiten);
    float emission = m_Intensity * max(edge, core) * rimFade;
    // Only the edge veils what lies behind, where the line of sight crosses the most plasma: seen
    // face-on the shell is pure added light, which keeps the body under it from darkening, and the
    // veiled sky keeps the edge's own colour instead of letting it wash out over blue.
    float veil = clamp(m_Coverage * m_Intensity * edge * rimFade, 0.0, 1.0);

    // Premultiplied: the colour is the light added, the alpha the share of the background hidden.
    // Each channel saturates on its own, so a bright orange tips toward yellow before white.
    gl_FragColor = vec4(min(color * emission * EMISSION_GAIN, vec3(1.0)), veil);
}
