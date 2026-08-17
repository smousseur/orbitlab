package com.smousseur.orbitlab.simulation.gravity;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * The sphere of influence of a body about its primary, in the Laplace sense: the radius at which
 * the body's attraction takes over from its primary's as the dominant one.
 *
 * <p>Introduced by PHY-4 / L4 (spec {@code docs/multi-corps/06-conception-L4.md} §3.2). It answers
 * the découpage's open question 2 — geometric Laplace sphere rather than a force ratio. The force
 * ratio would be more faithful, but since a switch derives the opposite body as a perturber on both
 * sides (spec L4 §4.2), the two sides are the same physics to 0.246 m over six hours and the radius
 * only decides where the <em>bookkeeping</em> flips.
 *
 * <p><b>The radius breathes, and that is measured, not stylistic.</b> The découpage quotes a single
 * 66 200 km for the Moon. That is the value at the mean Earth-Moon distance; over 400 days from
 * 2026-03-01 the real distance runs 356 779 → 406 570 km and the Laplace radius with it, <b>61 427
 * → 70 000 km</b>. Writing a constant would be writing that a 14% variation is ignored, and the
 * marginal cost of the true value is nil: the body's ephemeris is interpolated anyway to evaluate
 * the distance to it.
 *
 * @param body the body whose sphere this is — the Moon
 * @param primary the body it orbits — the Earth
 */
public record SphereOfInfluence(SolarSystemBody body, SolarSystemBody primary) {

  /** Laplace exponent: the sphere is {@code d · (m/M)^(2/5)}. */
  private static final double LAPLACE_EXPONENT = 0.4;

  public SphereOfInfluence {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(primary, "primary");
    if (body == primary) {
      throw new IllegalArgumentException("a body is not its own primary: " + body);
    }
  }

  /**
   * The sphere of influence of a body about the primary it orbits, read from {@link
   * SolarSystemBody#parent()}.
   *
   * @param body the body whose sphere is wanted; must have a parent
   * @return the sphere of influence
   */
  public static SphereOfInfluence of(SolarSystemBody body) {
    Objects.requireNonNull(body, "body");
    SolarSystemBody parent = body.parent();
    if (parent == null) {
      throw new IllegalArgumentException(body + " has no primary and therefore no SOI");
    }
    return new SphereOfInfluence(body, parent);
  }

  /**
   * The mass ratio factor {@code (m/M)^(2/5)}, computed from Orekit's gravitational parameters
   * rather than written down. Measured 0.17217202 for the Moon about the Earth.
   *
   * @return the dimensionless Laplace factor
   */
  public double laplaceFactor() {
    double gmBody = OrekitService.get().body(body).getGM();
    double gmPrimary = OrekitService.get().body(primary).getGM();
    return Math.pow(gmBody / gmPrimary, LAPLACE_EXPONENT);
  }

  /**
   * The radius of the sphere at the given date, from the instantaneous separation of the two
   * bodies.
   *
   * @param date the date at which the separation is evaluated
   * @return the sphere radius in meters
   */
  public double radiusAt(AbsoluteDate date) {
    return separationAt(date) * laplaceFactor();
  }

  /**
   * The distance between the body and its primary at the given date, evaluated in the primary's
   * ICRF-oriented frame.
   *
   * @param date the date
   * @return the separation in meters
   */
  public double separationAt(AbsoluteDate date) {
    return OrekitService.get()
        .body(body)
        .getPosition(date, OrekitService.get().bodyCentredIcrfFrame(primary))
        .getNorm();
  }
}
