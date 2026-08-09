#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4 m_Color;
uniform float m_CoreRatio;
uniform float m_Falloff;

varying vec2 texCoord;

void main() {
    // Radial coordinate on the camera-facing quad: 0 at the star's centre, 1 on the circle
    // inscribed in the quad. The corners reach sqrt(2) and are clamped away below, which is what
    // turns a square mesh into a round glow without a texture to sample or a resolution to lose.
    float r = length(texCoord * 2.0 - 1.0);

    // Remap so the decay starts at the limb (r = CoreRatio) and is spent at the quad's edge.
    // Everything inside the limb sits at t = 0, full strength: the depth test discards it against
    // the star's own geometry, so those fragments never reach the frame and cost nothing to leave
    // at full value.
    float t = clamp((r - m_CoreRatio) / (1.0 - m_CoreRatio), 0.0, 1.0);

    gl_FragColor = vec4(m_Color.rgb, m_Color.a * pow(1.0 - t, m_Falloff));
}
