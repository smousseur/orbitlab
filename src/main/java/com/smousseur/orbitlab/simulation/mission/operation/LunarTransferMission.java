package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarInjectionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import java.util.List;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * The acceptance flight of PHY-4: a translunar transfer from a parking orbit to a perilune, flown
 * as two arcs across the lunar sphere of influence (spec {@code
 * docs/multi-corps/08-conception-L6.md} §3).
 *
 * <p><b>It is not a mission of the product, and that is deliberate.</b> There is no wizard profile,
 * no {@code MissionType}, no {@code MissionSpec} and no launch window: the découpage puts a real
 * {@code TLIBurnStage} in {@code MIS-4}, and what this class exists for is to give the multi-arc
 * machinery of L3, L4 and L5 one real trajectory to be judged on. It reaches the application
 * through the legacy {@code MissionEntry(Mission)} door, behind the {@code mission.lunarDemo}
 * property.
 *
 * <p><b>Nor does it launch.</b> {@link #getInitialState} <em>is</em> the parking orbit, built by
 * {@link TranslunarInjectionPlan#parkingState} to contain the Moon's direction at arrival. That is
 * what dispenses with a launch window — there is no ground site to wait for — and it is also what
 * keeps {@code PropellantBudget}, {@code LaunchPlane} and {@code EarthMission} out of this lot
 * entirely, all three of them still Earth-hardcoded (L1 §4.1).
 *
 * <p><b>Two stages, and both of them minimal.</b> The injection applies an impulse and settles; the
 * coast declares the lunar sphere and flies until the horizon. The perturbers are declared once
 * here rather than twice on the stages, because {@link Mission#gravitationalContext()} is already
 * the default a stage inherits.
 */
public class LunarTransferMission extends Mission {
  /** Perilune altitude the injection aims for (m). */
  public static final double DEFAULT_PERILUNE_ALTITUDE = 100_000.0;

  /**
   * Total mission duration (s).
   *
   * <p><b>Four and a half days, and the half day is measured rather than prudential.</b> L4 §11.2
   * measured a 54 h dwell inside the lunar sphere; with the four-day time of flight the crossing
   * falls near 3.25 d and perilune near 4.0 d, so an exit cannot happen before ~5.5 d. Stopping at
   * 4.5 d makes the arc sequence exactly {@code [EARTH, MOON]} instead of leaving it to the
   * geometry of the shot, and puts perilune well inside — "propagated to perilune", literally.
   *
   * <p>It also keeps the trail undecimated: at the 60 s coast sampling step this is ~6 545 points
   * against {@code TrajectoryPolyline}'s 8 192 budget, so the drawn trace is the flown trace vertex
   * for vertex — true of no other mission in the repository (L3 §10.2 measured 9 992 points on the
   * LEO-400).
   */
  public static final double MISSION_DURATION_SECONDS = 4.5 * 86_400.0;

  /** Structural mass of the translunar stage (kg). */
  private static final double DRY_MASS = 500.0;

  /**
   * Propellant loaded (kg). Sized by Tsiolkovsky for the measured ~3 181 m/s injection at the
   * spacecraft engine's 300 s: a mass ratio of 2.95 needs 1 200 kg on a 500 kg dry stage and leaves
   * about 77 kg unburnt, which is a margin and not a coincidence.
   */
  private static final double PROPELLANT_LOAD = 1_200.0;

  private final double perileneAltitude;

  /** Creates the acceptance flight aiming for {@link #DEFAULT_PERILUNE_ALTITUDE}. */
  public LunarTransferMission(String name) {
    this(name, DEFAULT_PERILUNE_ALTITUDE);
  }

  /**
   * @param name the mission name
   * @param perileneAltitude the perilune altitude to aim for (m)
   */
  public LunarTransferMission(String name, double perileneAltitude) {
    super(
        name,
        new Spacecraft(
            DRY_MASS, PROPELLANT_LOAD, PROPELLANT_LOAD, PropulsionSystem.getSpacecraftPropulsion()),
        stages(perileneAltitude),
        new OrbitInsertionObjective(SolarSystemBody.MOON, perileneAltitude, perileneAltitude, 0.0));
    this.perileneAltitude = perileneAltitude;
    setHorizon(new MissionHorizon.FixedDuration(MISSION_DURATION_SECONDS));
  }

  /**
   * The parking orbit itself — this mission does not launch (see the class javadoc).
   *
   * @param initialDate the date the injection impulse is applied
   * @return the parking state, in GCRF
   */
  @Override
  public SpacecraftState getInitialState(AbsoluteDate initialDate) {
    return TranslunarInjectionPlan.parkingState(initialDate, getVehicle().getMass());
  }

  /**
   * Earth-centred with the Moon and the Sun as perturbers.
   *
   * <p>Declared here rather than on each stage: both of them inherit it, and the switch rule of L4
   * §4.2 derives the lunar side from it mechanically — {@code earth().withPerturbers(MOON, SUN)}
   * becomes {@code moon().withPerturbers(EARTH, SUN)} without anything naming the Sun twice. It is
   * that symmetry which makes the crossing a change of bookkeeping rather than a change of physics,
   * measured at 0.246 m by L4 §2.2.
   */
  @Override
  public GravitationalContext gravitationalContext() {
    return GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
  }

  /**
   * @return the perilune altitude this mission aims for (m)
   */
  public double getPerileneAltitude() {
    return perileneAltitude;
  }

  private static List<MissionStage> stages(double perileneAltitude) {
    return List.of(
        new TranslunarInjectionStage("Translunar injection", perileneAltitude),
        // Named "Coasting" because MissionLoadEvaluator.FINAL_COAST_STAGE matches on that name,
        // and a mission whose terminal coast is invisible to the feasibility predicate would be a
        // trap for whoever wires this into the sizing path later.
        new TranslunarCoastStage("Coasting"));
  }
}
