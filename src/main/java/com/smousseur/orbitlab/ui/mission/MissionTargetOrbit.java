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
 * @param inclination the requested inclination (rad), or {@code NaN} when the mission aims at none
 */
public record MissionTargetOrbit(
    double perigeeAltitude, double apogeeAltitude, double inclination) {

  /**
   * Whether this target commands a plane.
   *
   * <p>A lunar orbit does not: the inclination it reaches around the Moon follows from the arrival
   * geometry and the epoch, so {@code OrbitInsertionObjective} carries {@code NaN} rather than a
   * number nothing aimed at. {@code NaN} and not a nullable component, on the marker MIS-5 already
   * uses for exactly this absence — {@code OrbitInsertionObjective.inclination()} and {@code
   * MissionOptimizer.resolveTargetAltitude}.
   *
   * @return {@code true} when an inclination was asked for
   */
  public boolean hasInclination() {
    return !Double.isNaN(inclination);
  }

  /**
   * Resolves the displayable target of a spec.
   *
   * <p><b>A flyby has none, and fabricating one would be worse than showing nothing</b> (MIS-4 / L4
   * §6.1). A lunar <em>orbit</em>, by contrast, has one — see the branch below. There is no
   * (perigee, apogee, inclination) triple to display beside a lunar approach; a degenerate one
   * would put a false geocentric target next to the achieved orbit, which is exactly the kind of
   * silence this chantier removes. Both consumers already handle the absence — they meet it on
   * legacy entries carrying no spec — so this costs no new case in the UI.
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
      // Altitudes above the *Moon*, and they are comparable because since MIS-5 / L2 the achieved
      // orbit is reported against the arc body and L5's terminal coast declares the lunar one. The
      // inclination is the absent half of the pair L5 could not display: it is undergone, not
      // aimed at, so the miss line drops its degree field rather than inventing a target for it.
      case MissionSpec.LunarOrbit lunarOrbit ->
          Optional.of(
              new MissionTargetOrbit(
                  lunarOrbit.orbitAltitude(), lunarOrbit.orbitAltitude(), Double.NaN));
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
