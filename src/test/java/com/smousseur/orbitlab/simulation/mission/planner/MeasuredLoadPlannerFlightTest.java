package com.smousseur.orbitlab.simulation.mission.planner;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.runtime.AchievedOrbit;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionLoadEvaluator;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * PHY-2 / L4 closure: the measured two-pass sizing, flown (spec {@code
 * docs/atmosphere/11-conception-L4-PHY-2.md} §5).
 *
 * <p>This is a <b>measurement</b> and not a baseline: the lot changes what a mission computed
 * through the planner flies, and the repository's gates size through {@code PropellantBudget} and
 * fly through {@code MissionOptimizer} directly, so none of them sees this path at all (§3.5). The
 * proof therefore lives here rather than in a re-recorded pin — and a gate that moves because of
 * this lot is a signal that the sizing leaked out of its perimeter.
 *
 * <p>Slow by construction: each sizing pass is a full mission optimization, so the run is two or
 * three of them.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class MeasuredLoadPlannerFlightTest {

  /** Kourou — the site the Falcon Heavy LEO-400 profile of {@code DT-19} was measured from. */
  private static final double LATITUDE_DEG = 5.23;

  private static final double LONGITUDE_DEG = -52.77;
  private static final double TARGET_ALTITUDE_M = 400_000.0;
  private static final double PAYLOAD_MASS_KG = 10_000.0;

  /** The ±7 % band the mission optimization tests assert an insertion within. */
  private static final double OBJECTIVE_TOLERANCE_RATIO =
      MissionLoadEvaluator.DEFAULT_OBJECTIVE_TOLERANCE_RATIO;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void measuredSizing_dropsTheTopStageLoad_landsTheResidualInBand_andStillInserts() {
    MissionSpec spec = falconHeavyLeo400();
    double[] budgeted = spec.configuration().propellantLoads();
    int top = budgeted.length - 1;

    MissionPlan plan =
        new MeasuredLoadPlanner(spec, OptimizationType.FAST, launchEpoch(), 40_000, 42L, null)
            .plan();

    // 1 — the load was resolved against a flight, and it went down: the reserve is gone from what
    // the vehicle actually carries.
    PropellantSizing sizing = plan.sizing();
    assertNotNull(sizing, "the measured planner must attach its sizing");
    double[] flown = sizing.applyTo(budgeted);
    assertTrue(
        flown[top] < budgeted[top],
        () ->
            "measured sizing must lighten the top stage: "
                + budgeted[top]
                + " kg budgeted -> "
                + flown[top]
                + " kg flown");

    // 2 — and it landed inside the band rather than merely lower: above the flame-out floor, under
    // the dead-propellant ceiling.
    StagePropellant sized =
        plan.computation()
            .performanceReport()
            .residualForStage(top)
            .orElseThrow(() -> new AssertionError("no per-stage split for the sized stage"));
    double residualRatio = sized.residualRatio();
    assertTrue(
        residualRatio >= MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
        () -> "sized stage flamed out, residual ratio " + residualRatio);
    assertTrue(
        residualRatio <= MeasuredLoadPlanner.RESIDUAL_BAND_UPPER_RATIO,
        () -> "sized stage still carries dead propellant, residual ratio " + residualRatio);

    // 3 — the lighter vehicle still reaches the orbit it was asked for.
    AchievedOrbit achieved = plan.computation().achievedOrbit();
    assertTrue(achieved.hasOsculating(), "the flight must report an achieved orbit");
    OrbitElements orbit = achieved.osculating();
    assertWithinTolerance("perigee", orbit.perigeeAltitude());
    assertWithinTolerance("apogee", orbit.apogeeAltitude());

    // 4 — convergence: DT-19 bets on two passes, the cap allows one correction beyond.
    assertTrue(
        sizing.passes() >= 1 && sizing.passes() <= MeasuredLoadPlanner.MAX_SIZING_PASSES,
        () -> "unexpected pass count " + sizing.passes());
  }

  private static void assertWithinTolerance(String label, double altitude) {
    double deviation = Math.abs(altitude - TARGET_ALTITUDE_M) / TARGET_ALTITUDE_M;
    assertTrue(
        deviation <= OBJECTIVE_TOLERANCE_RATIO,
        () ->
            label
                + " "
                + altitude
                + " m is "
                + Math.round(100.0 * deviation)
                + " % off the "
                + TARGET_ALTITUDE_M
                + " m target");
  }

  private static MissionSpec falconHeavyLeo400() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(PAYLOAD_MASS_KG, 0.0);
    double[] budgeted =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY, payload, TARGET_ALTITUDE_M, LATITUDE_DEG);
    return MissionSpec.EarthOrbit.dueEast(
        "Falcon Heavy LEO 400 (measured sizing)",
        new LaunchConfiguration(Launchers.FALCON_HEAVY, budgeted, payload),
        TARGET_ALTITUDE_M,
        TARGET_ALTITUDE_M,
        "Kourou",
        LATITUDE_DEG,
        LONGITUDE_DEG,
        0.0,
        null);
  }

  private static AbsoluteDate launchEpoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }
}
