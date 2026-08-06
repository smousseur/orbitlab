package com.smousseur.orbitlab.simulation.mission;

/**
 * How much effort a mission's trajectory computation invests. The mode drives two independent axes:
 * the stage composition (analytic vs. CMA-ES, resolved by {@code MissionComposer}) and the
 * propellant-load handling (fixed vs. minimized, resolved by {@code MissionPlanOptimizer}).
 *
 * <p><b>What the effort actually buys is propellant, not orbit accuracy.</b> Measured 2026-08-06 on
 * one and the same launcher, payload and 400 km circular target (Falcon Heavy budget loads), the
 * only difference between the runs being the transfer stage:
 *
 * <table border="1">
 *   <caption>FAST vs BALANCED, identical ascent to the kilogram</caption>
 *   <tr><th></th><th>analytic (FAST)</th><th>CMA-ES (BALANCED)</th></tr>
 *   <tr><td>transfer</td><td>1 828 kg / 415 m/s</td><td>1 088 kg / 241 m/s</td></tr>
 *   <tr><td>final mass</td><td>14 112 kg</td><td>14 855 kg (+743 kg, +5.3 %)</td></tr>
 *   <tr><td>upper-stage residual</td><td>112 kg (5.7 %)</td><td>855 kg (43.6 %)</td></tr>
 * </table>
 *
 * <p><b>On orbit accuracy the ordering is reversed</b>, which is why none of these names should be
 * read as a precision gradient. On a 600 km circular target the analytic profile flies a worst-case
 * deviation of 767 m against the CMA-ES profile's 1 053 m — negligible either way. On an elliptic
 * 600/800 target it is not negligible: the analytic profile reaches 800 562 m of apogee against the
 * CMA-ES profile's 783 725 m, a 16 km miss, because an elliptic target is flown by the single-burn
 * {@code TransfertManeuverStage}, which grades apogee, perigee and eccentricity in one aggregate
 * cost and therefore trades between them. <b>Whether that single-burn path buys any propellant back
 * has never been measured</b>; the +743 kg above validates the two-burn circular path only.
 *
 * <p>The propellant gain also compounds with the load minimization {@code PRECISE} adds: the
 * analytic profile leaves 5.7 % of residual on the upper stage, the CMA-ES one 43.6 %, and that
 * residual is exactly the margin the λ campaign then reclaims.
 *
 * <p>Cost of the effort: 3 to 9× the wall clock of the analytic profile per mission, and the factor
 * multiplies inside the propellant loop, where every λ evaluation is a full mission optimization.
 *
 * <p>Kept at the mission root (rather than nested in {@code MissionEntry}) so both the {@code
 * context} and {@code operation} packages can depend on it without a package cycle.
 */
public enum OptimizationType {
  /**
   * Closed-form analytic profile, fixed loads. Fastest, deterministic (no CMA-ES seed, hence the
   * reference the other two are compared against), historical default — and, on a circular target,
   * the one that leaves ~5 % of the payload mass on the table.
   */
  FAST,
  /** CMA-ES optimized transfer at fixed loads. Buys the propellant, keeps the loads as given. */
  BALANCED,
  /**
   * CMA-ES optimized transfer with propellant-load minimization. Slowest, and the most
   * <em>efficient</em> — not the most accurate: see the class javadoc for why the two are not the
   * same axis here.
   */
  PRECISE
}
