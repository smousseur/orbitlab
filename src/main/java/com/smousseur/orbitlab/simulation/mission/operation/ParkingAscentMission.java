package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticParkingInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The lunar chain cut after its parking insertion: the ascent {@link LunarOrbitMission} and {@link
 * LunarFlybyMission} fly, and nothing after it.
 *
 * <p><b>What a lunar launch window measures the parking orbit on.</b> The window prices and
 * confirms the injection from the state the vehicle is in once parked, and that state is the end of
 * this mission. Flown by the optimizer, budget and seed of the production compute, at the same date
 * and loads, it lands on the insertion the full chain flies to the kilogram.
 *
 * <p><b>One composition for the three missions</b>: the two lunar profiles open with {@link
 * #stages}, so the ascent measured here cannot drift from the one they fly.
 */
public class ParkingAscentMission extends EarthMission {

  /** Name of the parking insertion, whose last sample is the vehicle in its parking orbit. */
  public static final String INSERTION_NAME = "Parking";

  private final double latitude;
  private final double longitude;
  private final double altitude;

  /**
   * @param name the mission name
   * @param configuration the launcher model, propellant loads and payload
   * @param parkingAltitude the parking orbit altitude in meters
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param launchPlane the plane the ascent flies
   */
  public ParkingAscentMission(
      String name,
      LaunchConfiguration configuration,
      double parkingAltitude,
      double latitude,
      double longitude,
      double altitude,
      LaunchPlane launchPlane) {
    this(
        name,
        configuration.toVehicleStack(),
        configuration.ascentProfile(),
        parkingAltitude,
        latitude,
        longitude,
        altitude,
        Objects.requireNonNull(launchPlane, "launchPlane"));
  }

  private ParkingAscentMission(
      String name,
      Vehicle vehicle,
      AscentProfile profile,
      double parkingAltitude,
      double latitude,
      double longitude,
      double altitude,
      LaunchPlane launchPlane) {
    super(
        name,
        vehicle,
        List.copyOf(stages(vehicle, profile, parkingAltitude, launchPlane, latitude)),
        // NaN as on the lunar orbit: the inclination is the plane's, not an aim.
        OrbitInsertionObjective.circular(SolarSystemBody.EARTH, parkingAltitude, Double.NaN));
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

  /** The lunar profiles' own context, Earth-centred with the Moon and the Sun as perturbers. */
  @Override
  public GravitationalContext gravitationalContext() {
    return GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
  }

  /**
   * The ascent to the parking orbit: vertical rise, gravity turn, then the parking insertion named
   * {@link #INSERTION_NAME}.
   *
   * @param vehicle the vehicle stack
   * @param profile the launcher's ascent profile
   * @param parkingAltitude the parking orbit altitude in meters
   * @param launchPlane the plane the ascent flies
   * @param latitude the launch site latitude in degrees
   * @return the stages, in flight order
   */
  static List<MissionStage> stages(
      Vehicle vehicle,
      AscentProfile profile,
      double parkingAltitude,
      LaunchPlane launchPlane,
      double latitude) {
    List<MissionStage> stages = new ArrayList<>();
    stages.add(new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()));
    stages.addAll(
        AscentSequence.gravityTurn(
            vehicle,
            profile,
            GravityTurnConstraints.forTarget(parkingAltitude),
            launchPlane,
            latitude));
    stages.add(new AnalyticParkingInsertionStage(INSERTION_NAME, parkingAltitude));
    return stages;
  }
}
