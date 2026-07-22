package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
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
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
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

import java.util.List;

public class GEOMission extends Mission {
  private static final double DEFAULT_LATITUDE = 5.23;
  private static final double DEFAULT_LONGITUDE = -52.77;
  private static final double DEFAULT_ALTITUDE = 0.0;
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
      String name, double parkingAltitude, double latitude, double longitude, double altitude) {
    this(name, parkingAltitude, GEO_ALTITUDE, latitude, longitude, altitude, 0.0);
  }

  public GEOMission(
      String name,
      double parkingAltitude,
      double targetAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(name, parkingAltitude, targetAltitude, latitude, longitude, altitude, 0.0);
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

  /**
   * Creates a GEO mission whose vehicle and flight profile come from a launch configuration
   * (launcher-driven profile).
   *
   * @param name the mission name
   * @param configuration the launcher model, propellant loads and payload
   * @param parkingAltitude the parking orbit altitude in meters
   * @param targetAltitude the target orbit altitude in meters
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param finalInclination the target final inclination in degrees
   */
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
        configuration.toVehicleStack(),
        configuration.ascentProfile(),
        parkingAltitude,
        targetAltitude,
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
      double latitude,
      double longitude,
      double altitude,
      double finalInclination) {
    super(
        name,
        vehicle,
        buildStages(profile, parkingAltitude, targetAltitude, finalInclination),
        new OrbitInsertionObjective(
            SolarSystemBody.EARTH, parkingAltitude, targetAltitude, FastMath.toRadians(latitude)));
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
      double finalInclination) {
    return List.of(
        new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()),
        new GravityTurnStage(
            "Gravity turn",
            profile.pitchKickAngleDeg(),
            profile.interstageCoastDuration(),
            GravityTurnConstraints.forTarget(parkingAltitude)),
        new AnalyticParkingInsertionStage("Parking", parkingAltitude),
        new CoastingStage("Coasting parking", true),
        new AnalyticGtoInjectionStage("GTO injection", targetAltitude),
        // Index 1 = the launcher's upper stage (stack = [S1, S2, payload]). Declaring it makes the
        // separation refuse to fire when the gravity turn left propellant in S1 — S1 would still
        // be the active stage and get jettisoned in S2's place, after which S2 silently takes over
        // the payload kick motor's burns (bilan 10 §6 follow-up, found by the I7 GEO run).
        new StageSeparationStage(
            "S2 separation", profile.interstageCoastDuration(), UPPER_STAGE_INDEX),
        // The AKM burn owns its ~5 h lead-in coast to the GTO apogee and centers the burn on it
        // (an hours-long 400 N burn starting AT apogee would ruin the insertion). Its plan runs a
        // Newton on the aimed perigee so the finite-burn apogee inflation lands on target; the
        // trim then raises the deliberately-low perigee with a short, drift-free burn.
        new AnalyticApogeeCircularizationStage(
            "Apogee circularization (AKM)", targetAltitude, FastMath.toRadians(finalInclination)),
        new AnalyticTrimBurnStage("Trim", targetAltitude, FastMath.toRadians(finalInclination)),
        // Node-targeted plane trim (bilan 08 §3.5): the hours-long AKM burn leaves a ~0.25° plane
        // residual it cannot correct off-node; a short out-of-plane burn at the node cleans it up.
        new AnalyticPlaneTrimAtNodeStage(
            "Plane trim (node)", FastMath.toRadians(finalInclination)),
        new CoastingStage("Coasting", null));
  }
}
