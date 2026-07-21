package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Outer propellant-sizing loop of I7 (spec 09 §1): closes the "the launcher sizes the propellant"
 * loop by searching the <em>smallest</em> load that still reaches the objective, instead of flying
 * the heuristic {@link com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget} loads
 * with their 10 % margin.
 *
 * <p>The search is a scalar bisection on a single scale factor {@code λ} applied to the heuristic
 * loads: {@code load_i(λ) = λ · load_i^heuristic} for the liquid (variable-load) launcher stages,
 * SOLID stages and the payload AKM being left off the scaling (they keep their design/analytic
 * sizing — spec 09 §1). Feasibility (objective met <em>and</em> a per-stage residual floor) is
 * monotone in λ, so a bisection between an infeasible lower bound and a feasible upper bound
 * converges on the minimal feasible {@code λ*}.
 *
 * <p>This class owns only the <em>bisection</em> and the pure {@code λ → loads} mapping. Rebuilding
 * a mission with {@code loads(λ)}, running {@link MissionOptimizer#optimize()} and evaluating the
 * success predicate is delegated to an injected {@link Evaluator} — that mission reconstruction is
 * spec 09 §6 task 2. Keeping the two apart lets the bisection be unit-tested against a synthetic
 * monotone evaluator without any propagation (spec 09 §6 task 1: monotonicity + budget).
 */
public final class PropellantLoadOptimizer {
  private static final Logger logger = LogManager.getLogger(PropellantLoadOptimizer.class);

  /** Lower bound of the scaling factor: the heuristic margin is at most ~10 %, so 0.3 is ample. */
  public static final double DEFAULT_LAMBDA_MIN = 0.3;

  /** Upper bound of the scaling factor: {@code λ = 1} reproduces the heuristic loads exactly. */
  public static final double DEFAULT_LAMBDA_MAX = 1.0;

  /** Convergence tolerance on the width of the {@code λ} bracket (2 %, spec 09 §1). */
  public static final double DEFAULT_TOLERANCE = 0.02;

  /** External evaluation budget: each evaluation is a full mission optimization (spec 09 §1). */
  public static final int DEFAULT_MAX_EVALUATIONS = 10;

  private final double lambdaMin;
  private final double lambdaMax;
  private final double tolerance;
  private final int maxEvaluations;

  /** Creates an optimizer with the spec-09 defaults ({@code λ ∈ [0.3, 1]}, tol 2 %, ≤ 10 evals). */
  public PropellantLoadOptimizer() {
    this(DEFAULT_LAMBDA_MIN, DEFAULT_LAMBDA_MAX, DEFAULT_TOLERANCE, DEFAULT_MAX_EVALUATIONS);
  }

  /**
   * Creates an optimizer with explicit search settings.
   *
   * @param lambdaMin the lower bound of the scaling factor (infeasible end of the bracket)
   * @param lambdaMax the upper bound of the scaling factor (feasible end, {@code 1} = heuristic)
   * @param tolerance the convergence tolerance on the {@code λ} bracket width (must be positive)
   * @param maxEvaluations the maximum number of evaluations, including the two bound probes (≥ 2)
   */
  public PropellantLoadOptimizer(
      double lambdaMin, double lambdaMax, double tolerance, int maxEvaluations) {
    if (!(lambdaMin < lambdaMax)) {
      throw new IllegalArgumentException("lambdaMin must be < lambdaMax");
    }
    if (!(tolerance > 0)) {
      throw new IllegalArgumentException("tolerance must be positive");
    }
    if (maxEvaluations < 2) {
      throw new IllegalArgumentException("maxEvaluations must be >= 2 (the two bound probes)");
    }
    this.lambdaMin = lambdaMin;
    this.lambdaMax = lambdaMax;
    this.tolerance = tolerance;
    this.maxEvaluations = maxEvaluations;
  }

  /**
   * One external evaluation of the mission at a scale factor {@code λ}: whether the mission rebuilt
   * with {@code loads(λ)} still meets its objective within tolerance and keeps a residual above the
   * per-stage floor (spec 09 §1). The {@link #result()} carries the underlying computation so the
   * caller can surface performance/warm-start data; the bisection itself reads only {@link
   * #feasible()}.
   *
   * @param lambda the scale factor this evaluation was run at
   * @param feasible whether the objective is met and the residual floor respected
   * @param result the full mission computation, or {@code null} when the evaluator did not produce
   *     one (e.g. a synthetic evaluator in tests, or a build that failed before optimization)
   */
  public record Evaluation(double lambda, boolean feasible, MissionComputeResult result) {}

  /**
   * Rebuilds the mission with {@code loads(λ)}, optimizes it and reports feasibility. Implementations
   * warm-start the internal CMA-ES from {@code previous} — the immediately preceding evaluation,
   * whose {@link MissionComputeResult#optimizerResult()} holds each stage's {@code bestVariables} —
   * so the bisection's repeated calls do not restart the inner optimizer from scratch (spec 09 §1,
   * §2). This mission reconstruction is spec 09 §6 task 2.
   */
  @FunctionalInterface
  public interface Evaluator {
    /**
     * Evaluates the mission at scale factor {@code λ}.
     *
     * @param lambda the scale factor to apply to the heuristic liquid-stage loads
     * @param previous the last evaluation performed (nearest {@code λ}), or {@code null} on the very
     *     first call; a warm-start source for the internal optimizer
     * @return the evaluation outcome at {@code λ}
     */
    Evaluation evaluate(double lambda, Evaluation previous);
  }

  /**
   * Outcome of the bisection.
   *
   * @param feasible whether a feasible load was found (false only when even the heuristic {@code
   *     λ = λmax} load fails — an under-dotée mission)
   * @param lambda the minimal feasible scale factor found within tolerance (equals {@code λmax} when
   *     infeasible)
   * @param evaluations the number of evaluations spent (bounded by {@code maxEvaluations})
   * @param best the evaluation at {@link #lambda()} — the tightest feasible load, or the failing
   *     {@code λmax} probe when {@link #feasible()} is false
   */
  public record Result(boolean feasible, double lambda, int evaluations, Evaluation best) {}

  /**
   * Bisects on {@code λ} to find the smallest feasible load scaling.
   *
   * <p>Probes the feasible upper bound ({@code λmax}, the heuristic loads) and the infeasible-hoped
   * lower bound ({@code λmin}) first, then bisects the remaining bracket, always keeping {@code
   * infeasibleλ < feasibleλ}, until the bracket is narrower than the tolerance or the evaluation
   * budget is spent. The two short-circuits: if {@code λmax} is already infeasible the mission is
   * under-dotée (nothing to shrink); if {@code λmin} is already feasible the search cannot do
   * better and returns immediately.
   *
   * <p><b>Monotonicity is only approximate.</b> The bisection assumes feasibility is monotone in
   * {@code λ} (more propellant ⇒ still feasible). Physical closure is monotone, but the residual
   * floor is not: the inner CMA-ES optimizes orbit accuracy, not propellant thrift, so at a given
   * {@code λ} it may stochastically land on a wasteful solution that empties the sized stage
   * (residual 0, below the floor) even though a thriftier solution with margin exists. A {@code λ}
   * can therefore read "infeasible" while a slightly larger one reads "feasible" with generous
   * residual (observed on the FH LEO integration run). The consequence is benign: {@code λ*} stays a
   * genuinely feasible load with margin, but it may be <em>conservative</em> (a smaller feasible load
   * might exist) and can shift with the seed. Making it exact would require a propellant-aware inner
   * cost — out of scope here.
   *
   * @param evaluator rebuilds + optimizes the mission at a given {@code λ}
   * @return the minimal feasible scaling and the evaluation that achieved it
   */
  public Result minimize(Evaluator evaluator) {
    Objects.requireNonNull(evaluator, "evaluator");

    logger.info(
        "PropellantLoadOptimizer starting: λ∈[{}, {}], tolerance={}, budget={} evals",
        lambdaMin,
        lambdaMax,
        tolerance,
        maxEvaluations);

    int evaluations = 0;

    // Upper bound: the heuristic loads. If even these fail, the mission is under-dotée — there is
    // nothing to shrink, so report infeasible rather than bisect toward an ever-smaller load.
    Evaluation hi = evaluator.evaluate(lambdaMax, null);
    evaluations++;
    logger.info("Probe λ={} (upper bound / heuristic loads): feasible={}", lambdaMax, hi.feasible());
    if (!hi.feasible()) {
      logger.warn(
          "Heuristic loads (λ={}) infeasible — mission under-dotée, nothing to shrink; aborting"
              + " after {} eval(s)",
          lambdaMax,
          evaluations);
      return new Result(false, lambdaMax, evaluations, hi);
    }

    // Lower bound. If the smallest allowed load already succeeds, we cannot do better; stop.
    Evaluation lo = evaluator.evaluate(lambdaMin, hi);
    evaluations++;
    logger.info("Probe λ={} (lower bound): feasible={}", lambdaMin, lo.feasible());
    if (lo.feasible()) {
      logger.info(
          "Lower bound λ={} already feasible — cannot do better; λ*={} after {} evals",
          lambdaMin,
          lambdaMin,
          evaluations);
      return new Result(true, lambdaMin, evaluations, lo);
    }

    double infeasibleLambda = lambdaMin; // known infeasible
    double feasibleLambda = lambdaMax; // known feasible
    Evaluation best = hi; // smallest-λ feasible evaluation so far
    Evaluation previous = lo; // last evaluation performed, for warm-start

    while (evaluations < maxEvaluations && (feasibleLambda - infeasibleLambda) > tolerance) {
      double mid = 0.5 * (infeasibleLambda + feasibleLambda);
      Evaluation midEval = evaluator.evaluate(mid, previous);
      evaluations++;
      previous = midEval;
      if (midEval.feasible()) {
        feasibleLambda = mid;
        best = midEval;
      } else {
        infeasibleLambda = mid;
      }
      logger.info(
          "Bisection eval {}/{}: λ={} feasible={} → bracket [{}, {}] (width {})",
          evaluations,
          maxEvaluations,
          mid,
          midEval.feasible(),
          infeasibleLambda,
          feasibleLambda,
          feasibleLambda - infeasibleLambda);
    }

    boolean converged = (feasibleLambda - infeasibleLambda) <= tolerance;
    logger.info(
        "PropellantLoadOptimizer done: λ*={} after {} evals ({}), final bracket width={}",
        feasibleLambda,
        evaluations,
        converged ? "converged within tolerance" : "budget exhausted",
        feasibleLambda - infeasibleLambda);
    return new Result(true, feasibleLambda, evaluations, best);
  }

  /**
   * Applies the scale factor to the liquid-stage loads, leaving the non-scaled stages untouched.
   * {@code load_i(λ) = λ · load_i^heuristic} where {@code lambdaScaled[i]} is true, otherwise {@code
   * load_i^heuristic}. Because {@code λ ≤ 1} and the heuristic loads never exceed capacity, no upper
   * clamp is needed; the result is floored at zero for safety.
   *
   * @param lambda the scale factor
   * @param heuristicLoads the baseline per-stage loads (kg), same order as the launcher stages
   * @param lambdaScaled which stages the scaling applies to (see {@link #lambdaScaledMask})
   * @return a new per-stage load array scaled by {@code λ} on the flagged stages
   */
  public static double[] scaledLoads(
      double lambda, double[] heuristicLoads, boolean[] lambdaScaled) {
    if (heuristicLoads.length != lambdaScaled.length) {
      throw new IllegalArgumentException(
          "heuristicLoads and lambdaScaled must have the same length");
    }
    double[] scaled = heuristicLoads.clone();
    for (int i = 0; i < scaled.length; i++) {
      if (lambdaScaled[i]) {
        scaled[i] = Math.max(0.0, lambda * heuristicLoads[i]);
      }
    }
    return scaled;
  }

  /**
   * Builds the scaling mask for a launcher: only the <b>sized top stage</b> — the last stage, and
   * only when it is variable-load (liquid) — is scaled by {@code λ}. The lower stages and any SOLID
   * stage stay off the scaling, and the payload AKM is sized separately and never appears in the
   * launcher loads.
   *
   * <p><b>Deviation from spec 09 §1</b> (which puts every non-SOLID, non-AKM stage under {@code λ}).
   * On the flown profiles the first stage is never jettisoned — it is dragged, dry mass and all, to
   * orbit — so its full load is already "just enough" to loft its own structure (~0.3 % residual on
   * Falcon Heavy LEO). Scaling it down breaks the ascent immediately, pinning {@code λ*} at 1 with
   * nothing reclaimed. The only stage carrying a genuine, reclaimable margin is the one {@link
   * com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget} actually sizes: the top
   * stage. Restricting {@code λ} to it is what lets the loop reclaim propellant on an un-staged
   * stack (finding logged when the I7 integration test first ran on FH LEO).
   *
   * @param launcher the launcher model
   * @return a per-stage boolean mask, {@code true} only on the sized (top, variable-load) stage
   */
  public static boolean[] lambdaScaledMask(LauncherModel launcher) {
    List<StageModel> stages = launcher.stages();
    boolean[] mask = new boolean[stages.size()];
    int topStage = stages.size() - 1;
    mask[topStage] = stages.get(topStage).capabilities().variableLoad();
    return mask;
  }
}
