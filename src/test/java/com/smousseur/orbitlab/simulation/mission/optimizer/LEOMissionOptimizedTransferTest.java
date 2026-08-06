package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.vehicle.*;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Mirror of {@link LEOMissionOptimizationTest} flying the CMA-ES-optimized transfer (spec 06 I6,
 * {@link LEOMission#circularWithOptimizedTransfer}) instead of the analytic Hohmann profile. Same
 * launcher configuration (Falcon Heavy fully loaded) and same targets, so any divergence between
 * the two classes isolates the transfer mode. This is the multi-altitude sweep required before
 * deciding whether the optimized transfer becomes the LEO default (bilan 08 §3.2).
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
public class LEOMissionOptimizedTransferTest extends AbstractTrajectoryOptimizerTest {

  /**
   * The latitude the missions below actually launch from ({@code EarthMission.DEFAULT_LATITUDE},
   * Kourou). Sizing the budget at any other latitude mis-credits the Earth-rotation assist and
   * loads the difference into the sized top stage as dead propellant — see the same constant in
   * {@link LEOMissionOptimizationTest}.
   */
  private static final double LAUNCH_LATITUDE_DEG = 5.23;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @ParameterizedTest(name = "targetAltitude={0}m")
  @ValueSource(doubles = {600_000})
  void testCircularMissions(double targetAltitude) {
    LEOMission mission =
        LEOMission.circularWithOptimizedTransfer(
            "LEO mission (optimized transfer)", defaultConfiguration(), targetAltitude);
    testMission(mission, targetAltitude, targetAltitude);
  }

  @Test
  void testFalconHeavyOptimizedTransfer() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, payload, 400_000, LAUNCH_LATITUDE_DEG);
    LEOMission mission =
        LEOMission.circularWithOptimizedTransfer(
            "Falcon Heavy (optimized transfer)",
            new LaunchConfiguration(Launchers.FALCON_HEAVY, loads, payload),
            400_000);
    testMission(mission, 400_000, 400_000);
  }

  /**
   * Same elliptic targets as the analytic {@link LEOMissionOptimizationTest#testEllipticMissions},
   * now flown through the CMA-ES-optimized single-burn transfer ({@link
   * LEOMission#ellipticWithOptimizedTransfer}). The single burn shapes the post-gravity-turn state
   * directly onto the target ellipse — {@link
   * com.smousseur.orbitlab.simulation.mission.stage.TransfertManeuverStage} grades apogee, perigee
   * and eccentricity — instead of the deterministic apoapsis circularization {@code
   * TransfertTwoManeuverStage} is limited to. The downstream trim burn is keyed on the target
   * perigee so the achieved shape is the ellipse (target perigee, achieved apogee).
   */
  @ParameterizedTest(name = "perigee={0}m, apogee={1}m")
  @CsvSource({"600_000, 800_000"})
  void testEllipticMissions(double perigeeAltitude, double apogeeAltitude) {
    LEOMission mission =
        LEOMission.ellipticWithOptimizedTransfer(
            "LEO mission (optimized elliptic transfer)",
            defaultConfiguration(),
            perigeeAltitude,
            apogeeAltitude);
    testMission(mission, perigeeAltitude, apogeeAltitude);
  }

  /** Same configuration as the historical ctors used by the analytic circular tests. */
  private static LaunchConfiguration defaultConfiguration() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }
}
