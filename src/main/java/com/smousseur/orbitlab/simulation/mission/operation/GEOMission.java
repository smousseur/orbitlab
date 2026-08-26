package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticApogeeCircularizationStage;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticGtoInjectionStage;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticParkingInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticPlaneTrimAtNodeStage;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticTrimBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.util.FastMath;

public class GEOMission extends EarthMission {
  public static final int GEO_ALTITUDE = 35_786_000;

  /** Stack index of the launcher's upper stage — the one "S2 separation" is meant to jettison. */
  private static final int UPPER_STAGE_INDEX = 1;

  private final double latitude;
  private final double longitude;
  private final double altitude;

  public GEOMission(String name, double parkingAltitude, double targetAltitude) {
    this(name, parkingAltitude, targetAltitude, 0.0);
  }

  public GEOMission(
      String name, double parkingAltitude, double targetAltitude, double finalInclination) {
    this(
        name,
        parkingAltitude,
        targetAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE,
        finalInclination);
  }

  public GEOMission(
      String name,
      double parkingAltitude,
      double targetAltitude,
      double latitude,
      double longitude,
      double altitude,
      double finalInclination) {
    this(
        name,
        defaultConfiguration(),
        parkingAltitude,
        targetAltitude,
        latitude,
        longitude,
        altitude,
        finalInclination);
  }

  public GEOMission(
      String name,
      LaunchConfiguration configuration,
      double parkingAltitude,
      double targetAltitude,
      double latitude,
      double longitude,
      double altitude,
      double finalInclination) {
    this(
        name,
        configuration,
        parkingAltitude,
        targetAltitude,
        LaunchPlane.dueEast(latitude),
        latitude,
        longitude,
        altitude,
        finalInclination);
  }

  /**
   * Creates a high-orbit mission whose <em>ascent</em> is steered into a given plane.
   *
   * <p><b>The ascent plane and the final inclination are two different things here</b>, and this
   * constructor exists because MIS-7 made the difference matter. A geostationary mission climbs due
   * east, reaches the parking orbit at the site latitude, and cancels that inclination with the
   * apogee burn — the plane change is <em>part of</em> the circularization, which is why {@code
   * finalInclination} is 0 while the ascent flies 5.23°. It could not be otherwise: no launch from
   * Kourou reaches an equatorial plane directly.
   *
   * <p>A medium Earth orbit at 55° is the opposite case. That plane <em>is</em> reachable from the
   * site, so flying the ascent due east and rotating 50° at apogee would be paying kilometres per
   * second for something the climb gives away — measured on this very chain before the plane was
   * wired through: the circularization asked for 2 969 m/s instead of 1 404, burnt the kick motor
   * dry, and still left the orbit at 34.5° with a perigee of 3 391 km. Steering the ascent into 55°
   * leaves the apogee burn nothing to do but circularize.
   *
   * @param name the mission name
   * @param configuration the launcher model, propellant loads and payload
   * @param parkingAltitude the parking orbit altitude in meters
   * @param targetAltitude the final circular orbit altitude in meters
   * @param ascentPlane the plane the ascent is steered into; {@link LaunchPlane#dueEast} for the
   *     geostationary profile, whose plane change belongs to the apogee burn
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param finalInclination the inclination of the orbit finally delivered, in degrees
   */
  public GEOMission(
      String name,
      LaunchConfiguration configuration,
      double parkingAltitude,
      double targetAltitude,
      LaunchPlane ascentPlane,
      double latitude,
      double longitude,
      double altitude,
      double finalInclination) {
    this(
        name,
        configuration.toVehicleStack(),
        configuration.ascentProfile(),
        parkingAltitude,
        targetAltitude,
        ascentPlane,
        latitude,
        longitude,
        altitude,
        finalInclination);
  }

  private GEOMission(
      String name,
      Vehicle vehicle,
      AscentProfile profile,
      double parkingAltitude,
      double targetAltitude,
      LaunchPlane ascentPlane,
      double latitude,
      double longitude,
      double altitude,
      double finalInclination) {
    super(
        name,
        vehicle,
        buildStages(
            profile, parkingAltitude, targetAltitude, ascentPlane, latitude, finalInclination),
        new OrbitInsertionObjective(
            SolarSystemBody.EARTH,
            parkingAltitude,
            targetAltitude,
            ascentPlane.targetInclination()));
    this.latitude = latitude;
    this.longitude = longitude;
    this.altitude = altitude;
  }

  @Override
  public double getLatitude() {
    return latitude;
  }

  @Override
  public double getLongitude() {
    return longitude;
  }

  @Override
  public double getAltitude() {
    return altitude;
  }

  /**
   * Default configuration of the historical ctors: Falcon Heavy fully loaded with the catalog GEO
   * satellite, AKM at full capacity. The split GEO profile (spec 06 I5) delegates the apogee
   * circularization to the payload's kick motor, so an AKM-less payload cannot fly it.
   */
  private static LaunchConfiguration defaultConfiguration() {
    return LaunchConfiguration.fullyLoaded(
        Launchers.FALCON_HEAVY,
        Payloads.GEO_SAT.toSpacecraft(
            Payloads.GEO_SAT.defaultDryMass(), Payloads.GEO_SAT.akmPropellantCapacity()));
  }

  private static List<MissionStage> buildStages(
      AscentProfile profile,
      double parkingAltitude,
      double targetAltitude,
      LaunchPlane ascentPlane,
      double latitude,
      double finalInclination) {
    List<MissionStage> stages = new ArrayList<>();
    stages.add(new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()));
    // The ascent is three explicit phases (spec 01 §4.2), so "S1 separation" and "S2 separation"
    // below are now two instances of the same class with expected stack indices 0 and 1 — the
    // launcher's staging is stated once, in one place, instead of half-implied by a detector.
    stages.addAll(
        AscentSequence.gravityTurn(
            profile, GravityTurnConstraints.forTarget(parkingAltitude), ascentPlane, latitude));
    stages.addAll(
        List.of(
            new AnalyticParkingInsertionStage("Parking", parkingAltitude),
            new CoastingStage("Coasting parking", true),
            new AnalyticGtoInjectionStage("GTO injection", targetAltitude),
            // Index 1 = the launcher's upper stage (stack = [S1, S2, payload]). Declaring it makes
            // the separation refuse to fire when the gravity turn left propellant in S1 — S1 would
            // still be the active stage and get jettisoned in S2's place, after which S2 silently
            // takes over the payload kick motor's burns (bilan 10 §6 follow-up, I7 GEO run).
            new StageSeparationStage(
                "S2 separation", profile.interstageCoastDuration(), UPPER_STAGE_INDEX),
            // The AKM burn owns its ~5 h lead-in coast to the GTO apogee and centers the burn on it
            // (an hours-long 400 N burn starting AT apogee would ruin the insertion). Its plan runs
            // a Newton on the aimed perigee so the finite-burn apogee inflation lands on target;
            // the trim then raises the deliberately-low perigee with a short, drift-free burn.
            new AnalyticApogeeCircularizationStage(
                "Circularization", targetAltitude, FastMath.toRadians(finalInclination)),
            new AnalyticTrimBurnStage("Trim", targetAltitude, FastMath.toRadians(finalInclination)),
            // Node-targeted plane trim (bilan 08 §3.5): the hours-long AKM burn leaves a ~0.25°
            // plane residual it cannot correct off-node; a short out-of-plane burn at the node
            // cleans it up.
            new AnalyticPlaneTrimAtNodeStage("Plane trim", FastMath.toRadians(finalInclination)),
            new CoastingStage("Coasting", null)));
    return List.copyOf(stages);
  }
}
