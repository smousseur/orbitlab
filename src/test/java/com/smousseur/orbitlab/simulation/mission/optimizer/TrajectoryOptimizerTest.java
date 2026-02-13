package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.orekit.utils.Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.orbit.OrbitTarget;
import com.smousseur.orbitlab.simulation.mission.objective.orbit.OrbitalObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.arcs.HohmannTransferProblem;
import com.smousseur.orbitlab.simulation.mission.stage.BallisticCoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.JettisonStage;
import com.smousseur.orbitlab.simulation.mission.stage.MissionStage;
import com.smousseur.orbitlab.simulation.mission.stage.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.*;
import java.util.ArrayList;
import java.util.List;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

public class TrajectoryOptimizerTest extends AbstractTrajectoryOptimizerTest {

  private static final double TARGET_ALTITUDE_M = 400_000; // 400 km

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void testSubOrbitalTransfertOptimization() {
    SubOrbitalMission mission =
        new SubOrbitalMission(getSubOrbitalMissionStages(), 52.77, -5.23, 0, TARGET_ALTITUDE_M);
    propagateMission(mission, OrekitTime.utcNow());
    SpacecraftState currentState = mission.getCurrentState();
  }

  /** Tests Hohmann transfer optimization from parking orbit */
  @Test
  void testHohmannTransfertOptimization() {
    HohmannMission mission =
        new HohmannMission(
            List.of(), Spacecraft.getSpacecraft(), 5.23, -52.77, 0, TARGET_ALTITUDE_M);
    AbsoluteDate start = OrekitTime.utcNow();

    // ──── 1. Démarrer en orbite circulaire basse (200 km) au lieu de suborbital ────
    Frame gcrf = OrekitService.get().gcrf();
    double mu = Constants.WGS84_EARTH_MU;
    double parkingAlt = 200_000.0; // 200 km
    double r = WGS84_EARTH_EQUATORIAL_RADIUS + parkingAlt;
    double v = FastMath.sqrt(mu / r); // vitesse circulaire

    Orbit parkingOrbit =
        new CartesianOrbit(
            new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)), gcrf, start, mu);
    Vehicle vehicle = mission.getVehicle();
    PropulsionSystem propulsion = vehicle.getPropulsion();
    SpacecraftState parkingState = new SpacecraftState(parkingOrbit).withMass(vehicle.getMass());
    mission.setCurrentState(parkingState);

    SpacecraftState postLaunchState = mission.getCurrentState();
    double postLaunchAlt =
        postLaunchState.getPVCoordinates().getPosition().getNorm() - WGS84_EARTH_EQUATORIAL_RADIUS;
    System.out.printf("Parking orbit altitude: %.1f m%n", postLaunchAlt);
    System.out.printf(
        "Propulsion: thrust=%.0f N, ISP=%.0f s%n", propulsion.thrust(), propulsion.isp());
    System.out.printf("Vehicle mass: %.1f kg%n", postLaunchState.getMass());

    // ──── 2. Find optimal solutions ────
    double rTarget = WGS84_EARTH_EQUATORIAL_RADIUS + TARGET_ALTITUDE_M;
    double aTransfer = (r + rTarget) / 2.0;
    double tTransfer = FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);

    AbsoluteDate transferEnd = postLaunchState.getDate().shiftedBy(tTransfer * 1.2);

    HohmannTransferProblem problem =
        new HohmannTransferProblem(mission.getCurrentState(), transferEnd, propulsion, rTarget);
    TrajectoryOptimizer optimizer =
        new TrajectoryOptimizer(problem, mission.getObjective()).withTolerance(1e-6);
    TrajectoryOptimizer.OptimizationResult result = optimizer.optimize();

    SpacecraftState finalState = result.finalState();

    validateArcs(result);

    // ──── 3. Re-propagate → validate ────
    validatePropagation(finalState, mission, TARGET_ALTITUDE_M);
  }

  private static class HohmannMission extends Mission {

    final double latitude;
    final double longitude;
    final double altitude;
    final double targetAltitude;

    public HohmannMission(
        List<MissionStage> stages,
        Vehicle vehicule,
        double latitude,
        double longitude,
        double altitude,
        double targetAltitude) {
      super("Low Earth Orbit Mission", vehicule, stages, getMissionObjective(targetAltitude));
      this.latitude = latitude;
      this.longitude = longitude;
      this.altitude = altitude;
      this.targetAltitude = targetAltitude;
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
  }

  private static class SubOrbitalMission extends HohmannMission {
    public SubOrbitalMission(
        List<MissionStage> stages,
        double latitude,
        double longitude,
        double altitude,
        double targetAltitude) {
      super(
          getSubOrbitalMissionStages(),
          getSubOrbitalVehicle(),
          latitude,
          longitude,
          altitude,
          targetAltitude);
    }
  }

  private static VehicleStack getSubOrbitalVehicle() {
    List<Vehicle> vehicles = new ArrayList<>();
    LaunchVehicle launcher = LaunchVehicle.getLauncherVechicle();
    vehicles.add(launcher);
    vehicles.add(Spacecraft.getSpacecraft());
    return new VehicleStack(vehicles, launcher.getPropulsion());
  }

  private static List<MissionStage> getSubOrbitalMissionStages() {
    VerticalAscentStage ascentStage = new VerticalAscentStage("Ascent", 120);
    JettisonStage jettisonStage = new JettisonStage("Jettison");
    BallisticCoastingStage coastingStage = new BallisticCoastingStage("Coasting", 15.0);
    return List.of(ascentStage, jettisonStage, coastingStage);
  }

  private static MissionObjective getMissionObjective(double targetAltitude) {
    return new OrbitalObjective(new OrbitTarget(SolarSystemBody.EARTH, targetAltitude, 0));
  }
}
