package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticHohmannTransferStage;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticTrimBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.TransfertManeuverStage;
import com.smousseur.orbitlab.simulation.mission.stage.TransfertTwoManeuverStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.util.FastMath;

/**
 * Concrete LEO (Low Earth Orbit) insertion mission launching from Kourou (French Guiana). Stages:
 * Vertical Ascent → Gravity turn (S1) → S1 separation → Gravity turn (S2) → Transfer → Trim →
 * Coasting.
 */
public class LEOMission extends EarthMission {
  private final double latitude;
  private final double longitude;
  private final double altitude;

  /**
   * Creates a LEO mission whose vehicle and flight profile come from a launch configuration
   * (launcher-driven profile).
   *
   * @param name the mission name
   * @param configuration the launcher model, propellant loads and payload
   * @param perigeeAltitude the target perigee altitude in meters
   * @param apogeeAltitude the target apogee altitude in meters
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  public LEOMission(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(
        name,
        configuration.toVehicleStack(),
        configuration.ascentProfile(),
        perigeeAltitude,
        apogeeAltitude,
        latitude,
        longitude,
        altitude);
  }

  public LEOMission(String name, LaunchConfiguration configuration, double targetAltitude) {
    this(
        name,
        configuration,
        targetAltitude,
        targetAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE);
  }

  private LEOMission(
      String name,
      Vehicle vehicle,
      AscentProfile profile,
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(
        name,
        vehicle,
        buildStages(profile, perigeeAltitude, apogeeAltitude, latitude),
        perigeeAltitude,
        apogeeAltitude,
        latitude,
        longitude,
        altitude);
  }

  private LEOMission(
      String name,
      Vehicle vehicle,
      List<MissionStage> stages,
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude) {
    super(name, vehicle, stages, buildObjective(perigeeAltitude, apogeeAltitude, latitude));
    this.latitude = latitude;
    this.longitude = longitude;
    this.altitude = altitude;
  }

  public static LEOMission circularWithOptimizedTransfer(
      String name, LaunchConfiguration configuration, double targetAltitude) {
    return circularWithOptimizedTransfer(
        name, configuration, targetAltitude, DEFAULT_LATITUDE, DEFAULT_LONGITUDE, DEFAULT_ALTITUDE);
  }

  public static LEOMission circularWithOptimizedTransfer(
      String name,
      LaunchConfiguration configuration,
      double targetAltitude,
      double latitude,
      double longitude,
      double altitude) {
    AscentProfile profile = configuration.ascentProfile();
    List<MissionStage> stages =
        ascentThen(
            profile,
            GravityTurnConstraints.forTarget(targetAltitude),
            new TransfertTwoManeuverStage(
                "Transfert", targetAltitude, FastMath.toRadians(latitude)),
            new AnalyticTrimBurnStage("Trim", targetAltitude, FastMath.toRadians(latitude)),
            new CoastingStage("Coasting", null));
    return new LEOMission(
        name,
        configuration.toVehicleStack(),
        stages,
        targetAltitude,
        targetAltitude,
        latitude,
        longitude,
        altitude);
  }

  public static LEOMission ellipticWithOptimizedTransfer(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude) {
    return ellipticWithOptimizedTransfer(
        name,
        configuration,
        perigeeAltitude,
        apogeeAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE);
  }

  public static LEOMission ellipticWithOptimizedTransfer(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude) {
    AscentProfile profile = configuration.ascentProfile();
    List<MissionStage> stages =
        ascentThen(
            profile,
            GravityTurnConstraints.forTarget(perigeeAltitude),
            new TransfertManeuverStage(
                "Transfert", perigeeAltitude, apogeeAltitude, FastMath.toRadians(latitude)),
            // The trim burn at the next apogee raises the perigee to the target perigee, shaping
            // the ellipse (target perigee, achieved apogee) — its altitude argument is the perigee.
            new AnalyticTrimBurnStage("Trim", perigeeAltitude, FastMath.toRadians(latitude)),
            new CoastingStage("Coasting", null));
    return new LEOMission(
        name,
        configuration.toVehicleStack(),
        stages,
        perigeeAltitude,
        apogeeAltitude,
        latitude,
        longitude,
        altitude);
  }

  public LEOMission(String name, double targetAltitude) {
    this(name, targetAltitude, targetAltitude);
  }

  public LEOMission(String name, double perigeeAltitude, double apogeeAltitude) {
    this(
        name,
        defaultConfiguration(),
        perigeeAltitude,
        apogeeAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE);
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

  /** Default configuration of the historical ctors: Falcon Heavy fully loaded (spec 06 I1). */
  private static LaunchConfiguration defaultConfiguration() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  private static List<MissionStage> buildStages(
      AscentProfile profile, double perigeeAltitude, double apogeeAltitude, double latitude) {
    return ascentThen(
        profile,
        GravityTurnConstraints.forTarget(perigeeAltitude),
        new AnalyticHohmannTransferStage(
            "Transfert", perigeeAltitude, apogeeAltitude, FastMath.toRadians(latitude)),
        new AnalyticTrimBurnStage("Trim", perigeeAltitude, FastMath.toRadians(latitude)),
        new CoastingStage("Coasting", null));
  }

  /**
   * The ascent — vertical climb then the three explicit gravity-turn phases ({@code Gravity turn
   * (S1) → S1 separation → Gravity turn (S2)}, spec {@code
   * specs/mission-stages/01-separations-implicites.md} §4.2) — followed by the orbital phases of a
   * given profile. Shared by the three LEO variants so none of them can drift on how the launcher
   * stages.
   *
   * @param profile the launcher's flight profile
   * @param constraints the gravity turn's hand-off targets
   * @param orbitalPhases the phases flown after MECO, in order
   * @return the full stage list
   */
  private static List<MissionStage> ascentThen(
      AscentProfile profile, GravityTurnConstraints constraints, MissionStage... orbitalPhases) {
    List<MissionStage> stages = new ArrayList<>();
    stages.add(new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()));
    stages.addAll(AscentSequence.gravityTurn(profile, constraints));
    stages.addAll(List.of(orbitalPhases));
    return List.copyOf(stages);
  }

  private static MissionObjective buildObjective(
      double perigeeAltitude, double apogeeAltitude, double latitudeDegrees) {
    return new OrbitInsertionObjective(
        SolarSystemBody.EARTH,
        perigeeAltitude,
        apogeeAltitude,
        FastMath.toRadians(latitudeDegrees));
  }
}
