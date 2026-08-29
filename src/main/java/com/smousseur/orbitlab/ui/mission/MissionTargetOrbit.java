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
   * <p><b>A flyby has none, and fabricating one would be worse than showing nothing</b> (MIS-4 / L4
   * §6.1). There is no (perigee, apogee, inclination) triple to display beside a lunar approach; a
   * degenerate one would put a false geocentric target next to the achieved orbit, which is exactly
   * the kind of silence this chantier removes. Both consumers already handle the absence — they
   * meet it on legacy entries carrying no spec — so this costs no new case in the UI.
   *
   * @param spec the mission spec
   * @return the target orbit, or empty when the mission aims at no orbit
   */
  public static Optional<MissionTargetOrbit> of(MissionSpec spec) {
    Objects.requireNonNull(spec, "spec");
    return switch (spec) {
      case MissionSpec.EarthOrbit earthOrbit ->
          // The inclination now comes from the spec's target plane, not from the site latitude.
          // They coincide on a due-east launch — which is what every mission flew before MIS-7 —
          // and diverge the moment a plane is asked for, which is the point.
          Optional.of(
              new MissionTargetOrbit(
                  earthOrbit.perigeeAltitude(),
                  earthOrbit.apogeeAltitude(),
                  earthOrbit.targetInclination()));
      case MissionSpec.Geo geo ->
          Optional.of(
              new MissionTargetOrbit(
                  geo.targetAltitude(),
                  geo.targetAltitude(),
                  FastMath.toRadians(geo.finalInclination())));
      case MissionSpec.Lunar lunar -> Optional.empty();
      // A lunar orbit HAS a target, unlike a flyby, and since MIS-5 / L2 the achieved orbit is
      // reported against the arc body — so (100 km, 100 km) would be comparable. What is not
      // displayable is the pair: MissionResultText.formatMiss prints the altitude miss and the
      // inclination miss in one string, and this mission aims at no inclination. Teaching a
      // formatter shared with LEO and GEO about absence, in a lot where no screen can be looked
      // at, buys less than it risks; L7 brings the card and the reader (spec
      // docs/lunar-orbit/07-conception-L5.md section 3.2).
      case MissionSpec.LunarOrbit lunarOrbit -> Optional.empty();
    };
  }

  /**
   * Resolves the displayable target of an entry.
   *
   * @param entry the mission entry
   * @return the target orbit, or empty for a legacy entry that carries no spec and for a mission
   *     that aims at no orbit — there is nothing to compare an achieved orbit against, and the view
   *     shows the achieved orbit alone
   */
  public static Optional<MissionTargetOrbit> forEntry(MissionEntry entry) {
    Objects.requireNonNull(entry, "entry");
    return entry.spec().flatMap(MissionTargetOrbit::of);
  }
}
