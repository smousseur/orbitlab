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
   * and flown with the two-burn circular transfer rather than the single-burn elliptic one.
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
    Mission mission =
        switch (spec) {
          case MissionSpec.EarthOrbit earthOrbit -> composeEarthOrbit(earthOrbit, mode);
          case MissionSpec.Geo geo -> composeGeo(geo, mode);
        };
    // This composer is the ONLY writer of a mission's restitution horizon (spec
    // docs/mission-horizon/01-horizon-explicite.md). Carrying it on the spec and applying it here,
    // rather than threading it through the constructor chains, is what makes it survive a mode
    // toggle or a wizard edit: both replace the Mission, neither replaces the spec.
    mission.setHorizon(spec.horizon());
    return mission;
  }

  private static Mission composeEarthOrbit(MissionSpec.EarthOrbit spec, OptimizationType mode) {
    LaunchPlane plane = spec.launchPlane();
    if (mode == OptimizationType.FAST) {
      // Analytic Hohmann transfer — the historical default. Kept byte-for-byte identical to the
      // pre-toggle path (non-regression baseline).
      return new EarthOrbitMission(
          spec.name(),
          spec.configuration(),
          spec.perigeeAltitude(),
          spec.apogeeAltitude(),
          plane,
          spec.latitude(),
          spec.longitude(),
          spec.altitude());
    }
    // CMA-ES optimized transfer, and the two shapes take DIFFERENT stages — read the direction
    // carefully, this comment used to state it backwards:
    //
    //  - circular  -> TransfertTwoManeuverStage, TWO burns (burn 1 optimized on 4 variables, the
    //    circularization at the next apoapsis resolved deterministically). It only supports
    //    circular targets, which is exactly why the tolerance above exists;
    //  - elliptic  -> TransfertManeuverStage, a SINGLE burn shaping the whole ellipse, graded on
    //    apogee, perigee and eccentricity in one aggregate cost.
    //
    // See OptimizationType for what each path measurably buys — and for the open question on the
    // elliptic one, which misses the apogee by 16 km where the analytic profile hits it to 562 m.
    boolean circular =
        Math.abs(spec.apogeeAltitude() - spec.perigeeAltitude()) < CIRCULAR_TOLERANCE_M;
    return circular
        ? EarthOrbitMission.circularWithOptimizedTransfer(
            spec.name(),
            spec.configuration(),
            spec.perigeeAltitude(),
            plane,
            spec.latitude(),
            spec.longitude(),
            spec.altitude())
        : EarthOrbitMission.ellipticWithOptimizedTransfer(
            spec.name(),
            spec.configuration(),
            spec.perigeeAltitude(),
            spec.apogeeAltitude(),
            plane,
            spec.latitude(),
            spec.longitude(),
            spec.altitude());
  }

  private static Mission composeGeo(MissionSpec.Geo spec, OptimizationType mode) {
    // GEO currently has a single (analytic) composition: both CMA-ES transfer stages are LEO-only,
    // with no GEO equivalent yet. Every mode therefore yields the analytic profile; the mode
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
