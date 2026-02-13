package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveStatus;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

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

  protected static void validateArcs(TrajectoryOptimizer.OptimizationResult result) {
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
}
