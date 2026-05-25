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
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LauncherType;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
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
  private static final int ASCENSION_DURATION = 10;
  private static final double DEFAULT_LATITUDE = 45.96;
  private static final double DEFAULT_LONGITUDE = 63.30;
  private static final double DEFAULT_ALTITUDE = 0.0;

  private final double latitude;
  private final double longitude;
  private final double altitude;

  /**
   * Creates a circular LEO mission with default Kourou launch site.
   *
   * @param name the mission name
   * @param targetAltitude the target orbital altitude in meters
   */
  public LEOMission(String name, double targetAltitude) {
    this(name, targetAltitude, targetAltitude);
  }

  /**
   * Creates a LEO mission with default Kourou launch site. Pass equal perigee and apogee for a
   * circular orbit.
   *
   * @param name the mission name
   * @param perigeeAltitude the target perigee altitude in meters
   * @param apogeeAltitude the target apogee altitude in meters
   */
  public LEOMission(String name, double perigeeAltitude, double apogeeAltitude) {
    this(
        name,
        perigeeAltitude,
        apogeeAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE);
  }

  /**
   * Creates a circular LEO mission with a custom launch site.
   *
   * @param name the mission name
   * @param targetAltitude the target orbital altitude in meters
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  public LEOMission(
      String name, double targetAltitude, double latitude, double longitude, double altitude) {
    this(name, targetAltitude, targetAltitude, latitude, longitude, altitude);
  }

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
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(
        name,
        perigeeAltitude,
        apogeeAltitude,
        latitude,
        longitude,
        altitude,
        Mission.getDefaultVehicle(),
        LauncherType.FALCON_HEAVY.modelPath());
  }

  /**
   * Creates a LEO mission with a custom launch site and an explicit vehicle stack and 3D model.
   *
   * @param name the mission name
   * @param perigeeAltitude the target perigee altitude in meters
   * @param apogeeAltitude the target apogee altitude in meters
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param vehicle the vehicle stack to use for the mission
   * @param modelPath the JME3 asset path of the 3D model representing the vehicle
   */
  public LEOMission(
      String name,
      double perigeeAltitude,
      double apogeeAltitude,
      double latitude,
      double longitude,
      double altitude,
      VehicleStack vehicle,
      String modelPath) {
    super(
        name,
        vehicle,
        buildStages(perigeeAltitude, apogeeAltitude, latitude),
        buildObjective(perigeeAltitude, apogeeAltitude, latitude),
        modelPath);
    this.latitude = latitude;
    this.longitude = longitude;
    this.altitude = altitude;
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

  private static List<MissionStage> buildStages(
      double perigeeAltitude, double apogeeAltitude, double latitude) {
    return List.of(
        new VerticalAscentStage("Vertical Ascent", ASCENSION_DURATION),
        new GravityTurnStage(
            "Gravity turn",
            ASCENSION_DURATION,
            3.0,
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
