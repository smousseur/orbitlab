#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4  m_Color;
uniform float m_WidthPx;
#ifdef HAS_HALO
uniform float m_HaloPx;
uniform float m_HaloAlpha;
uniform float m_CoreWhite;
#endif

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
#ifdef HAS_HALO
    // The half-width Ribbon.vert gave the band; the two must agree for distPx to be in pixels.
    if (m_HaloAlpha > 0.0) {
        halfPx = 0.5 * max(m_WidthPx, m_HaloPx) + 1.0;
    }
#endif
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

#ifdef HAS_HALO
    // Premultiplied, for a PremultAlpha blend: the halo adds light to what lies under it, and the
    // core is laid over both — the mockup's additive strokes under an opaque core, in one pass.
    // The mockup's three strokes add up to steps of 0.36, 0.16 and 0.06 out to 3, 5.5 and 9 px; the
    // quadratic falloff meets them where they change, 0.16 at 3 px and 0.054 at 5.5 px for an
    // 18 px halo, without their edges. With no halo and no white, this blended as PremultAlpha is
    // the plain output below blended as Alpha, term for term.
    vec3  core = mix(color.rgb, vec3(1.0), m_CoreWhite);
    float fall = max(1.0 - distPx / (0.5 * m_HaloPx), 0.0);
    float halo = m_HaloAlpha * fall * fall;
    gl_FragColor = vec4((core * cover + color.rgb * (halo * (1.0 - cover))) * color.a,
                        cover * color.a);
#else
    gl_FragColor = vec4(color.rgb, color.a * cover);
#endif
}
