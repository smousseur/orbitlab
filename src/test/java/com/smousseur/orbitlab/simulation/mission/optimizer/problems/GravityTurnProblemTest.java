package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jspecify.annotations.NonNull;
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
    maneuver =
        new GravityTurnManeuver(
            vehicle, vehicle.getMass(), 0.0, Math.PI / 2, 0.0, FlightContext.earth());
  }

  @Test
  void getNumVariables_returnsTwo() {
    GravityTurnProblem p = getGravityTurnProblem();
    assertEquals(2, p.getNumVariables());
  }

  private static @NonNull GravityTurnProblem getGravityTurnProblem() {
    GravityTurnConstraints constraints = GravityTurnConstraints.forTarget(400_000);
    return new GravityTurnProblem(maneuver, null, constraints);
  }

  @Test
  void getLowerBounds_correctValues() {
    GravityTurnProblem p = getGravityTurnProblem();
    double[] bounds = p.getLowerBounds();
    assertEquals(2, bounds.length);
    // The staging invariant is a cost penalty, not a bound: Hipparchus normalizes the search space
    // by the box width, so moving this floor would perturb every mission (see the penalty tests
    // below and GravityTurnProblem#getLowerBounds).
    assertEquals(30.0, bounds[0], 1e-10);
    assertEquals(0.1, bounds[1], 1e-10);
  }

  @Test
  void getUpperBounds_correctValues() {
    GravityTurnProblem p = getGravityTurnProblem();
    double[] bounds = p.getUpperBounds();
    assertEquals(2, bounds.length);
    assertEquals(520.0, bounds[0], 1e-10);
    assertEquals(3.0, bounds[1], 1e-10);
  }

  @Test
  void getInitialSigma_correctValues() {
    GravityTurnProblem p = getGravityTurnProblem();
    double[] sigma = p.getInitialSigma();
    assertEquals(2, sigma.length);
    assertEquals(30.0, sigma[0], 1e-10);
    assertEquals(0.3, sigma[1], 1e-10);
  }

  @Test
  void getAcceptableCost_sitsAboveIrreducibleFpaFloor() {
    // Bilan 08 §3.6: the acceptance threshold must clear the irreducible W_FPA_SOFT·fpa² floor so
    // the GT concludes without exhausting retries against a structural minimum. At the reference
    // hand-off (fpa ≈ 2.1°) that floor is ≈ 0.034; the threshold is sized at the FPA-soft cost of a
    // 2.5° hand-off, so it must land above 0.034 while staying well below any real constraint
    // violation (apogee/vTan penalties reach ~0.1+).
    GravityTurnProblem p = getGravityTurnProblem();
    double referenceFpaFloor = 25.0 * Math.pow(Math.toRadians(2.1), 2); // ≈ 0.0337
    assertTrue(
        p.getAcceptableCost() > referenceFpaFloor,
        "Acceptable cost must clear the reference FPA-soft floor (~0.034)");
    assertTrue(
        p.getAcceptableCost() < 0.1,
        "Acceptable cost must stay below genuine constraint-violation costs");
  }

  @Test
  void buildInitialGuess_withinBounds() {
    GravityTurnProblem p = getGravityTurnProblem();
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

  // ── Staging floor penalty: no longer a guard, now a search regularizer ────
  //
  // It once protected a real failure mode (a MECO before the in-ascent jettison detector left the
  // first stage attached, bilan 10 §5.3). The jettison is now a phase, so that cannot happen — but
  // removing the penalty was measured to cost +47 % evaluations and the reproducibility of the LEO
  // solution at the fixed seed, for an identical final orbit, so it was kept (spec
  // docs/mission-stages/02-baseline-n2.md §12). These fixtures pin it either way.
  //
  // The penalty is applied on top of the trajectory cost, so they assert the exact delta between
  // grading the SAME hand-off state with and without a below-staging candidate recorded. That keeps
  // them independent of whatever the propagation produced.

  /** Circular orbit at {@code altitude}, dated late enough to take computeCost's nominal path. */
  private static SpacecraftState circularStateAfter(double altitude, double secondsAfterEpoch) {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    double v = Math.sqrt(Constants.WGS84_EARTH_MU / r);
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
                OrekitService.get().gcrf(),
                AbsoluteDate.J2000_EPOCH.shiftedBy(secondsAfterEpoch),
                Constants.WGS84_EARTH_MU))
        .withMass(10_000);
  }

  private static GravityTurnProblem problemWithRealInitialState() {
    return new GravityTurnProblem(
        maneuver, circularStateAfter(150_000, 0.0), GravityTurnConstraints.forTarget(400_000));
  }

  @Test
  void computeCost_mecoBeforeStaging_addsAPenaltyDominatingAnyNominalCost() {
    GravityTurnProblem p = problemWithRealInitialState();
    SpacecraftState handOff = circularStateAfter(325_000, 200.0);
    double baseline = p.computeCost(handOff);

    double shortfall = 5.0;
    p.propagate(new double[] {maneuver.getStagingCompleteTime() - shortfall, 1.0});
    double penalized = p.computeCost(handOff);

    // 1e3 base + 1.0 per second short. Below the floor transitionTime controls nothing — every
    // candidate there flies the same ascent — so the cliff is what stops CMA-ES from spending
    // budget on that plateau.
    assertEquals(baseline + 1_000.0 + shortfall, penalized, 1e-6);
    assertTrue(penalized > 1_000.0, "a below-staging candidate must never outrank a valid one");
  }

  @Test
  void computeCost_mecoAfterStaging_carriesNoPenalty() {
    GravityTurnProblem p = problemWithRealInitialState();
    SpacecraftState handOff = circularStateAfter(325_000, 200.0);
    double baseline = p.computeCost(handOff);

    p.propagate(new double[] {maneuver.getStagingCompleteTime() + 20.0, 1.0});

    assertEquals(baseline, p.computeCost(handOff), 1e-9);
  }

  /**
   * A hand-off at altitude {@code alt} with the given tangential and radial velocity, dated late
   * enough to take computeCost's nominal path. Position on +X, velocity radial on +X and tangential
   * on +Y, so the flight path angle is exactly {@code atan(vRad/vTan)}.
   */
  private static SpacecraftState handOff(double alt, double vTan, double vRad) {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + alt;
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(vRad, vTan, 0)),
                OrekitService.get().gcrf(),
                AbsoluteDate.J2000_EPOCH.shiftedBy(200.0),
                Constants.WGS84_EARTH_MU))
        .withMass(10_000);
  }

  /**
   * The ranking the apogee-ceiling weight exists to get right (measured 2026-08-05 on the Falcon
   * Heavy 400 km budget-loads profile, same loads and same MECO, differing only in pitch exponent):
   *
   * <pre>
   *   exponent 0.354465 → alt 40 401 m, vTan 7971 m/s, vRad 284.5 m/s → final orbit 400 000×400 110 m
   *   exponent 0.386137 → alt 49 212 m, vTan 7913 m/s, vRad 387.4 m/s → final orbit 251 397×400 128 m
   * </pre>
   *
   * <p>The second hands off 103 m/s more radial velocity, which the transfer's first burn cancels
   * outright — it emptied the upper stage and left the mission 148 km short of its target perigee.
   * It also sits 36 km lower in apogee, and at the historical overshoot weight of 3.0 that bought
   * it a <em>better</em> cost (0.130867 against 0.225404): the objective preferred the candidate
   * that broke the mission. This fixture holds the fix — the cheaper hand-off must be the one the
   * mission survives.
   */
  @Test
  void computeCost_prefersTheHandOffTheMissionSurvives() {
    GravityTurnProblem p = problemWithRealInitialState();
    SpacecraftState survives = handOff(40_401, 7971.0, 284.5);
    SpacecraftState breaksTheMission = handOff(49_212, 7913.0, 387.4);

    double costSurvives = p.computeCost(survives);
    double costBreaks = p.computeCost(breaksTheMission);

    assertTrue(
        costSurvives < costBreaks,
        () ->
            "the hand-off the mission survives must rank cheaper: "
                + costSurvives
                + " vs "
                + costBreaks);
  }

  /**
   * The asymmetry that fix rests on: past the window an apogee error is absorbed by the trim burn,
   * short of it the transfer must make it up in ΔV. The same absolute deviation must therefore cost
   * strictly less above the ceiling than below the target.
   */
  @Test
  void computeCost_apogeeOvershoot_costsLessThanTheSameShortfall() {
    GravityTurnConstraints constraints = GravityTurnConstraints.forTarget(400_000);
    double deviation = 60_000.0;
    GravityTurnProblem p = problemWithRealInitialState();

    // Circular states are the cleanest way to place an apogee exactly: apogee = altitude.
    double overshootCost =
        p.computeCost(circularStateAfter(constraints.maxApogee() + deviation, 200.0));
    double shortfallCost =
        p.computeCost(circularStateAfter(constraints.targetApogee() - deviation, 200.0));

    assertTrue(
        overshootCost < shortfallCost,
        () ->
            "an apogee "
                + deviation
                + " m past the ceiling must cost less than one that far short of the target: "
                + overshootCost
                + " vs "
                + shortfallCost);
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
