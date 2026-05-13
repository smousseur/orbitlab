package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import org.hipparchus.util.FastMath;

/**
 * Per-mission tuning of the {@link TransferProblem} CMA-ES search. Encapsulates the parameters that
 * legitimately differ between problem classes (LEO insertion vs GTO/GEO transfer) so a single
 * {@code TransferProblem} implementation can be parameterized for either without code duplication.
 *
 * <p>The {@link #defaults()} factory reproduces the historical LEO-tuned values exactly.
 *
 * @param dt1MaxMultiplier upper-bound multiplier on the analytical {@code guessDt1} for burn 1
 *     duration; the effective bound also respects propellant availability
 * @param t1MaxPeriodFraction upper bound on burn 1 start offset {@code t1}, expressed as a fraction
 *     of the initial orbit period
 * @param alphaMaxRad symmetric bound on the in-plane thrust angle {@code α1} (rad)
 * @param betaMaxRad hard cap on the out-of-plane thrust angle bound {@code β1} (rad); applied on
 *     top of the historical π/8 floor + adaptive growth — set to a large value (≥ π/2) to leave
 *     the historical floor as the active constraint, or to a small value to tighten {@code β1}
 *     across the whole exploration
 * @param acceptableCost convergence threshold below which CMA-ES retries stop firing
 * @param failureBaseCost base penalty value applied when the propagation cannot grade the candidate
 *     (e.g. burn 1 catastrophically failed); kept at 1e3 to dominate any nominal cost while
 *     remaining finite
 * @param failureWeightA gradient weight on relative semi-major-axis error in the failure penalty
 * @param failureWeightE gradient weight on post-burn-1 eccentricity in the failure penalty
 * @param failureWeightI gradient weight on post-burn-1 inclination drift (rad²) in the failure
 *     penalty; nonzero values let CMA-ES feel β1 changes even when Step 3 is skipped
 * @param failFast envelope used by {@code TransfertTwoManeuver} to skip Step 3 for hopeless
 *     candidates
 */
public record TransferTuning(
    double dt1MaxMultiplier,
    double t1MaxPeriodFraction,
    double alphaMaxRad,
    double betaMaxRad,
    double acceptableCost,
    double failureBaseCost,
    double failureWeightA,
    double failureWeightE,
    double failureWeightI,
    FailFastEnvelope failFast) {

  /** Historical LEO-tuned values. */
  public static TransferTuning defaults() {
    return new TransferTuning(
        4.0,
        0.5,
        FastMath.PI / 2.0,
        FastMath.PI / 2.0,
        3e-3,
        1000.0,
        50.0,
        50.0,
        0.0,
        FailFastEnvelope.defaults());
  }
}
