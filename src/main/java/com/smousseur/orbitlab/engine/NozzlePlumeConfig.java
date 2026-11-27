package com.smousseur.orbitlab.engine;

import java.util.Map;
import java.util.Objects;

/**
 * Tuning of the exhaust plumes drawn at a launcher's firing nozzles.
 *
 * <p>Each cluster's plume is a cone marched in the shader, in cells {@code stepRadii} local jet
 * radii long across the jet and longer along it, at most {@code maxSteps} in each stretch of a ray.
 * Its shape is blended from the look's sea-level shape to its vacuum shape as the air thins, read
 * off a drawing atmosphere — {@code p / p0 = exp(−h / scaleHeightM)} at the object's altitude — and
 * not off the mission's own, which a mission flown without drag does not have: the plume opens from
 * where the pressure has fallen to {@code expansionStartRatio} of the ground's, and has its vacuum
 * shape from where it has fallen to {@code vacuumRatio}. Ignition and cut-off ramp over {@code
 * rampSeconds} of simulated time.
 *
 * @param looks the tuning of every {@link PlumeLook}
 * @param stepRadii the length of a ray's cells across the jet, in local jet radii (finite,
 *     positive)
 * @param maxSteps the most cells in a stretch of a ray (at least 1)
 * @param scaleHeightM the drawing atmosphere's scale height, in metres (finite, positive)
 * @param expansionStartRatio the pressure ratio below which the plume starts to open, in {@code (0,
 *     1]}
 * @param vacuumRatio the pressure ratio below which the plume has its vacuum shape, in {@code (0,
 *     expansionStartRatio)}
 * @param rampSeconds the simulated seconds a plume takes to light up and to go out (finite, at
 *     least 0)
 * @param exposure the exposure of the plume's own tone mapping: the scene has none (finite,
 *     positive)
 */
