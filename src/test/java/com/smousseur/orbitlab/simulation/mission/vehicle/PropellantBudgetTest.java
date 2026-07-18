package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropellantBudgetTest {

  private static final double S2_CAPACITY = 107_500;

  // --- Analytic ΔV formulas (known cases, spec 06 I3) ---

  @Test
  void gtoInjection_from200km_matchesTextbookValue() {
    double dv = PropellantBudget.gtoInjectionDeltaV(200_000);
    assertTrue(dv > 2_400 && dv < 2_500, () -> "Expected ~2 440-2 460 m/s, got " + dv);
  }

  @Test
  void apogeeCircularization_equatorial_isPureCircularization() {
    double dv = PropellantBudget.apogeeCircularizationDeltaV(400_000, 0.0);
    assertTrue(dv > 1_400 && dv < 1_500, () -> "Expected ~1 456 m/s, got " + dv);
  }

  @Test
  void apogeeCircularization_growsWithLatitude() {
    double equatorial = PropellantBudget.apogeeCircularizationDeltaV(400_000, 0.0);
    double kourou = PropellantBudget.apogeeCircularizationDeltaV(400_000, 5.23);
    assertTrue(kourou > equatorial);
  }

  @Test
  void ascentDeltaV_knownValue_andLatitudePenalty() {
    // √(μ/r) at 400 km (7 668.6) + calibrated losses (1 260) − equatorial rotation (465).
    double equatorial = PropellantBudget.ascentDeltaV(400_000, 0.0);
    assertTrue(
        equatorial > 8_440 && equatorial < 8_490, () -> "Expected ~8 464 m/s, got " + equatorial);
    assertTrue(PropellantBudget.ascentDeltaV(400_000, 45.96) > equatorial);
  }

  // --- Load sizing (spec 06 I3 exit criteria) ---

  @Test
  void loadsForLeo_lowerStageFull_topStageSizedUnderHalfCapacity() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, payload, 400_000, 45.96);

    assertEquals(2, loads.length);
    assertEquals(1_233_000, loads[0], 1e-6, "S1 flies full in v1");
    assertTrue(loads[1] > 0, "S2 needs some propellant");
    assertTrue(
        loads[1] < 0.5 * S2_CAPACITY,
        () -> "LEO 400 km must size S2 under half capacity, got " + loads[1]);
  }

  @Test
  void loadsForGeo_sizedAkm_andMuchMoreS2ThanLeo() {
    Spacecraft leoPayload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
    double[] leoLoads =
        PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, leoPayload, 400_000, 5.23);

    PropellantBudget.GeoLoads geoLoads =
        PropellantBudget.loadsForGeo(Launchers.FALCON_HEAVY, Payloads.GEO_SAT, 2_000, 400_000, 5.23);

    assertEquals(1_233_000, geoLoads.launcherLoads()[0], 1e-6, "S1 flies full in v1");
    assertTrue(
        geoLoads.akmLoad() > 1_000 && geoLoads.akmLoad() <= 2_000,
        () -> "AKM sized for ~1 500 m/s apogee dV expected in (1000, 2000] kg, got "
            + geoLoads.akmLoad());
    assertTrue(
        geoLoads.launcherLoads()[1] > 3 * leoLoads[1],
        () ->
            String.format(
                "GEO S2 load (%.0f) must dwarf LEO S2 load (%.0f)",
                geoLoads.launcherLoads()[1], leoLoads[1]));
  }

  @Test
  void loadsForGeo_inertPayload_zeroAkm() {
    PropellantBudget.GeoLoads geoLoads =
        PropellantBudget.loadsForGeo(
            Launchers.FALCON_HEAVY, Payloads.CARGO_MODULE, 15_000, 400_000, 5.23);
    assertEquals(0.0, geoLoads.akmLoad(), 1e-9);
  }

  @Test
  void solidTopStage_fliesFull() {
    LauncherModel solidTop =
        new LauncherModel(
            "SOLID_TOP",
            "Solid top",
            List.of(
                liquidStage("S1", 10_000, 200_000, 300, 3_000_000),
                new StageModel(
                    "S2 solid",
                    2_000,
                    30_000,
                    new PropulsionSystem(280, 500_000),
                    new StageCapabilities(
                        IgnitionMode.AIRSTART,
                        0,
                        ShutdownMode.BURN_TO_DEPLETION,
                        PropellantType.SOLID,
                        0.0,
                        StageRole.UPPER))),
            new AscentProfile(7, 3, 2));
    double[] loads =
        PropellantBudget.loadsForLeo(solidTop, Spacecraft.LEGACY, 400_000, 0.0);
    assertEquals(30_000, loads[1], 1e-6, "solid stages have no sizing degree of freedom");
  }

  @Test
  void undersizedTank_clampedToCapacity() {
    LauncherModel tinyUpper =
        new LauncherModel(
            "TINY_UPPER",
            "Tiny upper",
            List.of(
                liquidStage("S1", 10_000, 100_000, 300, 3_000_000),
                liquidStage("S2", 1_000, 1_000, 348, 100_000)),
            new AscentProfile(7, 3, 2));
    double[] loads =
        PropellantBudget.loadsForLeo(
            tinyUpper, Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0), 400_000, 0.0);
    assertEquals(1_000, loads[1], 1e-6, "required load beyond capacity is clamped");
  }

  private static StageModel liquidStage(
      String name, double dryMass, double capacity, double isp, double thrust) {
    return new StageModel(
        name,
        dryMass,
        capacity,
        new PropulsionSystem(isp, thrust),
        new StageCapabilities(
            IgnitionMode.GROUND,
            0,
            ShutdownMode.COMMANDED,
            PropellantType.CRYOGENIC,
            0.0,
            StageRole.CORE));
  }
}
