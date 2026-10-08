package com.smousseur.orbitlab.engine.scene.hover;

import com.jme3.material.Material;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.HoverConfig;
import java.util.Objects;

/**
 * An orbit ribbon's halo: switches the ribbon's material to its halo variant once, then shows on it
 * the hover intensity of the ribbon's planet, as {@link OrbitHoverLook} computes it.
 *
 * <p>The rest width is the ribbon's own, read from its material when the halo is created, so the
 * orbit grows from whatever width it was built with.
 */
public final class OrbitHalo {

  private static final String WIDTH_PX = "WidthPx";

  private final Material ribbon;
  private final HoverConfig config;
  private final float restWidthPx;
  private float intensity;

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
}
