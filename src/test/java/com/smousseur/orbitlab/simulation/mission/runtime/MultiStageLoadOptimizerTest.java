package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.runtime.PropellantLoadOptimizer.Evaluation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit test of the multi-stage coordinate sweep, exercised against synthetic feasibility functions
 * so the search logic is validated without any propagation — the same approach {@link
 * PropellantLoadOptimizerTest} takes for the scalar bisection.
 */
class MultiStageLoadOptimizerTest {

  /** Feasible iff every scaled coordinate clears its own threshold — separable, fully monotone. */
  private static final class PerStageThresholdEvaluator implements MultiStageLoadOptimizer.Evaluator {
    final double[] thresholds;
    final boolean[] scaled;
    final List<double[]> calls = new ArrayList<>();

    PerStageThresholdEvaluator(double[] thresholds, boolean[] scaled) {
      this.thresholds = thresholds;
      this.scaled = scaled;
    }

    @Override
    public Evaluation evaluate(double[] lambdas, Evaluation previous) {
      calls.add(lambdas.clone());
      boolean feasible = true;
      for (int i = 0; i < lambdas.length; i++) {
        if (scaled[i] && lambdas[i] < thresholds[i]) {
          feasible = false;
        }
      }
      return new Evaluation(Double.NaN, feasible, null);
    }
  }

  /** Feasible iff the scaled coordinates' <em>sum</em> clears a budget — a diagonal boundary. */
  private static final class SumBudgetEvaluator implements MultiStageLoadOptimizer.Evaluator {
    final double budget;
    final boolean[] scaled;
    final List<double[]> calls = new ArrayList<>();

    SumBudgetEvaluator(double budget, boolean[] scaled) {
      this.budget = budget;
      this.scaled = scaled;
    }

    @Override
    public Evaluation evaluate(double[] lambdas, Evaluation previous) {
      calls.add(lambdas.clone());
      double sum = 0.0;
      for (int i = 0; i < lambdas.length; i++) {
        if (scaled[i]) {
          sum += lambdas[i];
        }
      }
      return new Evaluation(Double.NaN, sum >= budget, null);
    }
  }

  private static final double TOL = PropellantLoadOptimizer.DEFAULT_TOLERANCE;

  // ── Strict generalization of the scalar optimizer ─────────────────────────

  @Test
  void singleScaledStage_reproducesTheScalarBisectionResult() {
    // The migration guarantee: with one stage under λ this must land where PropellantLoadOptimizer
    // lands, so the existing LEO/GEO λ* stay valid references.
    double threshold = 0.62;
    boolean[] scaled = {false, true};
    double[] loads = {1_233_000, 10_619};

    PropellantLoadOptimizer.Result scalar =
        new PropellantLoadOptimizer()
            .minimize((lambda, previous) -> new Evaluation(lambda, lambda >= threshold, null));

    MultiStageLoadOptimizer.Result multi =
        new MultiStageLoadOptimizer()
            .minimize(
                new PerStageThresholdEvaluator(new double[] {0.0, threshold}, scaled),
                scaled,
                loads);

    assertTrue(multi.feasible());
    assertEquals(scalar.lambda(), multi.lambdas()[1], 1e-12, "same λ* as the scalar bisection");
    assertEquals(1.0, multi.lambdas()[0], 1e-12, "unscaled stage stays at the heuristic load");
  }

  // ── Two coordinates ───────────────────────────────────────────────────────

  @Test
  void twoScaledStages_convergeToBothThresholds() {
    boolean[] scaled = {true, true};
    double[] thresholds = {0.55, 0.78};
    PerStageThresholdEvaluator evaluator = new PerStageThresholdEvaluator(thresholds, scaled);

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {100_000, 10_000});

