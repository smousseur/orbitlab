package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * <b>PHY-8 / L2 — the budget gate</b> (spec {@code docs/etagement/04-conception-L2.md} §5.2).
 *
 * <p>None of the four pinned baselines goes through {@link PropellantBudget}: three fly
 * hand-written loads and the fourth flies {@code fullyLoaded}. The fold {@code L2} adds to {@code
 * sizeTopStage} would therefore be guarded by nothing, while getting it wrong is not subtle —
 * without it the sized upper-stage load collapses from 1 963 kg to zero on a LEO profile (spec
 * §2.1).
 *
 * <p>Both launchers below are <em>test</em> fixtures: the sequential one reproduces the catalog
 * Falcon Heavy as it stood before {@code L2}, the split one is what {@code L2} writes. Comparing
 * them in one run means this test survives {@code L3} and {@code L4} without being touched, where
 * pinned literals would rot.
 */
class PropellantBudgetParallelBlockTest {

  private static final double LAT = 5.23;
  private static final double AZIMUTH = Physics.getLaunchAzimuth();
  private static final double LEO = 400_000.0;
  private static final double PARKING = 200_000.0;
  private static final double GEO = 35_786_000.0;

  @Test
  void leoLoadsAreUnmoved() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000.0, 0.0);

    assertSameLoads(
        PropellantBudget.loadsForLeo(SEQUENTIAL, payload, LEO, LAT),
        PropellantBudget.loadsForLeo(SPLIT, payload, LEO, LAT));
  }

  @Test
  void leoLoadsAtAnAzimuthAreUnmoved() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(5_000.0, 0.0);

    assertSameLoads(
        PropellantBudget.loadsForLeo(SEQUENTIAL, payload, LEO, LAT, AZIMUTH),
        PropellantBudget.loadsForLeo(SPLIT, payload, LEO, LAT, AZIMUTH));
  }

  @Test
  void geoLoadsAreUnmoved() {
    PropellantBudget.SizedLoads sequential =
        PropellantBudget.loadsForGeo(SEQUENTIAL, Payloads.GEO_SAT, 2_000.0, PARKING, LAT);
    PropellantBudget.SizedLoads split =
        PropellantBudget.loadsForGeo(SPLIT, Payloads.GEO_SAT, 2_000.0, PARKING, LAT);

    assertSameLoads(sequential.launcherLoads(), split.launcherLoads());
    assertEquals(sequential.payloadLoad(), split.payloadLoad(), 0.0);
  }

  @Test
  void highOrbitLoadsAreUnmoved() {
    PropellantBudget.SizedLoads sequential =
        PropellantBudget.loadsForHighOrbit(
            SEQUENTIAL, Payloads.GEO_SAT, 2_000.0, PARKING, GEO, LAT, LAT, AZIMUTH);
    PropellantBudget.SizedLoads split =
        PropellantBudget.loadsForHighOrbit(
            SPLIT, Payloads.GEO_SAT, 2_000.0, PARKING, GEO, LAT, LAT, AZIMUTH);

    assertSameLoads(sequential.launcherLoads(), split.launcherLoads());
    assertEquals(sequential.payloadLoad(), split.payloadLoad(), 0.0);
  }

  @Test
  void lunarLoadsAreUnmoved() {
    Spacecraft probe = Payloads.LUNAR_PROBE.toSpacecraft(2_000.0, 0.0);
    PropellantBudget.LunarLoads sequential =
        PropellantBudget.loadsForLunar(SEQUENTIAL, probe, LEO, LAT, AZIMUTH);
    PropellantBudget.LunarLoads split =
        PropellantBudget.loadsForLunar(SPLIT, probe, LEO, LAT, AZIMUTH);

    assertSameLoads(sequential.launcherLoads(), split.launcherLoads());
    assertEquals(sequential.massAtInjection(), split.massAtInjection(), 0.0);
  }

  @Test
  void lunarOrbitLoadsAreUnmoved() {
    PropellantBudget.LunarOrbitLoads sequential =
        PropellantBudget.loadsForLunarOrbit(
            SEQUENTIAL, Payloads.LUNAR_ORBITER, 2_000.0, LEO, 100_000.0, LAT, AZIMUTH);
    PropellantBudget.LunarOrbitLoads split =
        PropellantBudget.loadsForLunarOrbit(
            SPLIT, Payloads.LUNAR_ORBITER, 2_000.0, LEO, 100_000.0, LAT, AZIMUTH);

    assertSameLoads(sequential.launcherLoads(), split.launcherLoads());
    assertEquals(sequential.massAtInjection(), split.massAtInjection(), 0.0);
    assertEquals(sequential.insertionLoad(), split.insertionLoad(), 0.0);
  }

  @Test
  void withoutTheFoldTheUpperStageWouldBeStarved() {
    // The failure this gate exists for, stated as a number rather than as a warning: the serial
    // Tsiolkovsky chain credits the split stack with ΔV no staging produces, and the top stage is
    // sized on what is left.
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000.0, 0.0);
    double[] loads = PropellantBudget.loadsForLeo(SPLIT, payload, LEO, LAT);

    assertTrue(loads[2] > 1_000, () -> "the upper stage flies empty: " + loads[2] + " kg");
  }

  /**
   * The split loads must sum, block entry by block entry, to the sequential ones: the two bottom
   * entries against the single first stage, then one to one.
   */
  private static void assertSameLoads(double[] sequential, double[] split) {
    assertEquals(2, sequential.length);
    assertEquals(3, split.length);
    assertEquals(sequential[0], split[0] + split[1], 0.0, "the block carries the same propellant");
    assertEquals(sequential[1], split[2], 0.0, "the sized upper stage is unmoved");
  }

  // ── Fixtures: the Falcon Heavy before and after L2 ────────────────────────

  private static final StageModel UPPER =
      new StageModel(
          "S2 (Merlin Vacuum)",
          4_000,
          107_500,
          new PropulsionSystem(348, 981_000),
          new StageCapabilities(
              IgnitionMode.AIRSTART,
              2,
              ShutdownMode.COMMANDED,
              PropellantType.CRYOGENIC,
              7_200.0,
              StageRole.UPPER),
          new AerodynamicProperties(10.5, 2.2));

  private static final LauncherModel SEQUENTIAL =
      new LauncherModel(
          "SEQUENTIAL",
          "Falcon Heavy (aggregated first stage)",
          List.of(
              new StageModel(
                  "S1 (3 cores aggregated)",
                  66_000,
                  1_233_000,
                  new PropulsionSystem(296, 22_800_000),
                  groundLit(StageRole.CORE),
                  new AerodynamicProperties(31.6, 0.4)),
              UPPER),
          new AscentProfile(7.0, 3.0, 2.0));

  private static final LauncherModel SPLIT =
      new LauncherModel(
          "SPLIT",
          "Falcon Heavy (boosters split out)",
          List.of(
              new StageModel(
                  "Boosters (2 side cores)",
                  22_000,
                  411_000,
                  new PropulsionSystem(296, 7_600_000),
                  groundLit(StageRole.BOOSTER),
                  new AerodynamicProperties(10.5, 0.4),
                  2),
              new StageModel(
                  "Core",
                  22_000,
                  411_000,
                  new PropulsionSystem(296, 7_600_000),
                  groundLit(StageRole.CORE),
                  new AerodynamicProperties(10.5, 0.4)),
              UPPER),
          new AscentProfile(7.0, 3.0, 2.0));

  private static StageCapabilities groundLit(StageRole role) {
    return new StageCapabilities(
        IgnitionMode.GROUND, 0, ShutdownMode.COMMANDED, PropellantType.CRYOGENIC, 0.0, role);
  }
}
