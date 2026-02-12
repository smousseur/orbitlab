package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveStatus;
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
import java.util.concurrent.*;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.TopocentricFrame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

public class TrajectoryOptimizerTest {

  private static final double TARGET_ALTITUDE_M = 400_000; // 400 km

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void testOptimization() throws InterruptedException {
    LEOMission mission = new LEOMission(5.23, -52.77, 0, TARGET_ALTITUDE_M);
    AbsoluteDate start = OrekitTime.utcNow();

    // ──── 1. Démarrer en orbite circulaire basse (200 km) au lieu de suborbital ────
    Frame gcrf = OrekitService.get().gcrf();
    double mu = Constants.WGS84_EARTH_MU;
    double earthRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
    double parkingAlt = 200_000.0; // 200 km
    double r = earthRadius + parkingAlt;
    double v = FastMath.sqrt(mu / r); // vitesse circulaire

    Orbit parkingOrbit =
        new CartesianOrbit(
            new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)), gcrf, start, mu);
    SpacecraftState parkingState =
        new SpacecraftState(parkingOrbit).withMass(mission.getVehicle().getMass());
    mission.setCurrentState(parkingState);

    SpacecraftState postLaunchState = mission.getCurrentState();
    double postLaunchAlt = postLaunchState.getPVCoordinates().getPosition().getNorm() - earthRadius;
    System.out.printf("Parking orbit altitude: %.1f m%n", postLaunchAlt);
    System.out.printf("Parking orbit eccentricity: %.6f%n", postLaunchState.getOrbit().getE());
    System.out.printf(
        "Propulsion: thrust=%.0f N, ISP=%.0f s%n",
        mission.getVehicle().getPropulsion().thrust(), mission.getVehicle().getPropulsion().isp());
    System.out.printf("Vehicle mass: %.1f kg%n", postLaunchState.getMass());

    // ──── 2. Find optimal thrust profile ────
    double rTarget = earthRadius + TARGET_ALTITUDE_M;
    double aTransfer = (r + rTarget) / 2.0;
    double tTransfer = FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);
    AbsoluteDate transferEnd = postLaunchState.getDate().shiftedBy(tTransfer * 1.2);
    double targetR = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + TARGET_ALTITUDE_M;

    HohmannTransferProblem problem =
        new HohmannTransferProblem(
            mission.getCurrentState(), transferEnd, mission.getVehicle().getPropulsion(), targetR);

    TrajectoryOptimizer optimizer =
        new TrajectoryOptimizer(problem, mission.getObjective()).withTolerance(1e-6);

    TrajectoryOptimizer.OptimizationResult result = optimizer.optimize();
    SpacecraftState finalState = result.finalState();
    double[] residuals = result.residuals();
    double[] controls = result.variables();

    assertNotNull(controls, "Thrust controls must not be null");
    assertEquals(10, controls.length);
    double[] physical = result.physicalParams();
    for (int arc = 0; arc < 2; arc++) {
      int o = arc * 5;
      System.out.printf("Arc %d:%n", arc + 1);
      System.out.printf("  tStart   = %.2f s (%.2f min)%n", physical[o], physical[o] / 60.0);
      System.out.printf("  duration = %.2f s%n", physical[o + 1]);
      System.out.printf(
          "  alpha    = %.4f rad (%.1f°)%n", physical[o + 2], FastMath.toDegrees(physical[o + 2]));
      System.out.printf(
          "  delta    = %.4f rad (%.1f°)%n", physical[o + 3], FastMath.toDegrees(physical[o + 3]));
      System.out.printf("  throttle = %.1f%%%n", physical[o + 4] * 100);
    }

    // ──── 3. Re-propagate → validate ────
    double finalAlt = finalState.getPVCoordinates().getPosition().getNorm() - earthRadius;
    System.out.printf("Final altitude: %.1f m (target: %.1f m)%n", finalAlt, TARGET_ALTITUDE_M);

    double finalEcc = finalState.getOrbit().getE();
    System.out.printf("Final eccentricity: %.6f (target: ~0)%n", finalEcc);

    ObjectiveStatus status = new ObjectiveStatus();
    mission.getObjective().evaluate(status, finalState);
    double[] errors = status.getError();
    System.out.printf(
        "Objective errors: altError=%.4f, eccError=%.6f, vrError=%.4f%n",
        errors[0], errors[1], errors[2]);

    assertEquals(TARGET_ALTITUDE_M, finalAlt, 50_000, "Final altitude within 50 km of target");
    assertTrue(finalEcc < 0.1, "Eccentricity should be < 0.1, got " + finalEcc);
  }

  private static void propagateToApogee(LEOMission mission, AbsoluteDate start) {
    mission.start(start);

    double stepS = 10.0;
    AbsoluteDate end = start.shiftedBy(2, TimeUnit.DAYS);

    AbsoluteDate t = start;
    int i = 0;
    int maxIters = (int) FastMath.ceil(end.durationFrom(start) / stepS) + 10;

    while (mission.isOnGoing() && t.compareTo(end) < 0) {
      t = t.shiftedBy(stepS);
      mission.update(t);

      if (++i > maxIters) {
        throw new IllegalStateException("Too many iterations, stuck? t=" + t);
      }
    }
  }

  private SpacecraftState rePropagate(Mission mission, double[] deltaV, AbsoluteDate endDate) {

    NumericalPropagator propagator = createPropagator();

    SpacecraftState baseState = mission.getCurrentState();

    // Appliquer le ΔV sur l'état initial
    Vector3D position = baseState.getPVCoordinates().getPosition();
    Vector3D velocity = baseState.getPVCoordinates().getVelocity();
    Vector3D newVelocity = velocity.add(new Vector3D(deltaV[0], deltaV[1], deltaV[2]));

    PVCoordinates newPV = new PVCoordinates(position, newVelocity);
    CartesianOrbit newOrbit =
        new CartesianOrbit(
            newPV, baseState.getFrame(), baseState.getDate(), Constants.WGS84_EARTH_MU);

    SpacecraftState initialState = new SpacecraftState(newOrbit).withMass(baseState.getMass());
    propagator.setInitialState(initialState);

    try {
      return propagator.propagate(endDate);
    } catch (Exception e) {
      System.err.println("Re-propagation failed: " + e.getMessage());
      return initialState;
    }
  }

  private static NumericalPropagator createPropagator() {
    double[] absTol = {10.0, 10.0, 10.0, 1e-2, 1e-2, 1e-2, 1e-1};
    double[] relTol = {1e-6, 1e-6, 1e-6, 1e-6, 1e-6, 1e-6, 1e-6};
    NumericalPropagator p =
        new NumericalPropagator(new DormandPrince853Integrator(1e-6, 1000.0, absTol, relTol));
    p.setOrbitType(OrbitType.CARTESIAN);
    return p;
  }

  private static class LEOMission extends Mission {

    final double latitude;
    final double longitude;
    final double altitude;
    final double targetAltitude;

    public LEOMission(double latitude, double longitude, double altitude, double targetAltitude) {
      super(
          "Low Earth Orbit Mission",
          Spacecraft.getSpacecraft(), // getLEOVehicle(),
          getLEOMissionStages(),
          getLEOMissionObjective(targetAltitude));
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

  private static VehicleStack getLEOVehicle() {

    List<Vehicle> vehicles = new ArrayList<>();
    LaunchVehicle launcher = LaunchVehicle.getLauncherVechicle();
    vehicles.add(launcher);
    vehicles.add(Spacecraft.getSpacecraft());
    return new VehicleStack(vehicles, launcher.getPropulsion());
  }

  private static List<MissionStage> getLEOMissionStages() {
    VerticalAscentStage ascentStage = new VerticalAscentStage("Ascent", 60);
    JettisonStage jettisonStage = new JettisonStage("Jettison");
    BallisticCoastingStage coastingStage = new BallisticCoastingStage("Coasting");
    return List.of(ascentStage, jettisonStage, coastingStage);
  }

  private static MissionObjective getLEOMissionObjective(double targetAltitude) {
    return new OrbitalObjective(new OrbitTarget(SolarSystemBody.EARTH, targetAltitude, 0));
  }
}
