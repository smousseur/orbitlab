package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class LEOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {

  /**
   * The latitude the missions below actually launch from ({@code EarthMission.DEFAULT_LATITUDE},
   * Kourou). The propellant budget must be sized at the flown latitude: it credits the ascent with
   * {@code 465·cos(latitude)} of Earth-rotation assist, so sizing at 45.96° — as this test did
   * until 2026-08-05 — withheld 140 m/s the flight gets for free and loaded the difference into the
   * sized top stage as dead propellant (S2 load 2 844 → 1 963 kg, residual 36.7 % → 13.1 %, same
   * final orbit).
   */
  private static final double LAUNCH_LATITUDE_DEG = 5.23;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @ParameterizedTest(name = "targetAltitude={0}m")
  @ValueSource(doubles = {300_000, 600_000, 800_000, 1_000_000})
  void testCircularMissions(double targetAltitude) {
    LEOMission mission = new LEOMission("LEO mission", targetAltitude);
    testMission(mission, targetAltitude, targetAltitude);
  }

  @ParameterizedTest(name = "perigee={0}m, apogee={1}m")
  @CsvSource({"300_000, 600_000", "600_000, 800_000", "200_000, 1_000_000"})
  void testEllipticMissions(double perigeeAltitude, double apogeeAltitude) {
    LEOMission mission = new LEOMission("LEO mission", perigeeAltitude, apogeeAltitude);
    testMission(mission, perigeeAltitude, apogeeAltitude);
  }

  @Test
  void testFalconHeavy() {
    LEOMission mission =
        new LEOMission(
            "Falcon Heavy",
            new LaunchConfiguration(
                Launchers.FALCON_HEAVY, new double[] {600_000, 100_000}, Spacecraft.LEGACY),
            400_000);
    testMission(mission, 400_000, 400_000);
  }

  /**
   * Spec 06 I3 integration criterion: a LEO 400 km mission flying the analytic budget loads
   * converges, and the propellant left in the sized S2 stays under 15 % of that stage's load. Read
   * from the per-stage split (bilan 10 §6) rather than the stack-wide total, so the assertion
   * measures S2 alone.
   */
  @Test
  void testFalconHeavyBudgetLoads() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, payload, 400_000, LAUNCH_LATITUDE_DEG);
    LEOMission mission =
        new LEOMission(
            "Falcon Heavy (budget loads)",
            new LaunchConfiguration(Launchers.FALCON_HEAVY, loads, payload),
            400_000);

    MissionComputeResult result = testMission(mission, 400_000, 400_000);

    StagePropellant s2 =
        result
            .performanceReport()
            .residualForStage(1)
            .orElseThrow(() -> new AssertionError("no per-stage propellant split for S2"));
    Assertions.assertEquals(loads[1], s2.loaded(), 1.0, "S2 reported load differs from the budget");
    Assertions.assertTrue(
        s2.residualRatio() < 0.15,
        () ->
            String.format(
                "S2 residual %.0f kg exceeds 15%% of its sized load %.0f kg",
                s2.residual(), s2.loaded()));
  }
}