public record NozzlePlumeConfig(
    Map<PlumeLook, PlumeLookConfig> looks,
    float stepRadii,
    int maxSteps,
    double scaleHeightM,
    double expansionStartRatio,
    double vacuumRatio,
    double rampSeconds,
    float exposure) {

  public NozzlePlumeConfig {
    Objects.requireNonNull(looks, "looks");
    for (PlumeLook look : PlumeLook.values()) {
      if (!looks.containsKey(look)) {
        throw new IllegalArgumentException("no tuning for the " + look + " look");
      }
    }
    looks = Map.copyOf(looks);
    if (!Float.isFinite(stepRadii) || stepRadii <= 0f) {
      throw new IllegalArgumentException("stepRadii must be finite and > 0, got " + stepRadii);
    }
    if (maxSteps < 1) {
      throw new IllegalArgumentException("maxSteps must be at least 1, got " + maxSteps);
    }
    if (!Double.isFinite(scaleHeightM) || scaleHeightM <= 0.0) {
      throw new IllegalArgumentException(
          "scaleHeightM must be finite and > 0, got " + scaleHeightM);
    }
    if (!(expansionStartRatio > 0.0 && expansionStartRatio <= 1.0)) {
      throw new IllegalArgumentException(
          "expansionStartRatio must be in (0, 1], got " + expansionStartRatio);
    }
    if (!(vacuumRatio > 0.0 && vacuumRatio < expansionStartRatio)) {
      throw new IllegalArgumentException(
          "vacuumRatio must be in (0, expansionStartRatio), got " + vacuumRatio);
    }
    if (!Double.isFinite(rampSeconds) || rampSeconds < 0.0) {
      throw new IllegalArgumentException("rampSeconds must be finite and >= 0, got " + rampSeconds);
    }
    if (!Float.isFinite(exposure) || exposure <= 0f) {
      throw new IllegalArgumentException("exposure must be finite and > 0, got " + exposure);
    }
  }

  /**
   * The tuning of one look.
   *
   * @param look the look
   * @return its tuning
   */
  public PlumeLookConfig look(PlumeLook look) {
    return looks.get(look);
  }

  /**
   * How thin the air is at an altitude, for the plume's shape: 0 where it keeps its sea-level
   * shape, 1 where it has its vacuum shape, a smoothstep on the logarithm of the pressure in
   * between — which, for an exponential atmosphere, is a smoothstep on the altitude.
   *
   * @param altitudeM the object's altitude, in metres
   * @return the pressure factor, in {@code [0, 1]}
   */
  public float pressureFactor(double altitudeM) {
    double start = scaleHeightM * Math.log(1.0 / expansionStartRatio);
    double vacuum = scaleHeightM * Math.log(1.0 / vacuumRatio);
    double t = Math.min(1.0, Math.max(0.0, (altitudeM - start) / (vacuum - start)));
    return (float) (t * t * (3.0 - 2.0 * t));
  }

  /**
   * Creates the default plume tuning.
   *
   * <p>The shapes and colours are the starting points of the comparison mock-up the technique was
   * chosen on, to be set on screen: kerolox Merlins orange and fairly opaque, hydrolox Vulcain and
   * Vinci nearly clear and bluish, the P120C solids white-hot and the densest, shock diamonds on
   * the liquid sea-level engines only, and the vacuum engines — which only fire in vacuum — given
   * the same shape twice. The payloads' engines, which fire in orbit too, share one shape that is
   * the lightest, clearest and narrowest of all: Landsat 8's exit is its whole ring of thrusters,
   * 0.22 m in radius, and a vacuum stage's 28° opened its plume 4.7 m wide on a 3 m satellite. The
   * drawing atmosphere's scale height of 7 km is the pressure scale height of the lower atmosphere,
   * to be checked against a standard table; with the two ratios, the plume starts to open near 4.9
   * km and has its vacuum shape from about 48 km. Cells 0.35 local radius long across the jet, up
   * to 64 in a stretch of a ray, hold the dearest view measured — the camera a quarter of a stack
   * from an Ariane 64's vacuum plume, at 1280×720 — to 0.83 to 0.91 ms of GPU time, where samples
   * 0.35 exit radius apart took 9 ms inside it. A ramp of 0.3 s softens ignition and cut-off
   * without blurring them at the clock's normal rate. The exposure of 2 is what puts the exit of
   * every jet back to at least the brightness it had when the light did not thin out with the jet's
   * widening section, measured side on over black.
   *
   * @return the default plume configuration
   */
  public static NozzlePlumeConfig defaults() {
    PlumeLookConfig.Rgb keroloxCore = new PlumeLookConfig.Rgb(1.0f, 0.93f, 0.76f);
    PlumeLookConfig.Rgb keroloxEdge = new PlumeLookConfig.Rgb(1.0f, 0.45f, 0.12f);
    PlumeLookConfig.Rgb hydroloxCore = new PlumeLookConfig.Rgb(0.93f, 0.95f, 1.0f);
    PlumeLookConfig.Rgb hydroloxEdge = new PlumeLookConfig.Rgb(0.5f, 0.55f, 1.0f);
    PlumeShape mvac = new PlumeShape(26f, 28f, 0.6f, 0.12f, 0f);
    PlumeShape vinci = new PlumeShape(30f, 30f, 0.35f, 0.03f, 0f);
    PlumeShape payload = new PlumeShape(12f, 12f, 0.3f, 0.02f, 0f);
    return new NozzlePlumeConfig(
        Map.of(
            PlumeLook.MERLIN,
            new PlumeLookConfig(
                new PlumeShape(40f, 4f, 1.0f, 0.35f, 0.6f),
                new PlumeShape(30f, 32f, 0.7f, 0.12f, 0f),
                keroloxCore,
                keroloxEdge),
            PlumeLook.MVAC,
            new PlumeLookConfig(
                mvac,
                mvac,
                new PlumeLookConfig.Rgb(1.0f, 0.92f, 0.78f),
                new PlumeLookConfig.Rgb(0.85f, 0.42f, 0.22f)),
            PlumeLook.VULCAIN,
            new PlumeLookConfig(
                new PlumeShape(25f, 3f, 0.5f, 0.05f, 1.0f),
                new PlumeShape(30f, 30f, 0.35f, 0.03f, 0f),
                hydroloxCore,
                hydroloxEdge),
            PlumeLook.VINCI,
            new PlumeLookConfig(vinci, vinci, hydroloxCore, hydroloxEdge),
            PlumeLook.P120C,
            new PlumeLookConfig(
                new PlumeShape(60f, 5f, 1.3f, 0.55f, 0f),
                new PlumeShape(45f, 25f, 0.9f, 0.3f, 0f),
                new PlumeLookConfig.Rgb(1.0f, 1.0f, 0.96f),
                new PlumeLookConfig.Rgb(1.0f, 0.74f, 0.42f)),
            PlumeLook.PAYLOAD,
            new PlumeLookConfig(
                payload,
                payload,
                new PlumeLookConfig.Rgb(0.97f, 0.95f, 0.92f),
                new PlumeLookConfig.Rgb(0.75f, 0.62f, 0.85f))),
        0.35f,
        64,
        7_000.0,
        0.5,
        0.001,
        0.3,
        2.0f);
  }
}
