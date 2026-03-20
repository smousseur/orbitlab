package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import org.hipparchus.optim.PointValuePair;
import org.junit.jupiter.api.Test;

class AdaptiveConvergenceCheckerTest {

  private static PointValuePair pair(double value) {
    return new PointValuePair(new double[] {0.0}, value);
  }

  /** Calls converged() n times with the given cost value (both previous and current). */
  private static void burn(AdaptiveConvergenceChecker checker, int n, double cost) {
    PointValuePair p = pair(cost);
    for (int i = 0; i < n; i++) {
      checker.converged(i, p, p);
    }
  }

  @Test
  void doesNotConverge_beforeMinIterations() {
    AdaptiveConvergenceChecker checker = new AdaptiveConvergenceChecker(false, 0.1, 1e-6, 1e-6);
    // Even with zero diff and cost below acceptable, never converge before 100 calls
    for (int i = 0; i < 99; i++) {
      assertFalse(
          checker.converged(i, pair(0.0), pair(0.0)),
          "Should not converge at iterationCount=" + (i + 1));
    }
  }

  @Test
  void earlyKill_activates_after300IterationsWithHighCost() {
    AdaptiveConvergenceChecker checker = new AdaptiveConvergenceChecker(true, 0.1, 1e-6, 1e-6);
    PointValuePair bad = pair(2.0); // cost > BAD_BASIN_KILL_THRESHOLD (1.0)
    // Need iterationCount > 300 to trigger early kill
    burn(checker, 301, 2.0);
    assertTrue(checker.converged(301, bad, bad), "Early kill should activate after 301 iterations with bad cost");
  }

  @Test
  void earlyKill_disabled_doesNotKillAfter300() {
    AdaptiveConvergenceChecker checker = new AdaptiveConvergenceChecker(false, 0.1, 1e-6, 1e-6);
    PointValuePair bad = pair(2.0);
    burn(checker, 301, 2.0);
    // cost > acceptable (0.1), iterationCount (302) < 500 → no convergence
    assertFalse(checker.converged(301, bad, bad), "Without earlyKill, should not terminate at 302");
  }

  @Test
  void doesNotConverge_prematurely_whenCostAboveAcceptable_before500() {
    AdaptiveConvergenceChecker checker = new AdaptiveConvergenceChecker(false, 0.1, 1e-6, 1e-6);
    PointValuePair mediocre = pair(0.5); // cost > acceptable (0.1)
    // Reach 101 iterations (past the 100 min)
    burn(checker, 100, 0.5);
    // At 101st call: cost=0.5 > 0.1 and iterationCount=101 < 500 → false
    assertFalse(checker.converged(100, mediocre, mediocre),
        "Should not converge when cost > acceptable and iters < 500");
  }

  @Test
  void converges_whenCostBelowAcceptable_andSmallImprovement() {
    AdaptiveConvergenceChecker checker = new AdaptiveConvergenceChecker(false, 0.1, 1e-6, 1e-6);
    PointValuePair good = pair(0.05); // cost < acceptable (0.1)
    // Reach 100 iterations
    burn(checker, 100, 0.05);
    // At 101st call: diff=0 <= absoluteTolerance → converge
    assertTrue(checker.converged(100, good, good),
        "Should converge when cost < acceptable and diff <= absoluteTolerance");
  }

  @Test
  void converges_via_relativeImprovement() {
    AdaptiveConvergenceChecker checker = new AdaptiveConvergenceChecker(false, 0.1, 0.0, 0.01);
    PointValuePair good = pair(0.05);
    burn(checker, 100, 0.05);
    // diff=0, 0 <= 0.01 * 0.05 = 5e-4 → converge
    assertTrue(checker.converged(100, good, good));
  }

  @Test
  void doesNotConverge_whenImprovementAboveTolerance() {
    AdaptiveConvergenceChecker checker = new AdaptiveConvergenceChecker(false, 0.1, 1e-6, 1e-6);
    // Reach 100 iterations with cost below acceptable
    burn(checker, 100, 0.05);
    // Large diff between previous (0.5) and current (0.05): 0.45 >> 1e-6
    assertFalse(checker.converged(100, pair(0.5), pair(0.05)),
        "Should not converge when diff is above tolerance");
  }

  @Test
  void earlyKill_requiresCostAboveThreshold() {
    AdaptiveConvergenceChecker checker = new AdaptiveConvergenceChecker(true, 0.1, 1e-6, 1e-6);
    // Run past 300 iters but with cost BELOW the bad-basin threshold (1.0)
    burn(checker, 301, 0.5); // 0.5 < 1.0
    // Should NOT early-kill; cost (0.5) > acceptable (0.1), iters (302) < 500 → false
    assertFalse(checker.converged(301, pair(0.5), pair(0.5)),
        "Early kill should not activate when cost is below bad-basin threshold");
  }
}
