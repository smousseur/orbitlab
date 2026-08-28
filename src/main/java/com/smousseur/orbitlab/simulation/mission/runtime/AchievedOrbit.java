package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.OrbitElements;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;

/**
 * The orbit a mission achieved, in the two conventions that both make sense — and that do not say
 * the same thing.
 *
 * <p><b>Osculating</b>: the instantaneous orbit at the point considered. This is a launcher's
 * accuracy convention, and it is what demonstrates targeting quality — the analytic stages aim at
 * it and hit it to the metre.
 *
 * <p><b>Mean</b>: the orbit stripped of its short-period terms. This is a mission orbit's
 * convention, the one you compare against a TLE or against a requirement stated in mean elements.
 *
 * <p><b>Trap not to reproduce in the UI.</b> An instantaneously circular orbit has a <em>mean</em>
 * eccentricity of order {@code f = (3/2)*J2*(RE/a)^2}: the two cannot be circular at the same time.
 * Measured 2026-08-05 on a 400 km insertion aimed circular: osculating 400 000 x 400 114 m, mean
 * 390 612 x 409 712 m. Those ~9.4 km are <b>not</b> an insertion miss, and a UI showing only the
 * mean orbit would make perfect targeting look like a failure.
 *
 * <p>Either component may be {@code null} when the corresponding reading was unavailable — see
 * {@link #of(SpacecraftState)}. Read them through {@link #hasOsculating()} / {@link #hasMean()}, or
 * through the formatting helpers.
 *
 * @param osculating the osculating elements, or {@code null} if even that reading failed
 * @param mean the mean elements, or {@code null} if the conversion did not converge
 */
public record AchievedOrbit(OrbitElements osculating, OrbitElements mean) {

  private static final Logger logger = LogManager.getLogger(AchievedOrbit.class);

  private static final String UNAVAILABLE_TEXT = "unavailable";

  /** The report that could not be established at all: neither convention is available. */
  public static final AchievedOrbit UNAVAILABLE = new AchievedOrbit(null, null);

  /**
   * Reads both conventions off {@code state}, propagating and mutating nothing.
   *
   * <p><b>Never throws.</b> This is not decorative caution: {@code MissionOptimizer} calls this
   * method in the middle of {@code optimize()}, and {@code MissionLoadEvaluator} turns any {@code
   * RuntimeException} escaping {@code optimize()} into "lambda infeasible". An exception thrown
   * from here would therefore move the lambda retained by the propellant-sizing sweep — a mission
   * number shifted by a reporting line, with no error surfaced anywhere. That is exactly what the
   * spec's invariant (orbit-reporting/01 section 3.4) forbids, and it must be guaranteed
   * <b>here</b>, at the boundary, rather than resting on the fact that nothing throws today.
   *
   * @param state the state to report
   * @param referenceRadius the equatorial radius the apsides are counted from (m), read off the
   *     gravitational context of the stage that flew this arc (MIS-5 / L2, spec {@code
   *     docs/lunar-orbit/04-conception-L2.md} §3.2)
   */
  public static AchievedOrbit of(SpacecraftState state, double referenceRadius) {
    // The µ comes off the state's own orbit, not from an Earth constant (PHY-4 / L6, spec
    // docs/multi-corps/08-conception-L6.md §5.1). createOptimizationPropagator does
    // setOrbitType(CARTESIAN) then setMu(context.mu()), so the propagated state already carries the
    // µ of the body it was flown around: an orbit achieved around the Moon is reported against the
    // lunar µ, and every terrestrial mission keeps the very same double, since
    // GravitationalContext.earth() IS Constants.WGS84_EARTH_MU. The non-regression is therefore an
    // identity of the constant, not a measurement — AnalyticGtoInjectionStage already reads its µ
    // this way.
    //
    // Note for whoever touches the other µ: this one is the PROPAGATOR's, while
    // OrbitElements.mean() deliberately rebases on the potential provider's. Mixing the two shifts
    // the elements by about a metre, which reads as J2 (spec orbit-reporting/01 §3.3) — so a single
    // "central body µ" must not be made to serve both, and making this one contextual does not make
    // that one contextual.
    //
    // The radius comes from the caller and the µ from the state, and the asymmetry is deliberate
    // (MIS-5 / L2 §3.2): the µ is what the integrator integrated, the radius is what a reader
    // counts an altitude from. Two questions, not two answers to one — which is also why this
    // signature takes a double and not a GravitationalContext: a context would put context.mu()
    // within reach of the very line above that must not read it.
    try {
      KeplerianOrbit orbit =
          new KeplerianOrbit(
              state.getPVCoordinates(),
              state.getFrame(),
              state.getDate(),
              state.getOrbit().getMu());
      return new AchievedOrbit(
          OrbitElements.osculating(orbit, referenceRadius),
          OrbitElements.mean(orbit, referenceRadius).orElse(null));
    } catch (RuntimeException e) {
      logger.debug(
          "Achieved orbit unavailable ({}): {}", e.getClass().getSimpleName(), e.getMessage());
      return UNAVAILABLE;
    }
  }

  public boolean hasOsculating() {
    return osculating != null;
  }

  public boolean hasMean() {
    return mean != null;
  }

  /** The osculating line as logged, or {@code "unavailable"}. */
  public String formatOsculating() {
    return format(osculating);
  }

  /** The mean line as logged, or {@code "unavailable"}. */
  public String formatMean() {
    return format(mean);
  }

  private static String format(OrbitElements elements) {
    return elements == null ? UNAVAILABLE_TEXT : elements.format();
  }
}
