package com.smousseur.orbitlab.engine.scene.hover;

import com.smousseur.orbitlab.engine.HoverConfig;

/**
 * How a planet's orbit looks at a given hover intensity: its core thickens and blends toward white,
 * under a halo that rises from nothing.
 *
 * <p>The intensity is applied as it is, without the ease the icon gets, as in the mockup the hover
 * was designed on. At rest the halo's opacity is exactly zero, which is what keeps the ribbon's
 * geometry at its narrow rest width.
 *
 * @param widthPx the width of the orbit's core, in pixels
 * @param coreWhite how far toward white the core is blended, in {@code [0,1]}
 * @param haloAlpha the halo's opacity on the orbit's axis, in {@code [0,1]}
 */
public record OrbitHoverLook(float widthPx, float coreWhite, float haloAlpha) {

  /**
   * Computes the look of an orbit.
   *
   * @param intensity the hover intensity, in {@code [0,1]}
   * @param restWidthPx the width of the orbit's core at rest, in pixels
   * @param config the hover configuration
   * @return the orbit's width, core blend and halo opacity
   */
  public static OrbitHoverLook of(float intensity, float restWidthPx, HoverConfig config) {
    return new OrbitHoverLook(
        restWidthPx + config.orbitCoreGrowPx() * intensity,
        config.orbitCoreWhiteBlend() * intensity,
        config.haloPeakAlpha() * intensity);
  }
}
