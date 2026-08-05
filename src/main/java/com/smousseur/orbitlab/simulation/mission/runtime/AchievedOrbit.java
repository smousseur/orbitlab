package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.OrbitElements;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;

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
 * eccentricity of order {@code f = (3/2)*J2*(RE/a)^2}: the two cannot be circular at the same
 * time. Measured 2026-08-05 on a 400 km insertion aimed circular: osculating 400 000 x 400 114 m,
 * mean 390 612 x 409 712 m. Those ~9.4 km are <b>not</b> an insertion miss, and a UI showing only
 * the mean orbit would make perfect targeting look like a failure.
 *
 * <p>Either component may be {@code null} when the corresponding reading was unavailable — see
 * {@link #of(SpacecraftState)}. Read them through {@link #hasOsculating()} / {@link #hasMean()},
 * or through the formatting helpers.
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
   */
  public static AchievedOrbit of(SpacecraftState state) {
    try {
      KeplerianOrbit orbit =
          new KeplerianOrbit(
              state.getPVCoordinates(),
              state.getFrame(),
              state.getDate(),
              Constants.WGS84_EARTH_MU);
      return new AchievedOrbit(
          OrbitElements.osculating(orbit), OrbitElements.mean(orbit).orElse(null));
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
