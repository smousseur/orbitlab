package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveStatus;
import com.smousseur.orbitlab.simulation.mission.objective.orbit.OrbitTarget;
import com.smousseur.orbitlab.simulation.mission.objective.orbit.OrbitalObjective;
import com.smousseur.orbitlab.simulation.mission.stage.MissionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.util.FastMath;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.orekit.utils.Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

public class AbstractTrajectoryOptimizerTest {
  protected static void validatePropagation(
      SpacecraftState finalState, Mission mission, double targetAltitude) {
    double finalAlt =
        finalState.getPVCoordinates().getPosition().getNorm() - WGS84_EARTH_EQUATORIAL_RADIUS;
    System.out.printf("Final altitude: %.1f m (target: %.1f m)%n", finalAlt, targetAltitude);

    double finalEcc = finalState.getOrbit().getE();
    System.out.printf("Final eccentricity: %.6f (target: ~0)%n", finalEcc);

    ObjectiveStatus status = new ObjectiveStatus();
    mission.getObjective().evaluate(status, finalState);
    double[] errors = status.getError();
    System.out.printf(
        "Objective errors: altError=%.4f, eccError=%.6f, vrError=%.4f%n",
        errors[0], errors[1], errors[2]);
    System.out.printf("Final mass %.2f", finalState.getMass());

    assertEquals(targetAltitude, finalAlt, 50_000, "Final altitude within 50 km of target");
    assertTrue(finalEcc < 0.1, "Eccentricity should be < 0.1, got " + finalEcc);
  }

  protected static void propagateMission(Mission mission, AbsoluteDate start) {
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

  public static class TestMission extends Mission {

    final double latitude;
    final double longitude;
    final double altitude;
    final double targetAltitude;

    public TestMission(
        String missionName,
        List<MissionStage> stages,
        Vehicle vehicule,
        double latitude,
        double longitude,
        double altitude,
        double targetAltitude) {
      super(missionName, vehicule, stages, getMissionObjective(targetAltitude));
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

  private static MissionObjective getMissionObjective(double targetAltitude) {
    return new OrbitalObjective(new OrbitTarget(SolarSystemBody.EARTH, targetAltitude, 0));
  }
}
