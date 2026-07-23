package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Multi-stage propellant sizing: gives <b>each</b> variable-load stage its own scale factor and
 * minimizes them by cyclic coordinate-wise bisection, generalizing the single-{@code λ} {@link
 * PropellantLoadOptimizer}.
 *
 * <p><b>One coordinate at a time.</b> Exactly one {@code λ_i} moves per bisection step, the others
 * held fixed. That is what keeps the feasibility predicate scalar and monotone, hence bisectable in
 * ~6 evaluations with a guaranteed bracket. Moving two together would require searching a
 * two-dimensional boundary, and at 25–65 s per evaluation (each one a full mission optimization)
 * the budget is the binding constraint on this whole problem.
 *
 * <p><b>Why not a CMA-ES outer loop.</b> With {@code n} between 1 and 3, coordinate descent spends
 * ~27 evaluations for {@code n=2} and ~39 for {@code n=3}, where CMA-ES would need several hundred
 * before its covariance adaptation pays for itself — and the existing CMA-ES stack could not be
 * reused anyway, since it is built around {@code TrajectoryProblem} (propagate to a
 * {@code SpacecraftState}, grade that state) while an outer evaluation returns a mission result
 * plus a feasibility flag. Coordinate descent also exploits the monotonicity CMA-ES would throw
 * away, and every step stays readable in the log.
 *
 * <p><b>No global λ.</b> Scaling all stages by one common factor looks cheaper but cannot work
 * here: the stages carry marginal slack of wildly different magnitude, so a common factor is pinned
 * by the tightest one (the first stage) and the upper stages gain nothing — the same domination
 * that makes a raw total-mass objective meaningless when one stage weighs 1.2 M kg and another
 * 8.6 t. For that reason progress is measured against the <em>scaled</em> stages' mass only, never
 * the stack total.
 *
 * <p><b>Known limit.</b> Coordinate descent can stall on a corner of the feasible boundary that is
 * not its global minimum, because the coupling between stages is real and asymmetric: lightening an
 * upper stage eases the job of the stages below it, whereas lightening a lower stage makes the
 * upper ones work harder. The closing {@linkplain #diagonalStep diagonal probe} spends one
 * evaluation looking for the classic axis-aligned stall, and only when at least two coordinates
 * actually moved off the heuristic load — with fewer, there is no corner to find. A stall it does
 * not catch would call for a surrogate (Bayesian) search, which is the escalation path — not
 * CMA-ES.
 */
public final class MultiStageLoadOptimizer {
  private static final Logger logger = LogManager.getLogger(MultiStageLoadOptimizer.class);

  /** Sweeps performed at most, before the diagonal probe. */
  public static final int DEFAULT_MAX_PASSES = 3;

  /**
   * Total evaluation budget. Sized for {@code n=3}: ~8 evaluations for the first pass of each
   * coordinate, ~4 for each refinement pass, plus the diagonal probe.
   */
  public static final int DEFAULT_MAX_EVALUATIONS = 45;

  /**
   * A sweep reclaiming less than this fraction of the scaled stages' mass ends the search: the
   * coordinates have stopped moving each other and further passes would only re-measure the same
   * point.
   */
  public static final double DEFAULT_MIN_PASS_GAIN = 0.01;

  private final double lambdaMin;
  private final double lambdaMax;
  private final double tolerance;
  private final int maxEvaluations;
  private final int maxPasses;
  private final double minPassGain;

  /** Creates an optimizer with the default search settings. */
  public MultiStageLoadOptimizer() {
    this(
        PropellantLoadOptimizer.DEFAULT_LAMBDA_MIN,
        PropellantLoadOptimizer.DEFAULT_LAMBDA_MAX,
        PropellantLoadOptimizer.DEFAULT_TOLERANCE,
        DEFAULT_MAX_EVALUATIONS,
        DEFAULT_MAX_PASSES,
        DEFAULT_MIN_PASS_GAIN);
  }

  /**
   * Creates an optimizer with explicit search settings.
   *
   * @param lambdaMin lower bound of every {@code λ_i}
   * @param lambdaMax upper bound of every {@code λ_i} ({@code 1} = heuristic load)
   * @param tolerance convergence tolerance on each coordinate's bracket width
   * @param maxEvaluations total evaluation budget across all passes
   * @param maxPasses maximum number of sweeps over the coordinates
   * @param minPassGain sweep gain, as a fraction of the scaled stages' mass, below which the search
   *     stops
   */
  public MultiStageLoadOptimizer(
      double lambdaMin,
      double lambdaMax,
      double tolerance,
      int maxEvaluations,
      int maxPasses,
      double minPassGain) {
    if (!(lambdaMin < lambdaMax)) {
      throw new IllegalArgumentException("lambdaMin must be < lambdaMax");
    }
    if (!(tolerance > 0)) {
      throw new IllegalArgumentException("tolerance must be positive");
    }
    if (maxEvaluations < 2) {
      throw new IllegalArgumentException("maxEvaluations must be >= 2");
    }
    if (maxPasses < 1) {
      throw new IllegalArgumentException("maxPasses must be >= 1");
    }
    this.lambdaMin = lambdaMin;
    this.lambdaMax = lambdaMax;
    this.tolerance = tolerance;
    this.maxEvaluations = maxEvaluations;
    this.maxPasses = maxPasses;
    this.minPassGain = minPassGain;
  }

  /**
   * Step, on the {@code λ} axis, that the closing diagonal probe takes on every movable coordinate
   * at once.
   *
   * <p><b>Absolute, not relative</b> (bilan 11 §3.1). The step has to be commensurable with the
   * bisection's own convergence criterion, which is an absolute bracket width; a relative step
   * shrinks below that width as soon as {@code λ < 1}, so the probe lands <em>inside</em> the
   * unresolved bracket and re-asks a question the bisection just declined to answer. Measured on
   * FH LEO: at {@code λ = 0.43125} with a converged bracket of {@code [0.4203, 0.43125]}, a 2 %
   * relative step probed {@code 0.4226} — strictly inside it, hence an uninformative failure.
   */
  public double diagonalStep() {
    return tolerance;
  }

  /**
   * Rebuilds the mission with per-stage loads and reports feasibility. Same contract as {@link
   * PropellantLoadOptimizer.Evaluator}, with a full {@code λ} vector instead of a single factor.
   */
  @FunctionalInterface
  public interface Evaluator {
    /**
     * Evaluates the mission at a per-stage scaling.
     *
     * @param lambdas per-stage scale factors, same length and order as the launcher stages
     * @param previous the last evaluation performed, or {@code null} on the very first call
     * @return the evaluation outcome, whose {@code lambda} field carries the coordinate under test
     */
    PropellantLoadOptimizer.Evaluation evaluate(
        double[] lambdas, PropellantLoadOptimizer.Evaluation previous);
  }

  /**
   * Outcome of the coordinate sweep.
   *
   * @param feasible whether a feasible load vector was found (false only when the heuristic loads
   *     themselves fail)
   * @param lambdas the per-stage scale factors found, full length, {@code 1} on unscaled stages
   * @param evaluations evaluations spent, including the heuristic probe and the diagonal probe
   * @param passes sweeps actually performed
   * @param best the evaluation at {@link #lambdas()}
   */
  public record Result(
      boolean feasible,
      double[] lambdas,
      int evaluations,
      int passes,
      PropellantLoadOptimizer.Evaluation best) {}

  /**
   * Minimizes the per-stage loads by cyclic coordinate-wise bisection.
   *
   * <p>Coordinates are swept <b>top stage down</b>, the order the analytic {@code PropellantBudget}
   * already sizes in, and a deterministic one — which matters while the result is being compared
   * against the single-{@code λ} baseline. Each pass logs the per-stage residual so a
   * slack-ordering heuristic can be settled on data later; note such a heuristic would only be
   * valid for cutoff-terminated stages, since a stage burnt to depletion always reads a 0 %
   * residual whatever its slack.
   *
   * @param evaluator rebuilds + optimizes the mission at a given load vector
   * @param lambdaScaled which stages carry their own {@code λ} (see {@link
   *     PropellantLoadOptimizer#allVariableLoadMask})
   * @param heuristicLoads the baseline per-stage loads (kg), used to report reclaimed mass
   * @return the minimal feasible per-stage scaling found
   */
  public Result minimize(Evaluator evaluator, boolean[] lambdaScaled, double[] heuristicLoads) {
    Objects.requireNonNull(evaluator, "evaluator");
    if (lambdaScaled.length != heuristicLoads.length) {
      throw new IllegalArgumentException("lambdaScaled and heuristicLoads length mismatch");
    }
    int[] coordinates = scaledCoordinatesTopDown(lambdaScaled);
    if (coordinates.length == 0) {
      throw new IllegalArgumentException("no stage is under λ — nothing to size");
    }

    double[] lambdas = new double[lambdaScaled.length];
    Arrays.fill(lambdas, lambdaMax);

    logger.info(
        "MultiStageLoadOptimizer starting: {} scaled stage(s) {} (swept top-down), λ∈[{}, {}],"
            + " tolerance={}, budget={} evals, ≤{} passes",
        coordinates.length,
        Arrays.toString(coordinates),
        lambdaMin,
        lambdaMax,
        tolerance,
        maxEvaluations,
        maxPasses);

    // Heuristic point. If it already fails there is nothing to shrink — same short-circuit the
    // scalar optimizer applies, and the only case that reports infeasible.
    PropellantLoadOptimizer.Evaluation current = evaluator.evaluate(lambdas.clone(), null);
    int evaluations = 1;
    logger.info("Probe heuristic loads (all λ={}): feasible={}", lambdaMax, current.feasible());
    if (!current.feasible()) {
      logger.warn("Heuristic loads infeasible — mission under-dotée, nothing to shrink; aborting");
      return new Result(false, lambdas, evaluations, 0, current);
    }

    int passes = 0;
    for (int pass = 1; pass <= maxPasses; pass++) {
      double scaledMassBefore = scaledMass(lambdas, heuristicLoads, lambdaScaled);

      for (int index : coordinates) {
        int remaining = maxEvaluations - evaluations;
        if (remaining < 2) {
          logger.info("Evaluation budget spent — stopping mid-pass {}", pass);
          return finish(lambdas, evaluations, passes, current, heuristicLoads, lambdaScaled);
        }
        logger.info(
            "Pass {}/{} — bisecting stage {} from λ={} (others fixed at {})",
            pass,
            maxPasses,
            index,
            format(lambdas[index]),
            format(lambdas));

        CoordinateAdapter adapter = new CoordinateAdapter(evaluator, lambdas, index);
        PropellantLoadOptimizer bisection =
            new PropellantLoadOptimizer(lambdaMin, lambdaMax, tolerance, remaining);
        PropellantLoadOptimizer.Result coordinateResult =
            bisection.minimizeBelow(adapter, lambdas[index], current);

        evaluations += coordinateResult.evaluations();
        double before = lambdas[index];
        lambdas[index] = coordinateResult.lambda();
        current = coordinateResult.best();
        logger.info(
            "Pass {} — stage {}: λ {} → {} ({} evals, {} evals used overall)",
            pass,
            index,
            format(before),
            format(lambdas[index]),
            coordinateResult.evaluations(),
            evaluations);
      }
      passes = pass;

      double scaledMassAfter = scaledMass(lambdas, heuristicLoads, lambdaScaled);
      double gain =
          scaledMassBefore > 0 ? (scaledMassBefore - scaledMassAfter) / scaledMassBefore : 0.0;
      logger.info(
          "Pass {} done: scaled-stage mass {} → {} kg ({} reclaimed, {}%), λ={}",
          pass,
          Math.round(scaledMassBefore),
          Math.round(scaledMassAfter),
          Math.round(scaledMassBefore - scaledMassAfter),
          String.format(Locale.ROOT, "%.1f", 100.0 * gain),
          format(lambdas));
      logStageResiduals(current);

      if (gain < minPassGain) {
        logger.info(
            "Pass gain {}% below the {}% floor — coordinates have stopped moving each other",
            String.format(Locale.ROOT, "%.1f", 100.0 * gain),
            String.format(Locale.ROOT, "%.1f", 100.0 * minPassGain));
        break;
      }
    }

    // Diagonal probe: coordinate descent is blind to a feasible direction that is not axis-aligned.
    // One step down on every movable coordinate at once costs a single evaluation and tells us
    // whether the sweep stopped on the boundary or merely in a corner.
    int[] movable = movableCoordinates(coordinates, lambdas);
    if (movable.length < 2) {
      // A corner needs two coordinates. With one, the diagonal degenerates into a single-coordinate
      // step the bisection has just bracketed — a full mission optimization spent on a known answer.
      logger.info(
          "Diagonal probe skipped: {} movable coordinate(s) of {} scaled, a corner needs two",
          movable.length,
          coordinates.length);
    } else if (evaluations >= maxEvaluations) {
      logger.info("Diagonal probe skipped: evaluation budget spent");
    } else {
      double[] diagonal = lambdas.clone();
      for (int index : movable) {
        diagonal[index] = Math.max(lambdaMin, lambdas[index] - diagonalStep());
      }
      PropellantLoadOptimizer.Evaluation diagonalEval =
          evaluator.evaluate(diagonal.clone(), current);
      evaluations++;
      logger.info(
          "Diagonal probe (λ −{} on the {} movable coordinate(s) {}, others held): λ={} →"
              + " feasible={}",
          format(diagonalStep()),
          movable.length,
          Arrays.toString(movable),
          format(diagonal),
          diagonalEval.feasible());
      if (diagonalEval.feasible()) {
        lambdas = diagonal;
        current = diagonalEval;
        logger.info(
            "Diagonal step accepted — the sweep had stalled on a corner, not on the boundary."
                + " Re-running the sweep would reclaim more.");
      } else {
        // Deliberately not phrased as "we are on the boundary": one probe of one length cannot
        // establish that, and reading it that way is what made the FH LEO run look conclusive.
        logger.info(
            "Diagonal step refused — no diagonal slack beyond the bisection's own resolution ({}"
                + " on the λ axis). A finer corner, or one whose feasible direction needs a"
                + " coordinate pinned at the heuristic load, stays invisible to a single probe.",
            format(diagonalStep()));
      }
    }

    return finish(lambdas, evaluations, passes, current, heuristicLoads, lambdaScaled);
  }

  private Result finish(
      double[] lambdas,
      int evaluations,
      int passes,
      PropellantLoadOptimizer.Evaluation best,
      double[] heuristicLoads,
      boolean[] lambdaScaled) {
    double heuristicScaled = scaledMass(null, heuristicLoads, lambdaScaled);
    double finalScaled = scaledMass(lambdas, heuristicLoads, lambdaScaled);
    logger.info(
        "MultiStageLoadOptimizer done: λ*={} after {} evals over {} pass(es);"
            + " scaled-stage mass {} → {} kg (−{} kg, −{}%)",
        format(lambdas),
        evaluations,
        passes,
        Math.round(heuristicScaled),
        Math.round(finalScaled),
        Math.round(heuristicScaled - finalScaled),
        String.format(
            Locale.ROOT,
            "%.1f",
            heuristicScaled > 0 ? 100.0 * (1.0 - finalScaled / heuristicScaled) : 0.0));
    return new Result(true, lambdas, evaluations, passes, best);
  }

  /** Mass carried by the scaled stages only; {@code null} lambdas means the heuristic loads. */
  private static double scaledMass(
      double[] lambdas, double[] heuristicLoads, boolean[] lambdaScaled) {
    double mass = 0.0;
    for (int i = 0; i < heuristicLoads.length; i++) {
      if (lambdaScaled[i]) {
        mass += (lambdas == null ? 1.0 : lambdas[i]) * heuristicLoads[i];
      }
    }
    return mass;
  }

  /**
   * The coordinates the diagonal probe is allowed to step: those strictly inside {@code (λmin,
   * λmax)}.
   *
   * <p>A coordinate still sitting at {@code λmax} has just been proven unable to take a single step
   * down; stepping it anyway makes the probe fail for a reason unrelated to the corner hypothesis,
   * and the probe returns one bit that cannot be attributed. One already at {@code λmin} has no room
   * left. Excluding both is what keeps a refused probe interpretable — at the price of missing a
   * corner whose feasible direction requires moving a pinned coordinate, which is why the refusal is
   * logged as "no slack found", never as "the sweep is optimal".
   */
  private int[] movableCoordinates(int[] coordinates, double[] lambdas) {
    return Arrays.stream(coordinates)
        .filter(i -> lambdas[i] < lambdaMax - 1e-12 && lambdas[i] > lambdaMin + 1e-12)
        .toArray();
  }

  private static int[] scaledCoordinatesTopDown(boolean[] lambdaScaled) {
    return java.util.stream.IntStream.range(0, lambdaScaled.length)
        .map(i -> lambdaScaled.length - 1 - i)
        .filter(i -> lambdaScaled[i])
        .toArray();
  }

  private static void logStageResiduals(PropellantLoadOptimizer.Evaluation evaluation) {
    if (evaluation.result() == null) {
      return;
    }
    for (StagePropellant sp : evaluation.result().performanceReport().stagePropellants()) {
      logger.info(
          "  stage {} residual: {} kg of {} kg loaded ({}%)",
          sp.stageIndex(),
          Math.round(sp.residual()),
          Math.round(sp.loaded()),
          String.format(Locale.ROOT, "%.1f", 100.0 * sp.residualRatio()));
    }
  }

  private static String format(double lambda) {
    return String.format(Locale.ROOT, "%.4f", lambda);
  }

  private static String format(double[] lambdas) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < lambdas.length; i++) {
      if (i > 0) sb.append(", ");
      sb.append(format(lambdas[i]));
    }
    return sb.append(']').toString();
  }

  /**
   * Presents one coordinate of the load vector to the scalar bisection: it substitutes the
   * candidate {@code λ} at {@code index}, leaves the other coordinates at their current value, and
   * tags the resulting evaluation with the coordinate's own {@code λ} so the bisection can bracket
   * on it.
   */
  private static final class CoordinateAdapter implements PropellantLoadOptimizer.Evaluator {
    private final Evaluator delegate;
    private final double[] lambdas;
    private final int index;

    CoordinateAdapter(Evaluator delegate, double[] lambdas, int index) {
      this.delegate = delegate;
      this.lambdas = lambdas;
      this.index = index;
    }

    @Override
    public PropellantLoadOptimizer.Evaluation evaluate(
        double lambda, PropellantLoadOptimizer.Evaluation previous) {
      double[] candidate = lambdas.clone();
      candidate[index] = lambda;
      PropellantLoadOptimizer.Evaluation evaluation = delegate.evaluate(candidate, previous);
      return new PropellantLoadOptimizer.Evaluation(
          lambda, evaluation.feasible(), evaluation.result());
    }
  }
}
