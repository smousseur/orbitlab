package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.util.Map;

/**
 * Geometric facts of a launcher's stack meshes that the renderer needs to seat a shrinking
 * silhouette (PHY-5 / L6, spec {@code docs/multi-objets/08-conception-L6.md}). It sits beside
 * {@link LauncherAssets} for the same reason: it is a property of the drawn mesh, not of the
 * catalog launcher, and nothing in the propagation depends on it.
 *
 * <p>The only number needed is the height of the {@code after_s1} remnant (upper stage + fairing)
 * as a fraction of the full stack. It fixes where that remnant's base sits once the core is gone —
 * {@code (1 − fraction)} of the height up — and equally where the upper stage is drawn when it
 * later leaves as a debris, since it departs from that same base.
 */
public final class LauncherStackGeometry {

  /**
   * The {@code after_s1} mesh height as a fraction of the full stack, measured off the glTF (spec
   * §1): every piece is authored base-at-origin in a shared frame where the full stack is one unit
   * tall, so the remnant's own height <em>is</em> its fraction. Re-measure on a mesh re-export.
   */
  private static final Map<String, Double> AFTER_S1_FRACTION =
      Map.of(
          Launchers.FALCON_HEAVY.id(), 0.383,
          Launchers.ARIANE_64.id(), 0.514);

  private LauncherStackGeometry() {}

  /**
   * The {@code after_s1} remnant's height as a fraction of the full stack.
   *
   * @param launcherId the catalog key, or {@code null} for a mission carrying no launcher
   * @return the fraction, or {@code 1.0} for an unknown or absent launcher — which lifts nothing,
   *     the safe fallback (a launcher with no measured geometry is drawn as it was before L6, and
   *     the legacy path never sheds a stage anyway)
   */
  public static double afterS1Fraction(String launcherId) {
    if (launcherId == null) {
      return 1.0;
    }
    return AFTER_S1_FRACTION.getOrDefault(launcherId, 1.0);
  }
}
