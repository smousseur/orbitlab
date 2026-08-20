#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4 m_Color;
uniform sampler2D m_MaskMap;

varying vec2 vTexCoord;

void main() {
    // The texture's RGB is discarded on purpose. Unshaded — and therefore Lemur's
    // QuadBackgroundComponent — multiplies its Color by the texel, which tints a white icon and
    // leaves a black one black whatever colour is asked for. icon-spinner.png is pure black with
    // its twelve spokes carried by the alpha channel alone, so it can only be drawn by reading that
    // channel as coverage and taking the colour from the uniform.
    gl_FragColor = vec4(m_Color.rgb, m_Color.a * texture2D(m_MaskMap, vTexCoord).a);
}
