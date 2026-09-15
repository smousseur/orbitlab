package com.smousseur.orbitlab.simulation.mission.bench;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator;
import com.smousseur.orbitlab.simulation.mission.operation.EarthOrbitMission;
import com.smousseur.orbitlab.simulation.mission.operation.GEOMission;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * PHY-5 / L0 measurement §5.1 — the current single-object replay cost per profile, the reference
 * the K-debris cost of §5.2 adds to. NOT a gate: changes no {@code src/main}, asserts nothing.
 *
 * <p>Each profile is its own test so one can be run in isolation. Gated on {@code slowTests}
 * because getting a replayable mission needs a full optimize (the user's slow path); the
 * <b>measurement</b> itself is the isolated {@link MissionEphemerisGenerator#generate} call timed
 * afterwards, which is independent of the optimize budget. Run e.g. {@code cleanTest test
 * -Dorbitlab.slowTests=true --tests '*Phy5ReplayCostBench*'}.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class Phy5ReplayCostBench {

  /**
   * Kourou, the flown latitude the LEO budget must be sized at (see LEOMissionOptimizationTest).
   */
  private static final double LAUNCH_LATITUDE_DEG = 5.23;

  private static final long TEST_SEED = 42L;
  private static final int BUDGET = 40_000;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void falconHeavyLeo400() {
    timeReplay(
        "FH LEO-400 (LEGACY)",
        new EarthOrbitMission(
            "FH LEO",
            new LaunchConfiguration(
                Launchers.FALCON_HEAVY,
                new double[] {400_000, 200_000, 100_000},
                Spacecraft.LEGACY),
            400_000));
  }

  @Test
  void ariane64Leo400() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(Launchers.ARIANE_64, payload, 400_000, LAUNCH_LATITUDE_DEG);
    timeReplay(
        "Ariane 64 LEO-400",
        new EarthOrbitMission(
            "A64 LEO", new LaunchConfiguration(Launchers.ARIANE_64, loads, payload), 400_000));
  }

  @Test
  void geo() {
    timeReplay("GEO (default launcher)", new GEOMission("GEO", 400_000, 35_786_000));
  }

  private void timeReplay(String label, Mission mission) {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    SpacecraftState initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);

    MissionComputeResult result = new MissionOptimizer(mission, BUDGET, TEST_SEED).optimize();
    Mission flown = result.mission();

    MissionEphemerisGenerator generator = new MissionEphemerisGenerator();
    long best = Long.MAX_VALUE;
    int points = 0;
    boolean complete = true;
    // Four replays; the first warms JIT/allocation and is discarded, the best of the next three is
    // the reported cost.
    for (int i = 0; i < 4; i++) {
      long t0 = System.nanoTime();
      MissionEphemeris ephemeris = generator.generate(flown, flown.getInitialState(epoch));
      long ms = (System.nanoTime() - t0) / 1_000_000L;
      points = ephemeris.size();
      complete = ephemeris.isComplete();
      if (i > 0) {
        best = Math.min(best, ms);
      }
    }

    System.out.printf(
        Locale.ROOT,
        "%nPHY-5 / L0 §5.1 — REPLAY %-22s : %d ms (best of 3), %d points, complete=%b%n",
        label,
        best,
        points,
        complete);
  }
}
