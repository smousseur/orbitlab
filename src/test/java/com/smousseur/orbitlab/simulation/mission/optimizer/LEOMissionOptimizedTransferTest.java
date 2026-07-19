package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Mirror of {@link LEOMissionOptimizationTest} flying the CMA-ES-optimized transfer (spec 06 I6,
 * {@link LEOMission#withOptimizedTransfer}) instead of the analytic Hohmann profile. Same launcher
 * configuration (Falcon Heavy fully loaded) and same targets, so any divergence between the two
 * classes isolates the transfer mode. This is the multi-altitude sweep required before deciding
 * whether the optimized transfer becomes the LEO default (bilan 08 §3.2).
 */
public class LEOMissionOptimizedTransferTest extends AbstractTrajectoryOptimizerTest {
  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @ParameterizedTest(name = "targetAltitude={0}m")
  @ValueSource(doubles = {300_000, 600_000, 800_000, 1_000_000})
  void testCircularMissions(double targetAltitude) {
    LEOMission mission =
        LEOMission.withOptimizedTransfer(
            "LEO mission (optimized transfer)", defaultConfiguration(), targetAltitude);
    testMission(mission, targetAltitude, targetAltitude);
  }

  /**
   * Same elliptic targets as the analytic class, pending elliptic support in the optimized
   * transfer: {@code TransfertTwoManeuverStage} resolves burn 2 as a full circularization at
   * apoapsis, so only circular targets exist today. Enabling these requires a perigee-aware burn-2
   * resolution (vis-viva instead of circular speed) threaded through {@code TransfertTwoManeuver}
   * and an elliptic {@code withOptimizedTransfer} overload.
   */
  @Disabled("TransfertTwoManeuverStage only supports circular targets (spec 06 I6)")
  @ParameterizedTest(name = "perigee={0}m, apogee={1}m")
  @CsvSource({"300_000, 600_000", "600_000, 800_000", "200_000, 1_000_000"})
  void testEllipticMissions(double perigeeAltitude, double apogeeAltitude) {
    Assertions.fail("LEOMission.withOptimizedTransfer has no elliptic variant yet");
  }

  /** Same configuration as the historical ctors used by the analytic circular tests. */
  private static LaunchConfiguration defaultConfiguration() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }
}
