package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import java.util.Objects;

/**
 * Builds a concrete {@link Mission} from a {@link MissionSpec} and an {@link OptimizationType},
 * resolving the stage decomposition that the spec left open.
 *
 * <p>This is the seam that decouples <em>what</em> the mission is (the spec, frozen by the wizard)
 * from <em>how</em> it is flown (the stages, a function of the mode). The optimization toggle can
 * therefore recompose the mission after the wizard closes — swapping analytic stages for their
 * CMA-ES counterparts — instead of committing to one composition at creation time.
 *
 * <p>Two independent axes drive the modes; this composer owns only the first:
 *
 * <ul>
 *   <li><b>Stage composition</b> (here): {@code FAST} yields the closed-form analytic profile;
 *       {@code BALANCED}/{@code PRECISE} yield the CMA-ES optimized transfer where one exists.
 *   <li><b>Load handling</b> (in {@code MissionPlanOptimizer}): fixed vs. minimized propellant
 *       loads, selecting the concrete {@code MissionPlanner}. Orthogonal to the stage choice.
 * </ul>
 */
public final class MissionComposer {

  /**
   * LEO targets whose perigee and apogee differ by less than this (meters) are treated as circular
   * and flown with the single-burn circular transfer rather than the elliptic one.
   */
  private static final double CIRCULAR_TOLERANCE_M = 1_000.0;

  private MissionComposer() {}

  /**
   * Composes the mission for the given spec and optimization mode.
   *
   * @param spec the mission description (targets, vehicle, site)
   * @param mode the optimization mode driving the stage composition
   * @return the built mission, ready to hand to a {@code MissionPlanner}
   */
  public static Mission compose(MissionSpec spec, OptimizationType mode) {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(mode, "mode");
    return switch (spec) {
      case MissionSpec.Leo leo -> composeLeo(leo, mode);
      case MissionSpec.Geo geo -> composeGeo(geo, mode);
    };
  }

  private static Mission composeLeo(MissionSpec.Leo spec, OptimizationType mode) {
    if (mode == OptimizationType.FAST) {
      // Analytic Hohmann transfer — the historical default. Kept byte-for-byte identical to the
      // pre-toggle path (non-regression baseline).
      return new LEOMission(
          spec.name(),
          spec.configuration(),
          spec.perigeeAltitude(),
          spec.apogeeAltitude(),
          spec.latitude(),
          spec.longitude(),
          spec.altitude());
    }
    // CMA-ES optimized transfer. A circular target uses the single-burn circular transfer; an
    // elliptic target keeps its distinct apogee via the two-maneuver transfer.
    boolean circular =
        Math.abs(spec.apogeeAltitude() - spec.perigeeAltitude()) < CIRCULAR_TOLERANCE_M;
    return circular
        ? LEOMission.circularWithOptimizedTransfer(
            spec.name(),
            spec.configuration(),
            spec.perigeeAltitude(),
            spec.latitude(),
            spec.longitude(),
            spec.altitude())
        : LEOMission.ellipticWithOptimizedTransfer(
            spec.name(),
            spec.configuration(),
            spec.perigeeAltitude(),
            spec.apogeeAltitude(),
            spec.latitude(),
            spec.longitude(),
            spec.altitude());
  }

  private static Mission composeGeo(MissionSpec.Geo spec, OptimizationType mode) {
    // GEO currently has a single (analytic) composition: the CMA-ES two-maneuver transfer is a LEO
    // stage, with no GEO equivalent yet. Every mode therefore yields the analytic profile; the mode
    // still differentiates GEO on the load-handling axis in MissionPlanOptimizer.
    return new GEOMission(
        spec.name(),
        spec.configuration(),
        spec.parkingAltitude(),
        spec.targetAltitude(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        spec.finalInclination());
  }
}
