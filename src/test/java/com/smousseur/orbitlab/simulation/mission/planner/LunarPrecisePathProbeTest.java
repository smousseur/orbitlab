package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * MIS-4 / L4 §8.4 — the throwaway probe of the lot: <b>what does a {@code PRECISE} lunar mission
 * cost in wall time?</b>
 *
 * <p>§5 opened that path — {@code MissionLoadEvaluator} now routes any objective kind, so a lunar
 * spec on {@code PRECISE} runs the propellant-sizing sweep instead of throwing on the optimizer
 * thread after a full flight had already been paid for. What it costs was written down as unknown
 * rather than discovered: a sweep is up to ten evaluations per λ-scaled stage, coordinated between
 * stages, and <b>each one is a complete flight</b> — a CMA-ES ascent, two four-second aim solves,
 * and seven days of propagation. This measures it once.
 *
 * <p><b>Measured on 2026-08-27, and it does not reopen §5: 189.7 s — 3.2 minutes.</b> Nine
 * evaluations over two passes, some 21 s each, which is far less than a full flight because the
 * sweep runs at {@code DEFAULT_SIZING_MAX_EVALUATIONS} rather than the ascent's full budget and the
 * turn stops early. λ* = {@code [1.0000, 0.5844]}: the sweep hands back <b>44 680 kg</b> of upper
 * stage propellant, 41.6 % of its capacity.
 *
 * <p><b>What binds at the margin is the flame-out floor, not the Moon.</b> The last refused
 * evaluation reported {@code objectiveMet=true} with a 0.869 % residual against the 1 % floor — the
 * flyby was still reached at a load the floor would not allow. Worth knowing in L5, where {@code
 * PropellantBudget} gets its translunar case: what sizes this chain is the residual the upper stage
 * must keep, not the ΔV the transfer needs.
 *
 * <p><b>It cannot be a test, and it is not one.</b> It costs a whole sweep and it asserts nothing.
 * The pattern and the {@code orbitlab.probe} gate are {@code LunarBaselineProbeTest}'s, and like it
 * this class is meant to be deleted when the chantier closes — not when this lot does.
 *
 * <p>Run with {@code -Dorbitlab.probe=true}.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class LunarPrecisePathProbeTest {
  private static final Logger logger = LogManager.getLogger(LunarPrecisePathProbeTest.class);

  private static final double CANAVERAL_LATITUDE = 28.562;
  private static final double CANAVERAL_LONGITUDE = -80.577;
  private static final double CANAVERAL_ALTITUDE = 3.0;

  private static final double PARKING_ALTITUDE = 400_000.0;
  private static final double PERILUNE_ALTITUDE = 100_000.0;

  @BeforeAll
  static void init() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("How long a PRECISE lunar mission takes end to end")
  void precisePathWallTime() {
    MissionSpec.Lunar spec =
        new MissionSpec.Lunar(
            "Lunar flyby (PRECISE probe)",
            LaunchConfiguration.fullyLoaded(
                Launchers.FALCON_HEAVY, Payloads.CARGO_MODULE.toSpacecraft(1_000.0, 0.0)),
            PARKING_ALTITUDE,
            PERILUNE_ALTITUDE,
            "Cape Canaveral",
            CANAVERAL_LATITUDE,
            CANAVERAL_LONGITUDE,
            CANAVERAL_ALTITUDE,
            null,
            null);

    MissionEntry entry = new MissionEntry(spec);
    entry.setOptimizationType(OptimizationType.PRECISE);
    AbsoluteDate epoch = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());

    long startedAt = System.nanoTime();
    MissionPlan plan = new MissionPlanOptimizer(entry, epoch).compute();
    double wallSeconds = (System.nanoTime() - startedAt) / 1.0e9;

    logger.info(
        "PRECISE lunar path: {} s of wall time ({} min)",
        String.format(Locale.ROOT, "%.1f", wallSeconds),
        String.format(Locale.ROOT, "%.1f", wallSeconds / 60.0));
    logger.info(
        "Sizing outcome: {}",
        plan.sizing() != null ? plan.sizing() : "no propellant sizing reported");
  }
}
