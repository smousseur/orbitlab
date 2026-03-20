package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

class GravityTurnProblemTest {

  private static GravityTurnManeuver maneuver;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    Vehicle vehicle = LaunchVehicle.getLauncherStage1Vehicle();
    maneuver = new GravityTurnManeuver(vehicle, 0, 0, Math.PI / 2);
  }

  @Test
  void getNumVariables_returnsTwo() {
    GravityTurnProblem p = new GravityTurnProblem(maneuver, null, null);
    assertEquals(2, p.getNumVariables());
  }

  @Test
  void getLowerBounds_correctValues() {
    GravityTurnProblem p = new GravityTurnProblem(maneuver, null, null);
    double[] bounds = p.getLowerBounds();
    assertEquals(2, bounds.length);
    assertEquals(30.0, bounds[0], 1e-10);
    assertEquals(0.3, bounds[1], 1e-10);
  }

  @Test
  void getUpperBounds_correctValues() {
    GravityTurnProblem p = new GravityTurnProblem(maneuver, null, null);
    double[] bounds = p.getUpperBounds();
    assertEquals(2, bounds.length);
    assertEquals(450.0, bounds[0], 1e-10);
    assertEquals(3.0, bounds[1], 1e-10);
  }

  @Test
  void getInitialSigma_correctValues() {
    GravityTurnProblem p = new GravityTurnProblem(maneuver, null, null);
    double[] sigma = p.getInitialSigma();
    assertEquals(2, sigma.length);
    assertEquals(30.0, sigma[0], 1e-10);
    assertEquals(0.3, sigma[1], 1e-10);
  }

  @Test
  void getAcceptableCost_returnsTightThreshold() {
    GravityTurnProblem p = new GravityTurnProblem(maneuver, null, null);
    assertEquals(1e-4, p.getAcceptableCost(), 1e-12);
  }

  @Test
  void buildInitialGuess_withinBounds() {
    GravityTurnProblem p = new GravityTurnProblem(maneuver, null, null);
    double[] guess = p.buildInitialGuess();
    double[] lower = p.getLowerBounds();
    double[] upper = p.getUpperBounds();

    assertEquals(2, guess.length);
    // transitionTime
    assertTrue(guess[0] >= lower[0], "transitionTime guess should be >= lower bound");
    assertTrue(guess[0] <= upper[0], "transitionTime guess should be <= upper bound");
    // exponent
    assertEquals(1.0, guess[1], 1e-10);
  }

  @Test
  void computeCost_lowerAltitudeState_hasHigherCost() {
    // The cost function penalizes apogee below target window
    Frame gcrf = OrekitService.get().gcrf();
    AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
    GravityTurnConstraints constraints = GravityTurnConstraints.forTarget(400_000);

    // State 1: ~150km circular orbit — apogee well below target (300km)
    double r1 = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 150_000;
    double v1 = Math.sqrt(Constants.WGS84_EARTH_MU / r1);
    SpacecraftState state1 =
        new SpacecraftState(
                new CartesianOrbit(
                    new PVCoordinates(new Vector3D(r1, 0, 0), new Vector3D(0, v1, 0)),
                    gcrf,
                    date,
                    Constants.WGS84_EARTH_MU))
            .withMass(10_000);

    // State 2: ~325km circular orbit — apogee inside [300km, 350km] target window
    double r2 = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 325_000;
    double v2 = Math.sqrt(Constants.WGS84_EARTH_MU / r2);
    SpacecraftState state2 =
        new SpacecraftState(
                new CartesianOrbit(
                    new PVCoordinates(new Vector3D(r2, 0, 0), new Vector3D(0, v2, 0)),
                    gcrf,
                    date,
                    Constants.WGS84_EARTH_MU))
            .withMass(10_000);

    GravityTurnProblem problem = new GravityTurnProblem(maneuver, state1, constraints);
    double cost1 = problem.computeCost(state1);
    double cost2 = problem.computeCost(state2);

    assertTrue(cost1 > 0, "Cost should be positive for sub-target orbit");
    assertTrue(cost2 >= 0, "Cost should be non-negative");
    assertTrue(cost1 > cost2, "Lower altitude (further from target) should have higher cost");
  }

  @Test
  void computeCost_hyperbolicOrbit_hasHighCost() {
    Frame gcrf = OrekitService.get().gcrf();
    AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
    GravityTurnConstraints constraints = GravityTurnConstraints.forTarget(400_000);

    // Escape velocity state: very high speed → hyperbolic trajectory
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 300_000;
    double vEscape = Math.sqrt(2 * Constants.WGS84_EARTH_MU / r) * 1.1; // 10% above escape velocity
    SpacecraftState hyperbolicState =
        new SpacecraftState(
                new CartesianOrbit(
                    new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, vEscape, 0)),
                    gcrf,
                    date,
                    Constants.WGS84_EARTH_MU))
            .withMass(10_000);

    GravityTurnProblem problem = new GravityTurnProblem(maneuver, hyperbolicState, constraints);
    double cost = problem.computeCost(hyperbolicState);

    assertTrue(cost > 10.0, "Hyperbolic orbit should incur a large penalty cost");
  }
}
