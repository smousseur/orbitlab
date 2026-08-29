package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticParkingInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.LunarApproachCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.LunarInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.ParkingCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.stage.TLIBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.ArrayList;
import java.util.List;

/**
 * The lunar orbiter of the product: a mission that lifts off from a pad, parks, leaves for the
 * Moon, crosses its sphere of influence, and captures into a circular lunar orbit (MIS-5 / L5, spec
 * {@code docs/lunar-orbit/07-conception-L5.md} §3).
 *
 * <p><b>It is the first mission of the repository with stages after a sphere-of-influence
 * crossing.</b> Everything up to the crossing is {@link LunarFlybyMission}'s chain; what this class
 * adds is the arrival — a selenocentric approach coast, a retrograde insertion burn, and a terminal
 * coast that has to declare the arc it flies on.
 *
 * <p><b>The lunar orbit altitude is aimed exactly once</b>, by the injection: {@code TLIBurnStage}
 * receives it as the perilune to reach, and {@code LunarInsertionStage} circularises the perilune
 * it actually arrives at, taking no target of its own (L4 §4.1). A second parameter would be a
 * second truth about one target. Checking that the two coincide is the objective's job.
 *
 * <p><b>The S2 jettison sits just after the injection</b>, exactly where {@code GEOMission} places
 * its own and for the same reason: {@code resolveActiveStage} resolves by mass, so nothing makes
 * the payload's propulsion active before the launcher's upper stage is gone. Unlike the flyby,
 * which carries its spent stage to the Moon because nothing was ever handed over, this chain has a
 * burn left to fly and no launcher stage that could fly it — {@code maxCoastDuration} is 7 200 s on
 * the Falcon Heavy S2 against a ~265 000 s translunar coast (découpage §2.3 pt 1).
 *
 * <p><b>The Moon and the Sun are declared at mission level</b>, as on the flyby, and that is what
 * makes the crossing work at all: {@code ArcTransition} derives the selenocentric context
 * mechanically from this one, and the three stages that follow the crossing declare it through the
 * same rule rather than writing it out.
 */
public class LunarOrbitMission extends EarthMission {

  /** Stack index of the launcher's upper stage — the one "S2 separation" is meant to jettison. */
  private static final int UPPER_STAGE_INDEX = 1;

  /**
   * The circular parking orbit the translunar injection leaves from (m).
   *
   * <p>Its own constant rather than {@link LunarFlybyMission#DEFAULT_PARKING_ALTITUDE}, which holds
   * the same number for the same reason: a lunar orbit insertion has nothing to do with a flyby,
   * and reading the other mission's constant would make this chain follow, in silence, a value
   * changed for that one.
   *
   * <p><b>Not offered as a wizard field</b> (MIS-5 / L7 §3), on MIS-4 / L0's measurement: the aim
   * converges identically from 185 to 400 km, so a slider there would be a choice with nothing
   * behind it. 400 km is what the closure flight of L5 actually flew.
   */
  public static final double DEFAULT_PARKING_ALTITUDE = 400_000.0;

  /**
   * How far the translunar coast goes if it never reaches the lunar sphere (s).
   *
   * <p><b>A bound, not a duration</b> (MIS-5 / L1 §4): a coast that ends at a boundary owes a
   * figure for the case where the boundary never comes, and without one it would fall on {@code
   * StageChainRunner}'s 7 200 s safety net — three days short, while reporting itself complete.
   *
   * <p>Derived from the transfer's own time of flight rather than written as a fresh number, and
   * set so that reaching it reads as a failure: the crossing is measured at 3.071–3.148 d (L0
   * measure 3) and the safety net would be 0.083 d, so a stop at five days is unmistakably neither.
   */
  public static final double TRANSLUNAR_COAST_BOUND_SECONDS =
      TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS * 1.25;

  /**
   * Name of the terminal coast.
   *
   * <p><b>The one load-bearing string of this chain</b>: {@code
   * MissionLoadEvaluator.FINAL_COAST_STAGE} compares against it to decide which samples the
   * insertion objective is scored on. The eleven other stage names are read by nothing.
   */
  private static final String FINAL_COAST_NAME = "Coasting";

  /** Name of the parking coast, shaped like {@link LunarFlybyMission#PARKING_COAST_NAME}. */
  public static final String PARKING_COAST_NAME = "Parking coast";

  /** Name of the bounded translunar coast — the stage the sphere of influence terminates. */
  public static final String TRANSLUNAR_COAST_NAME = "Translunar coast";

