package com.smousseur.orbitlab.ui.mission;

import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import java.util.Objects;
import java.util.Optional;
import org.hipparchus.util.FastMath;

/**
 * The orbit a mission was asked to reach, as the detail view displays it.
 *
 * <p><b>Read from the spec, never from the objective.</b> {@code GEOMission} records its {@code
 * OrbitInsertionObjective} as {@code (parkingAltitude, targetAltitude, i = launch latitude)} — that
 * is the GTO, an intermediate orbit, not the mission's target. Displaying it beside a circularized
 * GEO orbit would report a ~35 000 km perigee miss and an inclination miss equal to the whole site
 * latitude, on a mission that succeeded. {@code MissionPlanOptimizer} already works around the same
 * trap for its feasibility test.
 *
 * <p>The resolution is a {@code switch} over the sealed {@link MissionSpec}: a third mission type
 * breaks the compilation here rather than silently displaying the wrong target.
 *
 * @param perigeeAltitude the requested perigee altitude (m)
 * @param apogeeAltitude the requested apogee altitude (m)
 * @param inclination the requested inclination (rad)
 */
public record MissionTargetOrbit(
    double perigeeAltitude, double apogeeAltitude, double inclination) {

  /**
   * Resolves the displayable target of a spec.
   *
   * @param spec the mission spec
   * @return the target orbit, never {@code null}
   */
  public static MissionTargetOrbit of(MissionSpec spec) {
    Objects.requireNonNull(spec, "spec");
    return switch (spec) {
      case MissionSpec.Leo leo ->
          new MissionTargetOrbit(
              leo.perigeeAltitude(), leo.apogeeAltitude(), FastMath.toRadians(leo.latitude()));
      case MissionSpec.Geo geo ->
          new MissionTargetOrbit(
              geo.targetAltitude(),
              geo.targetAltitude(),
              FastMath.toRadians(geo.finalInclination()));
    };
  }

  /**
   * Resolves the displayable target of an entry.
   *
   * @param entry the mission entry
   * @return the target orbit, or empty for a legacy entry that carries no spec — there is nothing
   *     to compare an achieved orbit against, and the view shows the achieved orbit alone
   */
  public static Optional<MissionTargetOrbit> forEntry(MissionEntry entry) {
    Objects.requireNonNull(entry, "entry");
    return entry.spec().map(MissionTargetOrbit::of);
  }
}
