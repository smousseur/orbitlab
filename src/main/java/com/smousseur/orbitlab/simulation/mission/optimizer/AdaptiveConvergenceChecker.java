package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.hipparchus.optim.ConvergenceChecker;
import org.hipparchus.optim.PointValuePair;
import org.hipparchus.util.FastMath;

/**
 * Convergence checker for CMA-ES runs with adaptive early-kill logic.
 *
 * <ul>
 *   <li>Never converges before 100 iterations.
 *   <li>If {@code earlyKill} is enabled, aborts runs stuck above the bad-basin threshold after 300
 *       iterations.
 *   <li>Does not converge prematurely if acceptable cost has not been reached and fewer than 500
 *       iterations have run.
 *   <li>Standard convergence: cost improvement below tolerance thresholds.
 * </ul>
 */
final class AdaptiveConvergenceChecker implements ConvergenceChecker<PointValuePair> {

  private static final int MIN_ITERS_BEFORE_CONVERGE = 100;
  private static final int BAD_BASIN_MIN_ITERS = 300;
  private static final double BAD_BASIN_KILL_THRESHOLD = 1.0;

  private final boolean earlyKill;
  private final double acceptableCost;
  private final double absoluteTolerance;
  private final double relativeTolerance;

  private int iterationCount = 0;

  /**
   * Creates a new adaptive convergence checker.
   *
   * @param earlyKill whether to enable early termination of runs stuck in bad basins
   * @param acceptableCost cost threshold below which a solution is considered acceptable
   * @param absoluteTolerance absolute improvement threshold for convergence
   * @param relativeTolerance relative improvement threshold for convergence
   */
  AdaptiveConvergenceChecker(
      boolean earlyKill,
      double acceptableCost,
      double absoluteTolerance,
      double relativeTolerance) {
    this.earlyKill = earlyKill;
    this.acceptableCost = acceptableCost;
    this.absoluteTolerance = absoluteTolerance;
    this.relativeTolerance = relativeTolerance;
  }

  @Override
  public boolean converged(int iteration, PointValuePair previous, PointValuePair current) {
    iterationCount++;

    // Never converge too early
    if (iterationCount < MIN_ITERS_BEFORE_CONVERGE) return false;

    // Early kill: abort runs stuck in bad basins
    if (earlyKill
        && iterationCount > BAD_BASIN_MIN_ITERS
        && current.getValue() > BAD_BASIN_KILL_THRESHOLD) {
      return true;
    }

    // Don't converge prematurely if we haven't reached acceptable cost yet
    if (current.getValue() > acceptableCost && iterationCount < 500) {
      return false;
    }

    // Standard convergence: cost stopped improving
    double diff = FastMath.abs(previous.getValue() - current.getValue());
    return diff <= absoluteTolerance
        || diff <= relativeTolerance * FastMath.abs(current.getValue());
  }
}
