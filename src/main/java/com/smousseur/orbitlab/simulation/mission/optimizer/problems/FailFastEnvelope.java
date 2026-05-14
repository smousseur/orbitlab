package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

/**
 * Thresholds used to reject a candidate burn-1 outcome before triggering the expensive Step 3
 * propagation in {@link com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver}.
 *
 * <p>The envelope is consumed by the maneuver to short-circuit hopeless candidates (highly
 * elliptical or out-of-range transfer orbits) and emit a partial {@code TransferResult} that the
 * cost function can grade by orbital-element distance instead of running a long, doomed
 * propagation.
 *
 * @param eccentricityMax reject {@code orbitPostBurn1} whose eccentricity exceeds this value
 * @param semiMajorAxisOffsetMax reject {@code orbitPostBurn1} whose semi-major axis exceeds {@code
 *     EARTH_RADIUS + targetAltitude + this} (meters)
 */
public record FailFastEnvelope(double eccentricityMax, double semiMajorAxisOffsetMax) {

  public static FailFastEnvelope defaults() {
    return new FailFastEnvelope(0.95, 2_000_000.0);
  }

  /**
   * Tighter envelope for GTO/GEO transfers: the Hohmann transfer ellipse for a parking-to-GEO
   * Hohmann has e ≈ 0.72, so we reject above 0.85 to catch wildly off-track candidates while still
   * tolerating CMA-ES wandering near the optimum.
   */
  public static FailFastEnvelope forGtoTransfer() {
    return new FailFastEnvelope(0.85, 5_000_000.0);
  }
}
