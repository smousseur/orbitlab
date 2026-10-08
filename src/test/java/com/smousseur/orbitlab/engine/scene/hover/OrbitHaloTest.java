package com.smousseur.orbitlab.engine.scene.hover;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.HoverConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** An orbit ribbon's halo, switched on once and then fed its planet's hover intensity. */
class OrbitHaloTest {

  private static final float TOLERANCE = 1e-6f;
  private static final HoverConfig CONFIG = HoverConfig.defaults();

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @Test
  void switchesTheRibbonToItsHaloVariant() {
    Material ribbon = orbitRibbon(2.5f);

    new OrbitHalo(ribbon, CONFIG);

    assertEquals(Float.valueOf(18f), ribbon.getParamValue("HaloPx"));
    assertEquals(BlendMode.PremultAlpha, ribbon.getAdditionalRenderState().getBlendMode());
  }

  @Test
  void fullyLitTheOrbitShowsItsHalo() {
    Material ribbon = orbitRibbon(2.5f);
    OrbitHalo halo = new OrbitHalo(ribbon, CONFIG);

    halo.setIntensity(1f);

    assertEquals(3.5f, floatOf(ribbon, "WidthPx"), TOLERANCE);
    assertEquals(0.35f, floatOf(ribbon, "CoreWhite"), TOLERANCE);
    assertEquals(0.36f, floatOf(ribbon, "HaloAlpha"), TOLERANCE);
  }

  /** The rest width is the ribbon's own, read from its material rather than copied here. */
  @Test
  void growsFromTheRibbonsOwnWidth() {
    Material ribbon = orbitRibbon(4f);
    OrbitHalo halo = new OrbitHalo(ribbon, CONFIG);

    halo.setIntensity(1f);

    assertEquals(5f, floatOf(ribbon, "WidthPx"), TOLERANCE);
  }

  /**
   * Back at rest the halo's opacity must be exactly zero, not merely small: it is what narrows the
   * ribbon's geometry back to its core.
   */
  @Test
  void backAtRestTheOrbitRegainsItsRestLook() {
    Material ribbon = orbitRibbon(2.5f);
    OrbitHalo halo = new OrbitHalo(ribbon, CONFIG);

    halo.setIntensity(1f);
    halo.setIntensity(0f);

    assertEquals(2.5f, floatOf(ribbon, "WidthPx"), 0f);
    assertEquals(0f, floatOf(ribbon, "CoreWhite"), 0f);
    assertEquals(0f, floatOf(ribbon, "HaloAlpha"), 0f);
  }

  @Test
  void dimmingFadesTheOrbitButKeepsItsColour() {
    Material ribbon = orbitRibbon(new ColorRGBA(0.2f, 0.3f, 0.4f, 1f));
    OrbitHalo halo = new OrbitHalo(ribbon, CONFIG);

    halo.setDim(0.35f);

    assertColor(new ColorRGBA(0.2f, 0.3f, 0.4f, 0.35f), colorOf(ribbon));
  }

  /** The base is the ribbon's own colour, alpha included, read from its material. */
  @Test
  void dimsFromTheRibbonsOwnAlpha() {
    Material ribbon = orbitRibbon(new ColorRGBA(0.2f, 0.3f, 0.4f, 0.8f));
    OrbitHalo halo = new OrbitHalo(ribbon, CONFIG);

    halo.setDim(0.5f);

    assertColor(new ColorRGBA(0.2f, 0.3f, 0.4f, 0.4f), colorOf(ribbon));
  }

  @Test
  void undimmedTheOrbitRegainsItsColour() {
    Material ribbon = orbitRibbon(new ColorRGBA(0.2f, 0.3f, 0.4f, 1f));
    OrbitHalo halo = new OrbitHalo(ribbon, CONFIG);

    halo.setDim(0.35f);
    halo.setDim(1f);

    assertColor(new ColorRGBA(0.2f, 0.3f, 0.4f, 1f), colorOf(ribbon));
  }

  /** The colour a ribbon is built with may be shared; dimming must never write into it. */
  @Test
  void leavesTheColourTheRibbonWasBuiltWithUntouched() {
    ColorRGBA built = new ColorRGBA(0.2f, 0.3f, 0.4f, 1f);
    OrbitHalo halo = new OrbitHalo(orbitRibbon(built), CONFIG);

    halo.setDim(0.35f);

    assertColor(new ColorRGBA(0.2f, 0.3f, 0.4f, 1f), built);
  }

  @Test
  void dimmingLeavesTheHaloToTheIntensity() {
    Material ribbon = orbitRibbon(2.5f);
    OrbitHalo halo = new OrbitHalo(ribbon, CONFIG);

    halo.setIntensity(1f);
    halo.setDim(0.35f);

    assertEquals(3.5f, floatOf(ribbon, "WidthPx"), TOLERANCE);
    assertEquals(0.35f, floatOf(ribbon, "CoreWhite"), TOLERANCE);
    assertEquals(0.36f, floatOf(ribbon, "HaloAlpha"), TOLERANCE);
  }

  private static Material orbitRibbon(float widthPx) {
    return AssetFactory.get().createRibbon(ColorRGBA.Blue, widthPx, false);
  }

  private static Material orbitRibbon(ColorRGBA color) {
    return AssetFactory.get().createRibbon(color, 2.5f, false);
  }

  private static float floatOf(Material material, String param) {
    return material.<Float>getParamValue(param);
  }

  private static ColorRGBA colorOf(Material material) {
    return material.getParamValue("Color");
  }

  private static void assertColor(ColorRGBA expected, ColorRGBA actual) {
    assertEquals(expected.r, actual.r, TOLERANCE, "red");
    assertEquals(expected.g, actual.g, TOLERANCE, "green");
    assertEquals(expected.b, actual.b, TOLERANCE, "blue");
    assertEquals(expected.a, actual.a, TOLERANCE, "alpha");
  }
}
