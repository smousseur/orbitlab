package com.smousseur.orbitlab.simulation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.Orbit;
import org.orekit.utils.Constants;

/**
 * Resolves the shaping radius an analytic burn must aim at so the <b>flown</b> altitude band is
 * centred on the requested orbit (spec orbit-reporting/02).
 *
 * <p>The width of that band is not a choice: under J2 the osculating semi-major axis and
 * eccentricity oscillate in phase with the same relative amplitude {@code f = (3/2)·J2·(RE/a)²},
 * which adds up at perigee and cancels at apogee. No orbit is flat. Only the <em>centring</em> is a
 * choice, and it is worth a factor of two on the worst-case deviation.
 *
 * <p><b>Why the mean semi-major axis, and not the mean perigee.</b> The first version of this class
 * targeted the mean perigee, on the measured identity "mean perigee = centre of the perigee
 * excursion". It halved the worst case — but it did not centre anything: measured on the long
 * suites 2026-08-05, a 600 km circular request flew 590 336 → 600 599 m, i.e. the request still
 * sitting at the <em>top</em> of the band, exactly as before. The whole gain came from the mean
 * eccentricity halving, which narrowed the band rather than moving it.
 *
 * <p>The same logs gave the relation that was missing. On both profiles the flown radii sit about
 * 9.1 km <em>below</em> the mean-element apsides:
 *
 * <pre>
 *   600/600 : mean 600 000 x 609 403  ->  flown 590 336 x 600 599
 *   600/800 : mean 600 000 x 809 334  ->  flown 590 576 x 800 563
 * </pre>
 *
 * so the flown band's centre is {@code mean semi-major axis − a·f}, to within 230 m on both. The
 * criterion is therefore {@code mean a = requested a + a·f}. It is also the better-posed target for
 * a burn with a single degree of freedom: the speed at the burn point sets the energy, and the
 * energy <em>is</em> the semi-major axis.
 *
 * <p><b>What the returned value is, and is not.</b> It is the shaping parameter of {@code
 * AnalyticHohmannTransferStage#computeTargetVelocityAtApogee}, which enters only through {@code a =
 * (aim + apsisRadius)/2}. It is <b>not</b> a prediction of the achieved osculating perigee: on a
 * near-circular target the aim exceeds the burn-point radius, so the burn point stays the perigee
 * and the aim is the far apside (spec 02 section 3.1). The name says what it steers, not what comes
 * out of it.
 *
 * <p><b>Total by construction.</b> {@link OrbitElements#mean(Orbit)} returns an {@code Optional}
 * because a fixed point may fail to converge, and <b>no mission may fail because a centring
 * refinement did not converge</b> — the mission flies perfectly well on the un-centred aim. An
 * empty mean therefore falls back on the closed form rather than throwing. That is what keeps this
 * targeting path free of any new failure mode.
 */
public final class FlownBandAim {
  private static final Logger logger = LogManager.getLogger(FlownBandAim.class);

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double J2 = -Constants.WGS84_EARTH_C20;

  /**
   * Fixed-point steps. The aim moves the semi-major axis at half its own rate ({@code a = (aim +
   * apsis)/2}), which the correction below compensates, so the iteration lands in one or two steps
   * from the closed-form seed. The budget covers the eccentric profiles where the seed is furthest
   * off.
   */
  private static final int MAX_ITERATIONS = 5;

  /**
   * Residual below which the aim counts as converged (m). Well under Eckstein-Hechler's own ~600 m
   * modelling residual (spec 01 section 3.2.2): iterating past the model's noise buys nothing.
   */
  private static final double CONVERGENCE_M = 10.0;

  private FlownBandAim() {}

  /**
   * The shaping radius to aim at so the flown altitude band is centred on {@code
   * targetBandCentreRadius}.
   *
   * @param targetBandCentreRadius the radius the flown band should be centred on (m from the
   *     Earth's centre) — for a circular target, the requested orbit radius; for an elliptic one,
   *     the mid-point of the requested apsides
   * @param apsisRadius the burn-point radius, i.e. the apside the burn does <em>not</em> move (m)
   * @param aimedOrbit maps a candidate shaping radius to the orbit that aim would produce; called a
   *     handful of times, and must not propagate
   * @return the shaping radius to hand the burn planner (m)
   */
  public static double resolve(
      double targetBandCentreRadius, double apsisRadius, DoubleFunction<Orbit> aimedOrbit) {
    Objects.requireNonNull(aimedOrbit, "aimedOrbit");
    double targetMeanA = targetBandCentreRadius + closedFormOffset(targetBandCentreRadius);
    // Seed: the aim whose OSCULATING a already equals the target mean a. Since a = (aim + apsis)/2,
    // that is aim = 2*targetMeanA − apsis.
    double fallback = 2.0 * targetMeanA - apsisRadius;

    double aim = fallback;
    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      Optional<OrbitElements> mean = OrbitElements.mean(aimedOrbit.apply(aim));
      if (mean.isEmpty()) {
        logger.debug(
            "Flown-band aim: mean orbit unavailable at iteration {}; falling back on the"
                + " closed-form seed (mean a target {} m).",
            iteration,
            targetMeanA);
        return fallback;
      }
      double residual = targetMeanA - mean.get().semiMajorAxis();
      // The aim moves a at half its own rate, hence the factor 2.
      aim += 2.0 * residual;
      if (FastMath.abs(residual) < CONVERGENCE_M) {
        return aim;
      }
    }
    logger.debug(
        "Flown-band aim: {} iterations without settling under {} m; flying the last aim ({} m).",
        MAX_ITERATIONS,
        CONVERGENCE_M,
        aim);
    return aim;
  }

  /**
   * The offset {@code a·f = (3/2)·J2·RE²/a} between the mean-element apsides and the radii actually
   * flown — 9 746 m at 400 km, 9 467 m at 600 km, 1 567 m at GEO.
   *
   * <p>Measured against the long suites 2026-08-05 it over-predicts by ~230 m on both profiles (9
   * 234 and 9 097 m observed). That residual is 5 % of the ~4.9 km worst case this centring leaves,
   * and it is deliberately <b>not</b> calibrated away: fitting a correction on two points would buy
   * 230 m of accuracy at the price of a constant nobody could re-derive.
   *
   * <p>Because {@code f} varies as {@code (RE/a)²}, the whole centring is a LEO concern: what is
   * worth 9.7 km at 400 km is worth 1.6 km at geostationary altitude.
   *
   * @param semiMajorAxis the orbit's semi-major axis (m)
   * @return the offset between the mean apsides and the flown radii (m)
   */
  public static double closedFormOffset(double semiMajorAxis) {
    return 1.5 * J2 * RE * RE / semiMajorAxis;
  }
}
