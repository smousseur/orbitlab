package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.operation.GEOMission;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import java.util.Arrays;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * I7 outer-loop integration tests (spec 09 §6 task 4, extended to GEO per bilan 10 §7.3). Runs the
 * full propellant-sizing bisection on real Falcon Heavy missions and asserts the exit criterion:
 * the loads found at {@code λ*} weigh strictly less than the heuristic loads, and the mission at
 * {@code λ*} is feasible. On GEO the payload's AKM is sized separately by {@link
 * PropellantBudget#loadsForGeo} and never appears in the launcher loads.
 *
 * <p>Each profile is run twice, under two scaling masks: the single-λ tests scale only the sized top
 * stage (S2, {@link PropellantLoadOptimizer#lambdaScaledMask}), while the {@code *MultiStage} tests
 * put every variable-load stage under its own λ ({@link PropellantLoadOptimizer#allVariableLoadMask})
 * and let {@link MultiStageLoadOptimizer} sweep the coordinates.
 *
 * <p>The two scenarios differ structurally in where the sized stage's residual ends up: on LEO, S2
 * is the final active stage and its residual is read at mission end; on GEO, S2 is jettisoned after
 * the GTO injection with its residual aboard, which exercises the per-stage split and the jettison
 * capture (bilan 10 §6) — the stack-wide total would only see the AKM.
 *
 * <p><b>Slow / nightly.</b> Each bisection evaluation is a complete mission optimization (~1.5 min),
 * so each loop is ~15 min. They are opt-in: enable with {@code -Dorbitlab.slowTests=true}. The
 * per-λ progress, the resolved {@code λ*} and the per-stage load comparison are logged at INFO by
 * {@link PropellantLoadOptimizer} and {@link MissionLoadEvaluator}.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
public class PropellantLoadOptimizerIntegrationTest {
  private static final Logger logger =
      LogManager.getLogger(PropellantLoadOptimizerIntegrationTest.class);

  /** Same launch latitude the optimized-transfer LEO mission flies from (its default site). */
  private static final double LAUNCH_LATITUDE_DEG = 45.96;

  private static final double TARGET_ALTITUDE_M = 400_000.0;

  /**
   * Single-λ reference on the same LEO configuration (bilan 11 §1), the number the multi-stage run
   * is read against. Logged, not asserted: the multi-stage sweep is answering an open question, and
   * pinning it to a past figure would turn a measurement into a regression bar.
   */
  private static final double SINGLE_LAMBDA_LEO_REFERENCE = 0.4313;

  /**
   * Circularity bar on the LEO final orbit. Asserted on {@code e} rather than on min/max coast
   * altitude (bilan 11 §3.3): geodetic altitudes mix insertion quality with the Earth's oblateness —
   * at i = 45.9° the flattening alone spreads min and max by 11 km on a perfectly circular orbit.
   * This bar is looser than what the ±7 % feasibility band already permits (e ≈ 4.1e-3 at 400 km),
   * so it corroborates the shape without tightening the criterion the loop optimizes against.
   */
  private static final double LEO_ECCENTRICITY_TOLERANCE = 5e-3;

  // ── GEO scenario ──
  /** GEO mission default site (Kourou), mirrors GEOMission's private defaults. */
  private static final double GEO_LAUNCH_LATITUDE_DEG = 5.23;

  private static final double GEO_LAUNCH_LONGITUDE_DEG = -52.77;
  private static final double GEO_PARKING_ALTITUDE_M = 400_000.0;
  private static final double GEO_ALTITUDE_M = 35_786_000.0;

  /**
   * Feasibility band on the final orbit: the ±50 km bar {@code GEOMissionOptimizationTest} asserts,
   * expressed as the ratio {@code objectiveMet} consumes. The ±7 % LEO default would accept an
   * orbit thousands of km off GEO.
   */
  private static final double GEO_ALTITUDE_TOLERANCE_M = 50_000.0;

  private static final double GEO_INCLINATION_TOLERANCE_RAD = FastMath.toRadians(0.05);

  /**
   * Realistic heavy payload (10 t) — unlike the 150 kg legacy sat, this makes the sized top stage
   * (S2) do real work and carry a reclaimable margin. {@link PropellantBudget} sizes S2 well under
   * half its capacity for this mass, so there is genuine propellant for the loop to shave.
   */
  private static final double PAYLOAD_MASS_KG = 10_000.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void leo400km_shrinksHeuristicLoads_andStaysFeasible() {
    LauncherModel launcher = Launchers.FALCON_HEAVY;
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(PAYLOAD_MASS_KG, 0.0);

    // Heuristic loads (10 % margin) — the λ = 1 baseline the loop must beat.
    double[] heuristicLoads =
        PropellantBudget.loadsForLeo(launcher, payload, TARGET_ALTITUDE_M, LAUNCH_LATITUDE_DEG);
    boolean[] mask = PropellantLoadOptimizer.lambdaScaledMask(launcher);
    logStageLoads("Heuristic loads", launcher, heuristicLoads, mask);

    AbsoluteDate launchEpoch =
        new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());

    // Each evaluation rebuilds the LEO mission (optimized transfer, spec 06 I6 — the inner loop I7
    // runs per spec 09 §2) with the scaled loads and optimizes it end to end.
    Function<double[], Mission> missionBuilder =
        loads ->
            LEOMission.withOptimizedTransfer(
                "I7 LEO 400 km",
                new LaunchConfiguration(launcher, loads, payload),
                TARGET_ALTITUDE_M);

    MissionLoadEvaluator evaluator =
        new MissionLoadEvaluator(missionBuilder, heuristicLoads, mask, launchEpoch);

    PropellantLoadOptimizer.Result result = new PropellantLoadOptimizer().minimize(evaluator);

    double[] scaledLoads =
        PropellantLoadOptimizer.scaledLoads(result.lambda(), heuristicLoads, mask);
    logStageLoads("Loads at λ*=" + result.lambda(), launcher, scaledLoads, mask);

    double sumHeuristic = Arrays.stream(heuristicLoads).sum();
    double sumScaled = Arrays.stream(scaledLoads).sum();
    double residualRatio =
        result.best().result() != null
            ? result.best().result().performanceReport().residualRatio()
            : Double.NaN;
    logger.info(
        "I7 result: feasible={}, λ*={}, evals={}, Σload heuristic={} kg → λ*={} kg (−{} kg,"
            + " −{}%), residual ratio at λ*={}",
        result.feasible(),
        result.lambda(),
        result.evaluations(),
        Math.round(sumHeuristic),
        Math.round(sumScaled),
        Math.round(sumHeuristic - sumScaled),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * (1.0 - sumScaled / sumHeuristic)),
        residualRatio);

    assertTrue(
        result.feasible(),
        "Heuristic loads must themselves be feasible for the loop to have anything to shrink");
    assertTrue(
        result.lambda() <= PropellantLoadOptimizer.DEFAULT_LAMBDA_MAX,
        () -> "λ* must not exceed the heuristic (upper) bound, got " + result.lambda());
    // Exit criterion (spec 09 §6): the found loads weigh strictly less than the heuristic loads.
    assertTrue(
        sumScaled < sumHeuristic,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "λ* loads (%.0f kg) must be below the heuristic loads (%.0f kg); λ*=%.4f",
                sumScaled,
                sumHeuristic,
                result.lambda()));
  }

  /**
   * GEO counterpart (bilan 10 §7.3): the sized S2 does the GTO injection and is jettisoned with its
   * residual aboard, the AKM (off λ, sized by the budget) circularizes at apogee. Feasibility is
   * measured against the flown final orbit — circular GEO — not the mission's recorded {@code
   * (parking, GEO)} objective, and at the ±50 km bar of the GEO optimization test.
   */
  @Test
  void geo_shrinksHeuristicLoads_andStaysFeasible() {
    LauncherModel launcher = Launchers.FALCON_HEAVY;
    double payloadDryMass = Payloads.GEO_SAT.defaultDryMass();

    // Heuristic loads (10 % margin) — the λ = 1 baseline the loop must beat. The AKM load is
    // sized once here and stays fixed across evaluations: λ only scales the launcher's S2.
    PropellantBudget.GeoLoads geoLoads =
        PropellantBudget.loadsForGeo(
            launcher,
            Payloads.GEO_SAT,
            payloadDryMass,
            GEO_PARKING_ALTITUDE_M,
            GEO_LAUNCH_LATITUDE_DEG);
    double[] heuristicLoads = geoLoads.launcherLoads();
    double akmLoad = geoLoads.akmLoad();
    boolean[] mask = PropellantLoadOptimizer.lambdaScaledMask(launcher);
    logStageLoads("GEO heuristic loads", launcher, heuristicLoads, mask);
    logger.info("GEO heuristic AKM load: {} kg (off λ)", Math.round(akmLoad));

    AbsoluteDate launchEpoch =
        new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());

    Function<double[], Mission> missionBuilder =
        loads ->
            new GEOMission(
                "I7 GEO",
                new LaunchConfiguration(
                    launcher, loads, Payloads.GEO_SAT.toSpacecraft(payloadDryMass, akmLoad)),
                GEO_PARKING_ALTITUDE_M,
                GEO_ALTITUDE_M,
                GEO_LAUNCH_LATITUDE_DEG,
                GEO_LAUNCH_LONGITUDE_DEG,
                0.0,
                0.0);

    MissionLoadEvaluator evaluator =
        new MissionLoadEvaluator(
            missionBuilder,
            heuristicLoads,
            mask,
            launchEpoch,
            MissionLoadEvaluator.DEFAULT_OPTIMIZER_MAX_EVALUATIONS,
            42L,
            GEO_ALTITUDE_TOLERANCE_M / GEO_ALTITUDE_M,
            MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
            OrbitInsertionObjective.circular(SolarSystemBody.EARTH, GEO_ALTITUDE_M, 0.0));

    PropellantLoadOptimizer.Result result = new PropellantLoadOptimizer().minimize(evaluator);

    double[] scaledLoads =
        PropellantLoadOptimizer.scaledLoads(result.lambda(), heuristicLoads, mask);
    logStageLoads("GEO loads at λ*=" + result.lambda(), launcher, scaledLoads, mask);

    double sumHeuristic = Arrays.stream(heuristicLoads).sum();
    double sumScaled = Arrays.stream(scaledLoads).sum();
    logger.info(
        "I7 GEO result: feasible={}, λ*={}, evals={}, Σload heuristic={} kg → λ*={} kg (−{} kg,"
            + " −{}%)",
        result.feasible(),
        result.lambda(),
        result.evaluations(),
        Math.round(sumHeuristic),
        Math.round(sumScaled),
        Math.round(sumHeuristic - sumScaled),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * (1.0 - sumScaled / sumHeuristic)));

    assertTrue(
        result.feasible(),
        "Heuristic GEO loads must themselves be feasible for the loop to have anything to shrink");
    assertTrue(
        result.lambda() <= PropellantLoadOptimizer.DEFAULT_LAMBDA_MAX,
        () -> "λ* must not exceed the heuristic (upper) bound, got " + result.lambda());
    assertTrue(
        sumScaled < sumHeuristic,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "λ* loads (%.0f kg) must be below the heuristic loads (%.0f kg); λ*=%.4f",
                sumScaled,
                sumHeuristic,
                result.lambda()));

    MissionComputeResult best = result.best().result();
    assertNotNull(best, "The feasible best evaluation must carry its mission compute result");

    // The GEO-specific mechanism under test (bilan 10 §6): S2 is jettisoned after the GTO
    // injection, so its residual only exists in the per-stage split captured at separation — and
    // it must clear the floor of its own load, not hide behind the AKM's.
    StagePropellant s2 =
        best.performanceReport()
            .residualForStage(1)
            .orElseThrow(() -> new AssertionError("no per-stage propellant split for S2"));
    logger.info(
        "GEO S2 at λ*: loaded={} kg, residual at jettison={} kg ({}% of its load)",
        Math.round(s2.loaded()),
        Math.round(s2.residual()),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * s2.residualRatio()));
    assertTrue(
        s2.residualRatio() >= MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "S2 residual at jettison (%.0f kg) is below %.0f%% of its %.0f kg load",
                s2.residual(),
                100.0 * MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
                s2.loaded()));

    // The retained solution must still meet the full GEO quality bar on the plane, which the
    // altitude-only feasibility predicate does not check (the AKM and the node trim own it).
    MissionEphemerisPoint last = best.ephemeris().lastPoint();
    KeplerianOrbit finalOrbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            OrekitService.get().gcrf(),
            last.time(),
            Constants.WGS84_EARTH_MU);
    assertTrue(
        finalOrbit.getI() < GEO_INCLINATION_TOLERANCE_RAD,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "Final inclination %.4f° exceeds tolerance %.3f°",
                FastMath.toDegrees(finalOrbit.getI()),
                FastMath.toDegrees(GEO_INCLINATION_TOLERANCE_RAD)));
  }

  /**
   * Multi-stage counterpart on LEO (bilan 11 §3.1): <b>every</b> variable-load stage gets its own λ
   * and {@link MultiStageLoadOptimizer} minimizes them by coordinate-wise bisection, same setup as
   * {@link #geoMultiStage_shrinksEveryVariableLoadStage()} on the LEO optimized-transfer profile.
   *
   * <p><b>The question it settles.</b> {@link PropellantLoadOptimizer#lambdaScaledMask} records that
   * S1 has no reclaimable slack on LEO, so putting it under λ pins λ* at 1. That finding predates
   * the staging fix (bilan 11 §2.1): before it, a {@code transitionTime} below {@code burn1Duration}
   * stopped the propagation short of the jettison detector, so S1 was never dropped and every
   * reduced-S1 run was measured on a mission that did not fly the profile. GEO then demonstrated the
   * opposite of the same intuition — λ₀ = 0.9453, 67 t reclaimed. This test re-measures LEO on
   * corrected data.
   *
   * <p><b>Measured answer: {@code λ* = [1.0000, 0.4312]}</b> — S1 stays pinned, and λ(S2) reproduces
   * the single-λ reference ({@code 0.4313}) to four decimals, which also cross-validates the
   * multi-stage sweep against the scalar bisection. The mask's finding holds on LEO, on sound data
   * this time, and the mechanism is a load transfer rather than a broken ascent: taking 1.1 % off S1
   * alone, S2 held at its optimum, drops S2's residual from 127 kg to zero. So this stays a
   * <em>regression guard on a settled question</em> — if λ₀ ever leaves 1 here, either the profile or
   * the staging changed.
   */
  @Test
  void leoMultiStage_shrinksEveryVariableLoadStage() {
    LauncherModel launcher = Launchers.FALCON_HEAVY;
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(PAYLOAD_MASS_KG, 0.0);

    double[] heuristicLoads =
        PropellantBudget.loadsForLeo(launcher, payload, TARGET_ALTITUDE_M, LAUNCH_LATITUDE_DEG);
    boolean[] mask = PropellantLoadOptimizer.allVariableLoadMask(launcher);
    logStageLoads("LEO multi-stage heuristic loads", launcher, heuristicLoads, mask);

    AbsoluteDate launchEpoch =
        new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());

    Function<double[], Mission> missionBuilder =
        loads ->
            LEOMission.withOptimizedTransfer(
                "I7 LEO 400 km multi-stage",
                new LaunchConfiguration(launcher, loads, payload),
                TARGET_ALTITUDE_M);

    // The LEO mission's own recorded objective is the flown final orbit (circular 400 km), so no
    // explicit feasibility objective is needed — unlike GEO, whose objective is (parking, GEO).
    MissionLoadEvaluator evaluator =
        new MissionLoadEvaluator(missionBuilder, heuristicLoads, mask, launchEpoch);

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator::evaluate, mask, heuristicLoads);

    double[] scaledLoads =
        PropellantLoadOptimizer.scaledLoads(result.lambdas(), heuristicLoads, mask);
    logStageLoads("LEO multi-stage loads at λ*", launcher, scaledLoads, mask);

    double scaledMassHeuristic = maskedSum(heuristicLoads, mask);
    double scaledMassFinal = maskedSum(scaledLoads, mask);
    logger.info(
        "I7 LEO multi-stage result: feasible={}, λ*={}, evals={}, passes={};"
            + " scaled-stage mass {} kg → {} kg (−{} kg, −{}%)",
        result.feasible(),
        Arrays.toString(result.lambdas()),
        result.evaluations(),
        result.passes(),
        Math.round(scaledMassHeuristic),
        Math.round(scaledMassFinal),
        Math.round(scaledMassHeuristic - scaledMassFinal),
        String.format(
            java.util.Locale.ROOT, "%.1f", 100.0 * (1.0 - scaledMassFinal / scaledMassHeuristic)));

    // The open question of bilan 11 §3.1, answered in the log rather than asserted: whether the
    // first stage moves off the heuristic load once staging actually happens.
    int bottomStage = 0;
    logger.info(
        "LEO multi-stage vs single-λ reference: λ(S1)={} (mask comment predicts 1.0),"
            + " λ(S2)={} vs reference λ*={}",
        String.format(java.util.Locale.ROOT, "%.4f", result.lambdas()[bottomStage]),
        String.format(
            java.util.Locale.ROOT, "%.4f", result.lambdas()[result.lambdas().length - 1]),
        SINGLE_LAMBDA_LEO_REFERENCE);

    assertTrue(result.feasible(), "the heuristic LEO loads must themselves be feasible");
    assertTrue(
        scaledMassFinal < scaledMassHeuristic,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "scaled-stage mass %.0f kg must fall below the heuristic %.0f kg",
                scaledMassFinal,
                scaledMassHeuristic));
    for (int i = 0; i < mask.length; i++) {
      int index = i;
      assertTrue(
          result.lambdas()[index] <= PropellantLoadOptimizer.DEFAULT_LAMBDA_MAX + 1e-12,
          () -> "λ" + index + " must not exceed the heuristic bound");
    }

    MissionComputeResult best = result.best().result();
    assertNotNull(best, "the feasible best evaluation must carry its mission compute result");

    // S2 is the final active stage on LEO, so its residual is read at mission end (no jettison
    // capture, unlike GEO). It is the stage the residual floor guards, and it must clear it.
    int sizedStage = launcher.stages().size() - 1;
    StagePropellant s2 =
        best.performanceReport()
            .residualForStage(sizedStage)
            .orElseThrow(() -> new AssertionError("no per-stage propellant split for S2"));
    logger.info(
        "LEO multi-stage S2 at λ*: loaded={} kg, residual at mission end={} kg ({}% of its load)",
        Math.round(s2.loaded()),
        Math.round(s2.residual()),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * s2.residualRatio()));
    assertTrue(
        s2.residualRatio() >= MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "S2 residual at mission end (%.0f kg) is below %.0f%% of its %.0f kg load",
                s2.residual(),
                100.0 * MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
                s2.loaded()));

    // The retained solution must still be a genuine circular 400 km orbit, not merely inside the
    // altitude band the feasibility predicate reads off the terminal coast.
    MissionEphemerisPoint last = best.ephemeris().lastPoint();
    KeplerianOrbit finalOrbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            OrekitService.get().gcrf(),
            last.time(),
            Constants.WGS84_EARTH_MU);
    logger.info(
        "LEO multi-stage final orbit: a={} km, e={}, i={}°",
        String.format(java.util.Locale.ROOT, "%.1f", finalOrbit.getA() / 1000.0),
        String.format(java.util.Locale.ROOT, "%.5f", finalOrbit.getE()),
        String.format(java.util.Locale.ROOT, "%.2f", FastMath.toDegrees(finalOrbit.getI())));
    assertTrue(
        finalOrbit.getE() < LEO_ECCENTRICITY_TOLERANCE,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "Final eccentricity %.5f exceeds tolerance %.5f",
                finalOrbit.getE(),
                LEO_ECCENTRICITY_TOLERANCE));
  }

  /**
   * Multi-stage counterpart on GEO: <b>every</b> variable-load stage gets its own λ (S1 and S2 on
   * Falcon Heavy) and {@link MultiStageLoadOptimizer} minimizes them by coordinate-wise bisection.
   *
   * <p>GEO is where this is worth trying first, on measured evidence rather than hope: on the
   * single-λ run the gravity turn sat pinned exactly on its staging floor at every λ&lt;1, with a
   * zero-length second burn — the optimizer saying it has more first-stage propellant than the
   * profile needs. That is the slack this test goes after, and it is the opposite of the LEO
   * finding recorded in {@link PropellantLoadOptimizer#lambdaScaledMask}.
   *
   * <p>Reference to beat: the single-λ result on the same configuration, {@code λ*=0.8141} →
   * S2 8645 kg, S1 untouched at 1 233 000 kg.
   */
  @Test
  void geoMultiStage_shrinksEveryVariableLoadStage() {
    LauncherModel launcher = Launchers.FALCON_HEAVY;
    double payloadDryMass = Payloads.GEO_SAT.defaultDryMass();

    PropellantBudget.GeoLoads geoLoads =
        PropellantBudget.loadsForGeo(
            launcher,
            Payloads.GEO_SAT,
            payloadDryMass,
            GEO_PARKING_ALTITUDE_M,
            GEO_LAUNCH_LATITUDE_DEG);
    double[] heuristicLoads = geoLoads.launcherLoads();
    double akmLoad = geoLoads.akmLoad();
    boolean[] mask = PropellantLoadOptimizer.allVariableLoadMask(launcher);
    logStageLoads("GEO multi-stage heuristic loads", launcher, heuristicLoads, mask);
    logger.info("GEO heuristic AKM load: {} kg (off λ)", Math.round(akmLoad));

    AbsoluteDate launchEpoch =
        new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());

    Function<double[], Mission> missionBuilder =
        loads ->
            new GEOMission(
                "I7 GEO multi-stage",
                new LaunchConfiguration(
                    launcher, loads, Payloads.GEO_SAT.toSpacecraft(payloadDryMass, akmLoad)),
                GEO_PARKING_ALTITUDE_M,
                GEO_ALTITUDE_M,
                GEO_LAUNCH_LATITUDE_DEG,
                GEO_LAUNCH_LONGITUDE_DEG,
                0.0,
                0.0);

    MissionLoadEvaluator evaluator =
        new MissionLoadEvaluator(
            missionBuilder,
            heuristicLoads,
            mask,
            launchEpoch,
            MissionLoadEvaluator.DEFAULT_OPTIMIZER_MAX_EVALUATIONS,
            42L,
            GEO_ALTITUDE_TOLERANCE_M / GEO_ALTITUDE_M,
            MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
            OrbitInsertionObjective.circular(SolarSystemBody.EARTH, GEO_ALTITUDE_M, 0.0));

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator::evaluate, mask, heuristicLoads);

    double[] scaledLoads =
        PropellantLoadOptimizer.scaledLoads(result.lambdas(), heuristicLoads, mask);
    logStageLoads("GEO multi-stage loads at λ*", launcher, scaledLoads, mask);

    double scaledMassHeuristic = maskedSum(heuristicLoads, mask);
    double scaledMassFinal = maskedSum(scaledLoads, mask);
    logger.info(
        "I7 GEO multi-stage result: feasible={}, λ*={}, evals={}, passes={};"
            + " scaled-stage mass {} kg → {} kg (−{} kg, −{}%)",
        result.feasible(),
        Arrays.toString(result.lambdas()),
        result.evaluations(),
        result.passes(),
        Math.round(scaledMassHeuristic),
        Math.round(scaledMassFinal),
        Math.round(scaledMassHeuristic - scaledMassFinal),
        String.format(
            java.util.Locale.ROOT, "%.1f", 100.0 * (1.0 - scaledMassFinal / scaledMassHeuristic)));

    assertTrue(result.feasible(), "the heuristic GEO loads must themselves be feasible");
    assertTrue(
        scaledMassFinal < scaledMassHeuristic,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "scaled-stage mass %.0f kg must fall below the heuristic %.0f kg",
                scaledMassFinal,
                scaledMassHeuristic));
    for (int i = 0; i < mask.length; i++) {
      int index = i;
      assertTrue(
          result.lambdas()[index] <= PropellantLoadOptimizer.DEFAULT_LAMBDA_MAX + 1e-12,
          () -> "λ" + index + " must not exceed the heuristic bound");
    }

    MissionComputeResult best = result.best().result();
    assertNotNull(best, "the feasible best evaluation must carry its mission compute result");
    StagePropellant s2 =
        best.performanceReport()
            .residualForStage(1)
            .orElseThrow(() -> new AssertionError("no per-stage propellant split for S2"));
    logger.info(
        "GEO multi-stage S2 at λ*: loaded={} kg, residual at jettison={} kg ({}% of its load)",
        Math.round(s2.loaded()),
        Math.round(s2.residual()),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * s2.residualRatio()));
    assertTrue(
        s2.residualRatio() >= MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "S2 residual at jettison (%.0f kg) is below %.0f%% of its %.0f kg load",
                s2.residual(),
                100.0 * MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
                s2.loaded()));

    MissionEphemerisPoint last = best.ephemeris().lastPoint();
    KeplerianOrbit finalOrbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            OrekitService.get().gcrf(),
            last.time(),
            Constants.WGS84_EARTH_MU);
    assertTrue(
        finalOrbit.getI() < GEO_INCLINATION_TOLERANCE_RAD,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "Final inclination %.4f° exceeds tolerance %.3f°",
                FastMath.toDegrees(finalOrbit.getI()),
                FastMath.toDegrees(GEO_INCLINATION_TOLERANCE_RAD)));
  }

  /** Sum of the loads of the masked stages only — the mass actually under the loop's control. */
  private static double maskedSum(double[] loads, boolean[] mask) {
    double sum = 0.0;
    for (int i = 0; i < loads.length; i++) {
      if (mask[i]) {
        sum += loads[i];
      }
    }
    return sum;
  }

  private static void logStageLoads(
      String label, LauncherModel launcher, double[] loads, boolean[] mask) {
    for (int i = 0; i < loads.length; i++) {
      logger.info(
          "{} — stage {} '{}': {} kg (λ-scaled={}, capacity={} kg)",
          label,
          i,
          launcher.stages().get(i).name(),
          Math.round(loads[i]),
          mask[i],
          Math.round(launcher.stages().get(i).propellantCapacity()));
    }
  }
}
