package com.smousseur.orbitlab.engine.scene.spacecraft;

/**
 * The shape and tint of the plasma around a re-entering piece, read off its air-relative speed and
 * its altitude: the uniforms the user's ray-marched prototype computed on the CPU, in its {@code
 * physics()}, ported as they are but for its radiative intensity, which the re-entry heat profile
 * gives instead.
 *
 * <p>The shock is Billig's detached bow shock, {@code x_s(r) = xA − K·(√(1 + A·r²) − 1)} in body
 * radii, ahead of the nose at {@code xA}, of nose curvature radius {@code rc} and asymptotic angle
 * a little wider than the Mach angle. The temperature scales the colour ramp of the hot gas, and
 * the violet share is the non-equilibrium emission of a thin, fast, high flow.
 *
 * <p>The prototype's formulas were calibrated by eye between 45 and 95 km; a falling launcher piece
 * glows from 90 km down to a few kilometres and peaks between 20 and 25 km. They are kept as they
 * are below their range: the free-stream temperature is extrapolated there, and every output stays
 * bounded. Not physics: a look, set to read plausibly.
 *
 * @param xA where the shock's apex stands, ahead of the nose, in body radii
 * @param k the shock's {@code Rc·cot²β}, in body radii
 * @param a the shock's {@code tan²β / Rc²}, per square body radius
 * @param rc the shock's nose curvature radius, in body radii
 * @param violet the share of violet emission, in {@code [0, 1]}
 * @param temperature the scale of the hot gas colour ramp, in {@code [0.5, 1.15]}
 */
public record ReentryPlasmaShape(
    float xA, float k, float a, float rc, float violet, float temperature) {

  /**
   * The lowest Mach number the shock is drawn for. Under Mach 1 the Mach angle {@code asin(1/M)}
   * does not exist; a piece is far below its glow long before that, the lowest Mach measured while
   * one glows being 3.1, so this is a numerical guard and never a look.
   */
  static final double MIN_MACH = 1.5;

  /** The prototype's shock nose curvature radius, in body radii. */
  private static final double SHOCK_NOSE_RADIUS = 1.5;

  /** How much wider than the Mach angle the prototype draws the shock's asymptote. */
  private static final double ASYMPTOTE_WIDENING = 1.15;

  /**
   * The plasma at a speed and an altitude.
   *
   * @param speedMs the air-relative speed, in m/s
   * @param altitudeM the altitude, in metres
   * @param soundSpeedMs the speed of sound the Mach number is taken against, in m/s
   * @return the shape and tint
   */
  public static ReentryPlasmaShape of(double speedMs, double altitudeM, double soundSpeedMs) {
    double mach = Math.max(MIN_MACH, speedMs / soundSpeedMs);
    double altitudeKm = altitudeM / 1000.0;
    double tanBeta = Math.tan(Math.asin(1.0 / mach)) * ASYMPTOTE_WIDENING;
    double tanBetaSquared = tanBeta * tanBeta;
    double dissociation = 1.0 - 0.35 * smoothstep(8.0, 20.0, mach);
    double xA = 0.5 + 0.35 * Math.exp(3.24 / (mach * mach)) * dissociation;
    double freeStream = Math.max(185.0, 247.0 - 2.4 * (altitudeKm - 60.0));
    double ideal = freeStream * (1.0 + 0.2 * mach * mach);
    double real = ideal / (1.0 + Math.pow(ideal / 12_000.0, 1.2));
    double violet = clamp(0.25 + (altitudeKm - 60.0) / 40.0 + (mach - 8.0) / 40.0, 0.0, 1.0);
    double temperature = clamp(0.5 + 0.65 * real / 8_000.0, 0.5, 1.15);
    return new ReentryPlasmaShape(
        (float) xA,
        (float) (SHOCK_NOSE_RADIUS / tanBetaSquared),
        (float) (tanBetaSquared / (SHOCK_NOSE_RADIUS * SHOCK_NOSE_RADIUS)),
        (float) SHOCK_NOSE_RADIUS,
        (float) violet,
        (float) temperature);
  }

  private static double smoothstep(double edge0, double edge1, double x) {
    double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
  }

  private static double clamp(double value, double low, double high) {
    return Math.min(high, Math.max(low, value));
  }
}
