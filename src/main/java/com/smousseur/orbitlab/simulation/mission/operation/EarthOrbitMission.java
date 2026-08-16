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
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Concrete Earth-orbit insertion mission: vertical ascent → gravity turn (S1) → S1 separation →
 * gravity turn (S2) → transfer → trim → coasting. The target plane is whatever {@link LaunchPlane}
 * asks for — the site's free due-east plane, or a polar, sun-synchronous or otherwise inclined one.
 *
 * <p><b>Not to be confused with {@link EarthMission}</b>, its superclass, despite the names sitting
 * one word apart. {@code EarthMission} is the launch-pad geometry: it turns a geodetic site into the
 * initial state, and knows nothing of orbits. {@code EarthOrbitMission} is one concrete flight
 * profile built on top of it, alongside {@link GEOMission}.
 *
 * <p>This class was {@code LEOMission} before MIS-7 (spec {@code
 * docs/earth-orbit/01-mission-terre-parametrable.md} §3.3). The rename is a rename: the three
 * factories and their stage chains are unchanged, they now carry the target plane instead of
 * recomputing {@code toRadians(latitude)} at each call site. {@link LaunchPlane#dueEast} reproduces
 * the historical behaviour exactly.
 */
public class EarthOrbitMission extends EarthMission {
  private final double latitude;
  private final double longitude;
  private final double altitude;
  private final LaunchPlane launchPlane;

  /**
   * Creates an Earth-orbit mission flying the analytic Hohmann profile into the site's free plane.
   *
   * @param name the mission name
   * @param configuration the launcher model, propellant loads and payload
   * @param perigeeAltitude the target perigee altitude in meters
   * @param apogeeAltitude the target apogee altitude in meters
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  public EarthOrbitMission(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(
        name,
        configuration,
        perigeeAltitude,
        apogeeAltitude,
        LaunchPlane.dueEast(latitude),
        latitude,
        longitude,
        altitude);
  }

  /**
   * Creates an Earth-orbit mission flying the analytic Hohmann profile into a given plane.
   *
   * @param name the mission name
   * @param configuration the launcher model, propellant loads and payload
   * @param perigeeAltitude the target perigee altitude in meters
   * @param apogeeAltitude the target apogee altitude in meters
   * @param launchPlane the target orbital plane
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  public EarthOrbitMission(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude,
      LaunchPlane launchPlane,
      double latitude,
      double longitude,
      double altitude) {
    this(
        name,
        configuration.toVehicleStack(),
        buildStages(
            configuration.ascentProfile(),
            perigeeAltitude,
            apogeeAltitude,
            launchPlane,
            latitude),
        perigeeAltitude,
        apogeeAltitude,
        launchPlane,
        latitude,
        longitude,
        altitude);
  }

  public EarthOrbitMission(String name, LaunchConfiguration configuration, double targetAltitude) {
    this(
        name,
        configuration,
        targetAltitude,
        targetAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE);
  }

  private EarthOrbitMission(
      String name,
      Vehicle vehicle,
      List<MissionStage> stages,
      double perigeeAltitude,
      double apogeeAltitude,
      LaunchPlane launchPlane,
      double latitude,
      double longitude,
      double altitude) {
    super(
        name,
        vehicle,
        stages,
        buildObjective(perigeeAltitude, apogeeAltitude, launchPlane, latitude));
    this.latitude = latitude;
    this.longitude = longitude;
    this.altitude = altitude;
    this.launchPlane = Objects.requireNonNull(launchPlane, "launchPlane");
  }

  public static EarthOrbitMission circularWithOptimizedTransfer(
      String name, LaunchConfiguration configuration, double targetAltitude) {
    return circularWithOptimizedTransfer(
        name, configuration, targetAltitude, DEFAULT_LATITUDE, DEFAULT_LONGITUDE, DEFAULT_ALTITUDE);
  }

  public static EarthOrbitMission circularWithOptimizedTransfer(
      String name,
      LaunchConfiguration configuration,
      double targetAltitude,
      double latitude,
      double longitude,
      double altitude) {
    return circularWithOptimizedTransfer(
        name,
        configuration,
        targetAltitude,
        LaunchPlane.dueEast(latitude),
        latitude,
        longitude,
        altitude);
  }

  public static EarthOrbitMission circularWithOptimizedTransfer(
      String name,
      LaunchConfiguration configuration,
      double targetAltitude,
      LaunchPlane launchPlane,
      double latitude,
      double longitude,
      double altitude) {
    AscentProfile profile = configuration.ascentProfile();
    List<MissionStage> stages =
        ascentThen(
            profile,
            GravityTurnConstraints.forTarget(targetAltitude),
            launchPlane,
            latitude,
            new TransfertTwoManeuverStage(
                "Transfert", targetAltitude, launchPlane.targetInclination()),
            new AnalyticTrimBurnStage("Trim", targetAltitude, launchPlane.targetInclination()));
    return new EarthOrbitMission(
        name,
        configuration.toVehicleStack(),
        stages,
        targetAltitude,
        targetAltitude,
        launchPlane,
        latitude,
        longitude,
        altitude);
  }

  public static EarthOrbitMission ellipticWithOptimizedTransfer(
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

  public static EarthOrbitMission ellipticWithOptimizedTransfer(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude) {
    return ellipticWithOptimizedTransfer(
        name,
        configuration,
        perigeeAltitude,
        apogeeAltitude,
        LaunchPlane.dueEast(latitude),
        latitude,
        longitude,
        altitude);
  }

  public static EarthOrbitMission ellipticWithOptimizedTransfer(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude,
      LaunchPlane launchPlane,
      double latitude,
      double longitude,
      double altitude) {
    AscentProfile profile = configuration.ascentProfile();
    List<MissionStage> stages =
        ascentThen(
            profile,
            GravityTurnConstraints.forTarget(perigeeAltitude),
            launchPlane,
            latitude,
            new TransfertManeuverStage(
                "Transfert", perigeeAltitude, apogeeAltitude, launchPlane.targetInclination()),
            // The trim burn at the next apogee raises the perigee to the target perigee, shaping
            // the ellipse (target perigee, achieved apogee) — its altitude argument is the perigee.
            new AnalyticTrimBurnStage("Trim", perigeeAltitude, launchPlane.targetInclination()));
    return new EarthOrbitMission(
        name,
        configuration.toVehicleStack(),
        stages,
        perigeeAltitude,
        apogeeAltitude,
        launchPlane,
        latitude,
        longitude,
        altitude);
  }

  public EarthOrbitMission(String name, double targetAltitude) {
    this(name, targetAltitude, targetAltitude);
  }

  public EarthOrbitMission(String name, double perigeeAltitude, double apogeeAltitude) {
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

  /**
   * @return the target orbital plane this mission flies into
   */
  public LaunchPlane getLaunchPlane() {
    return launchPlane;
  }

  /** Default configuration of the historical ctors: Falcon Heavy fully loaded (spec 06 I1). */
  private static LaunchConfiguration defaultConfiguration() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  private static List<MissionStage> buildStages(
      AscentProfile profile,
      double perigeeAltitude,
      double apogeeAltitude,
      LaunchPlane launchPlane,
      double latitude) {
    return ascentThen(
        profile,
        GravityTurnConstraints.forTarget(perigeeAltitude),
        launchPlane,
        latitude,
        new AnalyticHohmannTransferStage(
            "Transfert", perigeeAltitude, apogeeAltitude, launchPlane.targetInclination()),
        new AnalyticTrimBurnStage("Trim", perigeeAltitude, launchPlane.targetInclination()));
  }

  /**
   * The ascent — vertical climb then the three explicit gravity-turn phases ({@code Gravity turn
   * (S1) → S1 separation → Gravity turn (S2)}, spec {@code
   * docs/mission-stages/01-separations-implicites.md} §4.2) — followed by the orbital phases of a
   * given profile, and closed by the coast. Shared by the three variants so none of them can drift
   * on how the launcher stages, nor on when the plane residual is cleaned up.
   *
   * @param profile the launcher's flight profile
   * @param constraints the gravity turn's hand-off targets
   * @param launchPlane the target orbital plane
   * @param latitude the launch site latitude in degrees
   * @param orbitalPhases the phases flown after MECO, in order, before the closing coast
   * @return the full stage list
   */
  private static List<MissionStage> ascentThen(
      AscentProfile profile,
      GravityTurnConstraints constraints,
      LaunchPlane launchPlane,
      double latitude,
      MissionStage... orbitalPhases) {
    List<MissionStage> stages = new ArrayList<>();
    stages.add(new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()));
    stages.addAll(AscentSequence.gravityTurn(profile, constraints, launchPlane, latitude));
    stages.addAll(List.of(orbitalPhases));
    stages.add(new CoastingStage("Coasting", null));
    return List.copyOf(stages);
  }

  private static MissionObjective buildObjective(
      double perigeeAltitude,
      double apogeeAltitude,
      LaunchPlane launchPlane,
      double latitudeDegrees) {
    return new OrbitInsertionObjective(
        SolarSystemBody.EARTH,
        perigeeAltitude,
        apogeeAltitude,
        launchPlane.requireReachableFrom(latitudeDegrees).targetInclination());
  }
}