  /** Name of the selenocentric approach, which ends at the insertion's ignition point. */
  public static final String APPROACH_COAST_NAME = "Lunar approach";

  /** Name of the insertion burn. */
  public static final String INSERTION_NAME = "Lunar orbit insertion";

  private final double latitude;
  private final double longitude;
  private final double altitude;

  /**
   * Creates a lunar orbit insertion flown from a ground site.
   *
   * <p>The ascent plane is the one a due-east launch reaches for free, {@code i = φ}: there is no
   * choice to offer, the aim having a single scalar degree of freedom already spent on the
   * perilune.
   *
   * @param name the mission name
   * @param configuration the launcher model, propellant loads and payload
   * @param parkingAltitude the parking orbit altitude in meters
   * @param orbitAltitude the circular lunar orbit altitude in meters above the lunar surface
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  public LunarOrbitMission(
      String name,
      LaunchConfiguration configuration,
      double parkingAltitude,
      double orbitAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(
        name,
        configuration.toVehicleStack(),
        configuration.ascentProfile(),
        parkingAltitude,
        orbitAltitude,
        latitude,
        longitude,
        altitude);
  }

  private LunarOrbitMission(
      String name,
      Vehicle vehicle,
      AscentProfile profile,
      double parkingAltitude,
      double orbitAltitude,
      double latitude,
      double longitude,
      double altitude) {
    super(
        name,
        vehicle,
        buildStages(profile, parkingAltitude, orbitAltitude, latitude),
        // The inclination is NOT aimed at, and NaN says so rather than a plausible number. The
        // geometry delivers 131.1° to 153.4° depending on the epoch (L0 measure 1), and the closed
        // form that would predict it — 180° − φ — is right to 2° three times out of four and wrong
        // by 20.3° the fourth. Nothing in the repository reads this component, so the marker of
        // absence costs no reader anything (spec §3.2).
        OrbitInsertionObjective.circular(SolarSystemBody.MOON, orbitAltitude, Double.NaN));
    this.latitude = latitude;
    this.longitude = longitude;
    this.altitude = altitude;
  }

  @Override
  protected double getLatitude() {
    return latitude;
  }

  @Override
  protected double getLongitude() {
    return longitude;
  }

  @Override
  protected double getAltitude() {
    return altitude;
  }

  /**
   * Earth-centred with the Moon and the Sun as perturbers — the context {@code ArcTransition}
   * derives the lunar one from, both when the coast crosses the sphere of influence and when the
   * three stages after it declare their arc.
   */
  @Override
  public GravitationalContext gravitationalContext() {
    return GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
  }

  private static List<MissionStage> buildStages(
      AscentProfile profile, double parkingAltitude, double orbitAltitude, double latitude) {
    List<MissionStage> stages = new ArrayList<>();
    stages.add(new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()));
    stages.addAll(
        AscentSequence.gravityTurn(
            profile,
            GravityTurnConstraints.forTarget(parkingAltitude),
            LaunchPlane.dueEast(latitude),
            latitude));
    stages.addAll(
        List.of(
            new AnalyticParkingInsertionStage("Parking", parkingAltitude),
            new ParkingCoastStage(PARKING_COAST_NAME),
            new TLIBurnStage("Translunar injection", orbitAltitude),
            // Index 1 = the launcher's upper stage (stack = [S1, S2, payload]). Declaring it makes
            // the separation refuse to fire when the gravity turn left propellant in S1, which
            // would otherwise jettison S1 in S2's place and hand the insertion to the wrong engine.
            new StageSeparationStage(
                "S2 separation", profile.interstageCoastDuration(), UPPER_STAGE_INDEX),
            new TranslunarCoastStage(TRANSLUNAR_COAST_NAME, TRANSLUNAR_COAST_BOUND_SECONDS),
            new LunarApproachCoastStage(APPROACH_COAST_NAME),
            new LunarInsertionStage(INSERTION_NAME),
            // The terminal coast is selenocentric and has to say so: StageLegRunner converts every
            // stage entry into the context the stage declares, comparing frames by reference, so a
            // coast inheriting the mission's terrestrial context would really transform the arrival
            // back into GCRF and the mission would be measured against the Earth (spec §3.1).
            new CoastingStage(FINAL_COAST_NAME, null, SolarSystemBody.MOON)));
    return List.copyOf(stages);
  }
}
