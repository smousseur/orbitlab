#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4  m_Color;
uniform float m_WidthPx;

varying float vSide;
// Normalised arc length. Unused today; it is what the animated dashes and the trail fade read, and
// carrying it costs one interpolator (spec §11.4, §11.6).
varying float vArc;

#ifdef HAS_VERTEXCOLOR
varying vec4 vColor;
#endif

void main() {
    // vSide runs from -1 to +1 across a band whose screen width is 2 * halfPx by construction, so
    // this is a distance to the ribbon's axis measured in pixels — the same number whatever the
    // zoom, whatever the viewport scale.
    float halfPx = 0.5 * m_WidthPx + 1.0;
    float distPx = abs(vSide) * halfPx;

    // Exactly one pixel of fade, spent in the margin the vertex shader added. Full coverage up to
    // WidthPx/2 - 0.5, zero at WidthPx/2 + 0.5. Independent of the width and of the MSAA level,
    // which four samples on a one-pixel line could never deliver; and it degrades gracefully below
    // one pixel of width, the coverage dropping instead of the ribbon vanishing.
    float cover = clamp(0.5 * m_WidthPx + 0.5 - distPx, 0.0, 1.0);

    vec4 color = m_Color;
#ifdef HAS_VERTEXCOLOR
    color *= vColor;
#endif

    gl_FragColor = vec4(color.rgb, color.a * cover);
}
