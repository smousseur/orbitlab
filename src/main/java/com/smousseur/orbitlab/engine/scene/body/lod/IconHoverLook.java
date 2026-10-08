package com.smousseur.orbitlab.engine.scene.body.lod;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.engine.HoverConfig;

/**
 * How an icon looks at a given hover intensity and dimming: it grows, and its ring and its name
 * blend toward white, the name further than the ring so that it reads first; dimmed, the ring and
 * the name fade together, keeping their colour.
 *
 * <p>The intensity is eased out with a cubic before it is applied, {@code e = 1 − (1 − h)³}, as in
 * the mockup the hover was designed on: the icon reacts at once and settles gently. The dimming is
 * applied as it is.
 *
 * @param iconPx the icon's size, in pixels
 * @param ring the colour of the icon's ring
 * @param label the colour of the icon's name
 */
public record IconHoverLook(float iconPx, ColorRGBA ring, ColorRGBA label) {

  /**
   * Computes the look of an icon.
   *
   * @param intensity the hover intensity, in {@code [0,1]}
   * @param dim the opacity factor while another body is lit, 1 for an icon that is not dimmed
   * @param base the icon's own colour, which is left untouched
   * @param config the hover configuration
   * @return the icon's size and colours
   */
  public static IconHoverLook of(float intensity, float dim, ColorRGBA base, HoverConfig config) {
    float e = easeOut(intensity);
    return new IconHoverLook(
        config.iconRestPx() + (config.iconHoverPx() - config.iconRestPx()) * e,
        towardWhite(base, config.ringWhiteBlend() * e, dim),
        towardWhite(base, config.labelWhiteBlend() * e, dim));
  }

  static float easeOut(float h) {
    float rest = 1f - h;
    return 1f - rest * rest * rest;
  }

  private static ColorRGBA towardWhite(ColorRGBA base, float t, float dim) {
    return new ColorRGBA(
        base.r + (1f - base.r) * t,
        base.g + (1f - base.g) * t,
        base.b + (1f - base.b) * t,
        base.a * dim);
  }
}
