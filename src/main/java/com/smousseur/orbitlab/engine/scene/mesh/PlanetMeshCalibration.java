package com.smousseur.orbitlab.engine.scene.mesh;

/**
 * What a body's asset was measured to carry, plus the values a measurement cannot supply.
 *
 * <p>The components have deliberately different natures, and fusing them into a single quaternion
 * is what the previous design got wrong (see {@code docs/orientation-planetes/01-decoupage.md}
 * §4.2): {@code measured} is produced by {@link MeshFrameProbe} and copied verbatim from its
 * report, never edited by hand, while {@code lambda0Deg} is a human datum about the
 * <em>texture</em> that no file inspection can establish, and {@code visibleLayerDriftDegPerDay} is
 * a property of the body itself that no inspection of the asset could ever reach.
 *
 * <p>Which is also why they age differently. Replacing a mesh invalidates {@code measured} and
 * leaves the other two intact whenever the new model reuses the same texture — the common case.
 *
 * @param measured the frame the asset carries, as reported by {@code ./gradlew meshProbe}
 * @param lambda0Deg the body-fixed longitude, in degrees, that the texture's column {@code u = 0}
 *     represents — at J2000 when the body drifts, at any date when it does not. Its neutral value
 *     is {@code PlanetMeshCorrection.CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG}, not zero
 * @param visibleLayerDriftDegPerDay east-longitude drift, in degrees per day, of the layer the
 *     texture depicts within the frame Orekit rotates the body in. Zero for a map of a solid
 *     surface, which is what the body frame is defined on; non-zero for a cloud deck, which has its
 *     own period — Jupiter's belts and Saturn's are catalogued in rotation systems that are not the
 *     radio one Orekit uses. A single rate per body, so it says nothing about rotation that varies
 *     with latitude
 * @param textureWidth width of the base colour texture the globe was bound to when measured
 * @param textureHeight its height. Together with the width this is the asset's texture identity,
 *     which the startup guard needs: a re-export that keeps the mesh and swaps the texture leaves
 *     {@code measured} identical and {@code lambda0Deg} silently wrong
 */
public record PlanetMeshCalibration(
    MeshFrame measured,
    float lambda0Deg,
    double visibleLayerDriftDegPerDay,
    int textureWidth,
    int textureHeight) {

  /**
   * The longitude the texture's column {@code u = 0} represents at a given date, drift included.
   *
   * @param daysSinceJ2000 elapsed days since J2000, negative before it
   * @return the longitude in degrees, unwrapped
   */
  public double lambda0DegAt(double daysSinceJ2000) {
    return lambda0Deg + visibleLayerDriftDegPerDay * daysSinceJ2000;
  }

  /** Whether the layer this asset depicts turns at a rate of its own. */
  public boolean hasVisibleLayerDrift() {
    return visibleLayerDriftDegPerDay != 0.0;
  }
}