    assertTrue(result.feasible());
    for (int i = 0; i < 2; i++) {
      int index = i;
      assertTrue(
          result.lambdas()[index] >= thresholds[index],
          () -> "λ" + index + " must stay feasible, got " + result.lambdas()[index]);
      assertTrue(
          result.lambdas()[index] - thresholds[index] <= TOL,
          () -> "λ" + index + " must reach its threshold within tolerance");
    }
  }

  @Test
  void sweepStartsFromTheTopStage() {
    boolean[] scaled = {true, true};
    PerStageThresholdEvaluator evaluator =
        new PerStageThresholdEvaluator(new double[] {0.55, 0.78}, scaled);

    new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {100_000, 10_000});

    // Call 0 is the heuristic probe; call 1 opens the first coordinate's bracket at λmin. The top
    // stage (index 1) must be the one moving there — the order PropellantBudget sizes in.
    double[] firstBisectionCall = evaluator.calls.get(1);
    assertEquals(
        PropellantLoadOptimizer.DEFAULT_LAMBDA_MIN, firstBisectionCall[1], 1e-12, "top stage first");
    assertEquals(1.0, firstBisectionCall[0], 1e-12, "lower stage still at its heuristic load");
  }

  @Test
  void doesNotRepayForTheUpperBoundOfEachCoordinate() {
    // minimizeBelow reuses the already-known feasible point; a coordinate must never re-probe
    // λ=1 with the rest of the vector unchanged (that would cost a full mission optimization).
    boolean[] scaled = {true, true};
    PerStageThresholdEvaluator evaluator =
        new PerStageThresholdEvaluator(new double[] {0.55, 0.78}, scaled);

    new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {100_000, 10_000});

    long allOnes =
        evaluator.calls.stream().filter(c -> c[0] == 1.0 && c[1] == 1.0).count();
    assertEquals(1, allOnes, "the all-heuristic point must be evaluated exactly once");
  }

  // ── Refinement passes: probe the endpoint before re-bisecting ─────────────

  /**
   * Feasible iff {@code λ0 ≥ floor0} and {@code λ1 ≥ threshold1 − coupling·(1 − λ0)}: lightening the
   * lower stage relaxes what the upper one needs. This is the coupling the refinement passes exist
   * for — a separable evaluator can never exercise them, because a second sweep provably finds
   * nothing there.
   */
  private static final class CoupledEvaluator implements MultiStageLoadOptimizer.Evaluator {
    final List<double[]> calls = new ArrayList<>();

    @Override
    public Evaluation evaluate(double[] lambdas, Evaluation previous) {
      calls.add(lambdas.clone());
      boolean feasible = lambdas[0] >= 0.55 && lambdas[1] >= 0.78 - 0.5 * (1.0 - lambdas[0]);
      return new Evaluation(Double.NaN, feasible, null);
    }
  }

  @Test
  void refinementPass_withoutSlack_costsOneProbeInsteadOfAFullBisection() {
    // On a separable boundary the second pass provably finds nothing: each coordinate is already at
    // its own threshold. Measured on both FH profiles, that pass cost 12-13 evaluations to return
    // the exact λ it started from — 26 % (LEO) and 34 % (GEO) of the run for zero kilogram.
    //
    // One evaluation answers it: if λ−tolerance is infeasible, the minimal feasible λ lies inside
    // (λ−tolerance, λ], a bracket whose width IS the bisection's convergence criterion, so the
    // bisection would have returned λ anyway.
    boolean[] scaled = {true, true};
    PerStageThresholdEvaluator evaluator =
        new PerStageThresholdEvaluator(new double[] {0.55, 0.78}, scaled);

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {100_000, 10_000});

    double[] finalLambdas = result.lambdas();
    assertTrue(result.passes() >= 2, "the scenario must actually reach a refinement pass");

    // The top stage is swept first, so its pass-1 bisection ran with the lower stage still at 1.0.
    // A call opening at λmin with the lower stage at its FINAL value can therefore only come from a
    // refinement pass re-bisecting from scratch — exactly what the probe replaces.
    boolean reopenedTheBracket =
        evaluator.calls.stream()
            .anyMatch(
                c ->
                    Math.abs(c[1] - PropellantLoadOptimizer.DEFAULT_LAMBDA_MIN) < 1e-12
                        && Math.abs(c[0] - finalLambdas[0]) < 1e-12);
    assertFalse(
        reopenedTheBracket,
        "a refinement pass must not reopen a full bracket at λmin: the endpoint probe settles it");

    boolean probed =
        evaluator.calls.stream()
            .anyMatch(
                c ->
                    Math.abs(c[0] - finalLambdas[0]) < 1e-12
                        && Math.abs(c[1] - (finalLambdas[1] - TOL)) < 1e-9);
    assertTrue(probed, "the refinement pass must probe exactly one tolerance below the current λ");

    // And the answer must be the one the full bisection gave.
    assertTrue(finalLambdas[0] >= 0.55 && finalLambdas[0] - 0.55 <= TOL, "λ0 still at its threshold");
    assertTrue(finalLambdas[1] >= 0.78 && finalLambdas[1] - 0.78 <= TOL, "λ1 still at its threshold");
  }

  @Test
  void refinementPass_withSlack_stillBisectsAndReclaimsIt() {
    // The probe is a short circuit, not a cap: when it succeeds there is real coupling to exploit
    // and the full bisection must still run. Here pass 1 leaves λ1 at 0.78 (decided while λ0 was
    // still 1.0); once λ0 drops to 0.55 the constraint on λ1 relaxes to 0.555, and the refinement
    // pass has to find it.
    boolean[] scaled = {true, true};
    CoupledEvaluator evaluator = new CoupledEvaluator();

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {100_000, 10_000});

    assertTrue(result.feasible());
    assertTrue(
        result.lambdas()[1] <= 0.60,
        () ->
            "the refinement pass must reclaim the slack the coupling opened (expected ≈0.555), got "
                + result.lambdas()[1]);
  }

  // ── The pass-gain rule must be per coordinate ─────────────────────────────

  @Test
  void passGain_isMeasuredPerCoordinate_notOnTheStackDominatingStage() {
    // Loads as lopsided as the real Falcon Heavy stack: S1 at 1.2 M kg against S2 at ~1.3 t.
    //
    // Measured 2026-08-06 on LEO: a refinement pass reclaimed 3 % of S2's load and the gain,
    // measured on the scaled stages' TOTAL mass, read 0.0035 % — below the 1 % floor, so the sweep
    // stopped. S2 cannot structurally weigh 1 % of a total S1 dominates, so no gain on an upper
    // stage can ever earn another pass. That is the same domination the class javadoc invokes to
    // reject a global λ, reappearing in the stopping rule.
    //
    // Here the coupling lets pass 2 reclaim ~29 % of the upper coordinate while the lower one does
    // not move: on the total that is ~0.04 %, so the old rule stopped at two passes.
    boolean[] scaled = {true, true};
    CoupledEvaluator evaluator = new CoupledEvaluator();

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {1_000_000, 1_000});

    assertTrue(result.feasible());
    assertEquals(
        3,
        result.passes(),
        "a coordinate that moved 29 % of its own load must earn another pass, however little it"
            + " weighs in the stack");
  }

  @Test
  void passGain_stillStopsWhenNoCoordinateMoves() {
    // The counterpart: on a separable boundary no coordinate moves in pass 2, so the per-coordinate
    // rule must stop exactly where the aggregate one did — the change must not turn the floor off.
    boolean[] scaled = {true, true};
    PerStageThresholdEvaluator evaluator =
        new PerStageThresholdEvaluator(new double[] {0.55, 0.78}, scaled);

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {1_000_000, 1_000});

    assertEquals(2, result.passes(), "nothing moved in pass 2: the sweep must stop there");
  }

  // ── Budget and short-circuits ─────────────────────────────────────────────

  @Test
  void neverExceedsTheEvaluationBudget() {
    boolean[] scaled = {true, true, true};
    for (double t = 0.35; t < 1.0; t += 0.07) {
      PerStageThresholdEvaluator evaluator =
          new PerStageThresholdEvaluator(new double[] {t, t + 0.05, t - 0.03}, scaled);
      MultiStageLoadOptimizer.Result result =
          new MultiStageLoadOptimizer()
              .minimize(evaluator, scaled, new double[] {100_000, 10_000, 1_000});
      assertTrue(
          result.evaluations() <= MultiStageLoadOptimizer.DEFAULT_MAX_EVALUATIONS,
          () -> "budget exceeded, spent " + result.evaluations());
      assertEquals(evaluator.calls.size(), result.evaluations(), "count matches actual calls");
    }
  }

  @Test
  void heuristicLoadsInfeasible_reportsUnderDoteeInOneEval() {
    boolean[] scaled = {true};
    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer()
            .minimize(
                new PerStageThresholdEvaluator(new double[] {1.5}, scaled),
                scaled,
                new double[] {10_000});

    assertFalse(result.feasible());
    assertEquals(1, result.evaluations(), "stops at the failing heuristic probe");
    assertEquals(0, result.passes());
  }

  @Test
  void noScaledStage_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MultiStageLoadOptimizer()
                .minimize(
                    (lambdas, previous) -> new Evaluation(Double.NaN, true, null),
                    new boolean[] {false, false},
                    new double[] {1, 2}));
  }

  // ── Diagonal boundary: the coordinate-descent stall ───────────────────────

  @Test
  void diagonalBoundary_stillReturnsAFeasiblePoint() {
    // Feasibility depends on the SUM, so the boundary is diagonal and coordinate descent stalls on
    // a corner. Whatever it returns must at least be feasible and no worse than the heuristic.
    boolean[] scaled = {true, true};
    SumBudgetEvaluator evaluator = new SumBudgetEvaluator(1.30, scaled);

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {10_000, 10_000});

    assertTrue(result.feasible());
    double sum = result.lambdas()[0] + result.lambdas()[1];
    assertTrue(sum >= 1.30 - 1e-9, () -> "returned point must be feasible, sum=" + sum);
    assertTrue(sum <= 2.0, () -> "must improve on the heuristic (sum 2.0), got " + sum);
  }

  @Test
  void diagonalProbeStepsEveryMovableCoordinateByAnAbsoluteStep() {
    boolean[] scaled = {true, true};
    PerStageThresholdEvaluator evaluator =
        new PerStageThresholdEvaluator(new double[] {0.55, 0.78}, scaled);
    MultiStageLoadOptimizer optimizer = new MultiStageLoadOptimizer();

    MultiStageLoadOptimizer.Result result =
        optimizer.minimize(evaluator, scaled, new double[] {100_000, 10_000});

    // The step must be absolute on the λ axis, not relative (bilan 11 §3.1): a relative step is
    // smaller than the bisection's own bracket tolerance as soon as λ < 1, so it probes inside the
    // unresolved bracket and can only re-measure what the bisection already declined to resolve.
    double[] last = evaluator.calls.get(evaluator.calls.size() - 1);
    // On a separable boundary the probe fails, so the result keeps its pre-probe λ.
    assertEquals(result.lambdas()[0] - optimizer.diagonalStep(), last[0], 1e-9);
    assertEquals(result.lambdas()[1] - optimizer.diagonalStep(), last[1], 1e-9);
    assertTrue(
        optimizer.diagonalStep() >= TOL,
        "the diagonal step must not be finer than the bisection's own resolution");
  }

  @Test
  void diagonalProbeSkipped_whenOnlyOneCoordinateLeftTheHeuristicLoad() {
    // Threshold 1.0 on the lower stage pins it at λmax: every step down fails, exactly the FH LEO
    // situation. The probe then has a single movable coordinate — no corner to find — and must not
    // spend an evaluation stepping the pinned one, whose failure would say nothing about a corner.
    boolean[] scaled = {true, true};
    PerStageThresholdEvaluator evaluator =
        new PerStageThresholdEvaluator(new double[] {1.0, 0.78}, scaled);

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {100_000, 10_000});

    assertTrue(result.feasible());
    assertEquals(1.0, result.lambdas()[0], 1e-12, "the pinned coordinate stays at the heuristic");
    assertEquals(0, diagonalCalls(evaluator.calls, result.lambdas()), "no diagonal probe ran");
  }

  @Test
  void diagonalProbeRunsExactlyOnce_whenTwoCoordinatesMoved() {
    boolean[] scaled = {true, true};
    PerStageThresholdEvaluator evaluator =
        new PerStageThresholdEvaluator(new double[] {0.55, 0.78}, scaled);

    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator, scaled, new double[] {100_000, 10_000});

    assertEquals(1, diagonalCalls(evaluator.calls, result.lambdas()), "one probe, at the very end");
  }

  /**
   * Counts the evaluations that stepped <em>every</em> scaled coordinate strictly below its final
   * value — the signature of a diagonal probe. A coordinate-wise bisection never produces one: it
   * moves a single coordinate while the others sit at a value they will not rise above.
   */
  private static long diagonalCalls(List<double[]> calls, double[] finalLambdas) {
    return calls.stream()
        .filter(
            call -> {
              for (int i = 0; i < call.length; i++) {
                if (call[i] >= finalLambdas[i] - 1e-12) {
                  return false;
                }
              }
              return true;
            })
        .count();
  }

  // ── Per-stage λ → loads mapping ───────────────────────────────────────────

  @Test
  void scaledLoads_appliesEachStageItsOwnFactor() {
    double[] heuristic = {1_000_000, 100_000, 5_000};
    boolean[] mask = {false, true, true};
    double[] scaled =
        PropellantLoadOptimizer.scaledLoads(new double[] {0.1, 0.5, 0.8}, heuristic, mask);

    assertEquals(1_000_000, scaled[0], 1e-9, "unscaled stage ignores its λ entirely");
    assertEquals(50_000, scaled[1], 1e-9);
    assertEquals(4_000, scaled[2], 1e-9);
    assertEquals(100_000, heuristic[1], 1e-9, "input not mutated");
  }

  @Test
  void scaledLoads_perStage_lengthMismatch_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PropellantLoadOptimizer.scaledLoads(
                new double[] {0.5}, new double[] {1, 2}, new boolean[] {true, true}));
  }
}
