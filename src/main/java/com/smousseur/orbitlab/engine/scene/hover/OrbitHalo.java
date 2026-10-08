package com.smousseur.orbitlab.engine.scene.hover;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.HoverConfig;
import java.util.Objects;

/**
 * An orbit ribbon's halo: switches the ribbon's material to its halo variant once, then shows on it
 * the hover intensity of the ribbon's planet, as {@link OrbitHoverLook} computes it, and how far
 * the orbit fades back while another body is lit, as {@link HoverDim} computes it.
 *
 * <p>The rest width and the colour are the ribbon's own, read from its material when the halo is
 * created, so the orbit grows from whatever width it was built with and fades from its own colour.
 * The two looks are independent: the intensity drives the halo's uniforms, the dimming the alpha of
 * the colour, which the halo variant multiplies into the core and the halo alike.
 */
public final class OrbitHalo {

  private static final String WIDTH_PX = "WidthPx";
  private static final String COLOR = "Color";

  private final Material ribbon;
  private final HoverConfig config;
  private final float restWidthPx;
  private final ColorRGBA baseColor;
  private float intensity;
  private float dim = 1f;

  /**
   * Switches a ribbon to its halo variant, at rest.
   *
   * @param ribbon the ribbon's material, made by {@link AssetFactory#createRibbon}
   * @param config the hover configuration, for the halo's width and the lit look
   */
  public OrbitHalo(Material ribbon, HoverConfig config) {
    this.ribbon = Objects.requireNonNull(ribbon, "ribbon");
    this.config = Objects.requireNonNull(config, "config");
    this.restWidthPx = ribbon.<Float>getParamValue(WIDTH_PX);
    this.baseColor = ribbon.<ColorRGBA>getParamValue(COLOR).clone();
    AssetFactory.get().enableRibbonHalo(ribbon, config.haloPx());
  }

  /**
   * Shows how strongly the ribbon's planet is hovered. The material is only touched when the
   * intensity changes, so a resting orbit costs nothing.
   *
   * @param intensity the hover intensity, 0 at rest and 1 fully lit
   */
  public void setIntensity(float intensity) {
    if (intensity == this.intensity) {
      return;
    }
    this.intensity = intensity;
    OrbitHoverLook look = OrbitHoverLook.of(intensity, restWidthPx, config);
    ribbon.setFloat(WIDTH_PX, look.widthPx());
    ribbon.setFloat("CoreWhite", look.coreWhite());
    ribbon.setFloat("HaloAlpha", look.haloAlpha());
  }

  /**
   * Shows how far the orbit fades back while another body is lit. The colour is set anew rather
   * than written into, since the one the ribbon was built with may be shared; and only when the
   * dimming changes.
   *
   * @param dim the opacity factor, 1 for an orbit that is not dimmed
   */
  public void setDim(float dim) {
    if (dim == this.dim) {
      return;
    }
    this.dim = dim;
    ribbon.setColor(COLOR, new ColorRGBA(baseColor.r, baseColor.g, baseColor.b, baseColor.a * dim));
  }
}
