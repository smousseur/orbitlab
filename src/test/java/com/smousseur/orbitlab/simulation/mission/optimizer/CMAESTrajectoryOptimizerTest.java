package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.orekit.utils.Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TwoManeuverTransferProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

public class CMAESTrajectoryOptimizerTest {

  private static final double TARGET_ALTITUDE_M = 350_000; // 400 km

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void testTransfertFromOrbit() {
    Frame gcrf = OrekitService.get().gcrf();
    double mu = Constants.WGS84_EARTH_MU;
    double altitude = 200_000.0;
    double r = WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    double speed = 6000.0;
    double gamma = FastMath.toRadians(30.0); // 30° flight path angle

    double vRadial = speed * FastMath.sin(gamma); // 3000 m/s up
    double vTang = speed * FastMath.cos(gamma); // 5196 m/s horizontal

    // Position on X axis, velocity in XY plane
    Vector3D pos = new Vector3D(r, 0, 0);
    Vector3D vel = new Vector3D(vRadial, vTang, 0);

    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    CartesianOrbit orbit = new CartesianOrbit(new PVCoordinates(pos, vel), gcrf, epoch, mu);

    double vCirc = FastMath.sqrt(mu / r);
    System.out.printf(
        "State: alt=%.0fkm, v=%.0f m/s, gamma=%.0f deg%n",
        altitude / 1000, speed, FastMath.toDegrees(gamma));
    System.out.printf("vRadial=%.0f m/s, vTang=%.0f m/s, vCirc=%.0f m/s%n", vRadial, vTang, vCirc);
    System.out.printf("Tangential deficit=%.0f m/s%n", vCirc - vTang);

    PropulsionSystem propulsion = new PropulsionSystem(450, 10000);
    TwoManeuverTransferProblem problem =
        new TwoManeuverTransferProblem(
            new KeplerianOrbit(orbit), 1000.0, TARGET_ALTITUDE_M, propulsion);
    CMAESTrajectoryOptimizer optimizer = new CMAESTrajectoryOptimizer(problem, 20000);
    OptimizationResult result = optimizer.optimize();
    SpacecraftState finalState = result.bestState();

    double finalAlt =
        finalState.getPVCoordinates().getPosition().getNorm() - WGS84_EARTH_EQUATORIAL_RADIUS;
    System.out.printf("Final altitude: %.1f m (target: %.1f m)%n", finalAlt, TARGET_ALTITUDE_M);

    double finalEcc = finalState.getOrbit().getE();
    System.out.printf("Final eccentricity: %.6f (target: ~0)%n", finalEcc);

    System.out.printf("Final mass %.2f", finalState.getMass());

    assertEquals(TARGET_ALTITUDE_M, finalAlt, 50_000, "Final altitude within 50 km of target");
    assertTrue(finalEcc < 0.1, "Eccentricity should be < 0.1, got " + finalEcc);
  }
}
