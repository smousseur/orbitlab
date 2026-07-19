package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticHohmannTransferStage;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticTrimBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.TransfertTwoManeuverStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.TopocentricFrame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Concrete LEO (Low Earth Orbit) insertion mission launching from Kourou (French Guiana). Stages:
 * Vertical Ascent → Gravity Turn → Transfer Two-Maneuver → Coasting.
 */
public class LEOMission extends Mission {
  private static final double DEFAULT_LATITUDE = 45.96;
  private static final double DEFAULT_LONGITUDE = 63.30;
  private static final double DEFAULT_ALTITUDE = 0.0;

  private final double latitude;
  private final double longitude;
  private final double altitude;

  /**
   * Creates a LEO mission with a custom launch site. Pass equal perigee and apogee for a circular
   * orbit.
   *
   * @param name the mission name
   * @param perigeeAltitude the target perigee altitude in meters
   * @param apogeeAltitude the target apogee altitude in meters
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  public LEOMission(
      String name,
      Vehicle vehicle,
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(
        name,
        vehicle,
        AscentProfile.LEGACY,
        perigeeAltitude,
        apogeeAltitude,
        latitude,
        longitude,
        altitude);
  }

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

  /**
   * LEO mission variant whose transfer is CMA-ES-optimized (spec 06 I6) instead of analytic:
   * burn 1 explores timing, duration (up to actual depletion) and thrust angles, seeded by the
   * analytic Hohmann solution. Circular targets only — the two-burn problem circularizes at the
   * target altitude. Opt-in: the regular ctors keep the analytic Hohmann profile.
   *
   * @param name the mission name
   * @param configuration the launcher model, propellant loads and payload
   * @param targetAltitude the circular target orbit altitude (m)
   * @return the mission, launching from the default site
   */
  public static LEOMission withOptimizedTransfer(
      String name, LaunchConfiguration configuration, double targetAltitude) {
    AscentProfile profile = configuration.ascentProfile();
    List<MissionStage> stages =
        List.of(
            new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()),
            new GravityTurnStage(
                "Gravity turn",
                profile.pitchKickAngleDeg(),
                profile.interstageCoastDuration(),
                GravityTurnConstraints.forTarget(targetAltitude)),
            new TransfertTwoManeuverStage(
                "Transfert", targetAltitude, FastMath.toRadians(DEFAULT_LATITUDE)),
            new AnalyticTrimBurnStage(
                "Trim", targetAltitude, FastMath.toRadians(DEFAULT_LATITUDE)),
            new CoastingStage("Coasting", null));
    return new LEOMission(
        name,
        configuration.toVehicleStack(),
        stages,
        targetAltitude,
        targetAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE);
  }

  public LEOMission(String name, Vehicle vehicle, double targetAltitude) {
    this(
        name,
        vehicle,
        targetAltitude,
        targetAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE);
  }

  public LEOMission(String name, double targetAltitude) {
    this(name, targetAltitude, targetAltitude);
  }

  public LEOMission(String name, double perigeeAltitude, double apogeeAltitude) {
    this(
        name,
        perigeeAltitude,
        apogeeAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE);
  }

  public LEOMission(
      String name, double targetAltitude, double latitude, double longitude, double altitude) {
    this(name, targetAltitude, targetAltitude, latitude, longitude, altitude);
  }

  public LEOMission(
      String name,
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(
        name,
        defaultConfiguration(),
        perigeeAltitude,
        apogeeAltitude,
        latitude,
        longitude,
        altitude);
  }

  @Override
  public SpacecraftState getInitialState(AbsoluteDate initialDate) {
    OneAxisEllipsoid earth = OrekitService.get().getEarthEllipsoid();
    Frame itrf = OrekitService.get().itrf();
    Frame gcrf = OrekitService.get().gcrf();
    GeodeticPoint launchPad =
        new GeodeticPoint(FastMath.toRadians(latitude), FastMath.toRadians(longitude), altitude);
    TopocentricFrame launchFrame = new TopocentricFrame(earth, launchPad, "Launch Pad");
    PVCoordinates initialPVInGCRF =
        itrf.getTransformTo(gcrf, initialDate)
            .transformPVCoordinates(
                new PVCoordinates(launchFrame.getCartesianPoint(), Vector3D.ZERO));
    Orbit initialOrbit =
        new CartesianOrbit(initialPVInGCRF, gcrf, initialDate, Constants.WGS84_EARTH_MU);
    return new SpacecraftState(initialOrbit).withMass(this.getVehicle().getMass());
  }

  /** Default configuration of the historical ctors: Falcon Heavy fully loaded (spec 06 I1). */
  private static LaunchConfiguration defaultConfiguration() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  private static List<MissionStage> buildStages(
      AscentProfile profile, double perigeeAltitude, double apogeeAltitude, double latitude) {
    return List.of(
        new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()),
        new GravityTurnStage(
            "Gravity turn",
            profile.pitchKickAngleDeg(),
            profile.interstageCoastDuration(),
            GravityTurnConstraints.forTarget(perigeeAltitude)),
        new AnalyticHohmannTransferStage(
            "Transfert", perigeeAltitude, apogeeAltitude, FastMath.toRadians(latitude)),
        new AnalyticTrimBurnStage("Trim", perigeeAltitude, FastMath.toRadians(latitude)),
        new CoastingStage("Coasting", null));
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
