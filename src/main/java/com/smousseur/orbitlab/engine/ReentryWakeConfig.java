package com.smousseur.orbitlab.engine;

/**
 * Tuning of the re-entry wake: the glowing tube a hot falling object trails behind it, along the
 * air-relative velocity.
 *
 * <p>Up close the wake is physical: it streams back from the open rim of the sheath, as wide as
 * that rim, past the body and on beyond its base by the distance the object covers through the air
 * in {@code persistenceSeconds}. Its width is the sheath's, not a tuning of its own, so the two
 * stay one envelope whatever the sheath's proportions. Far away it would shrink under a pixel — a
 * falling debris glows 400 to 1,400 km from the primary the camera follows by default — so two
 * floors take over there. The width never drops under {@code minWidthPx}. The length is magnified
 * instead of clamped: a wake at {@code referenceSpeedMs} is never shorter than {@code minLengthPx},
 * and every other wake keeps its proportion to it, so a slow booster still trails a shorter wake
 * than a fast core from across the screen.
 *
 * @param persistenceSeconds how long the heated air stays luminous, in seconds (finite, positive):
 *     the wake runs {@code persistenceSeconds · |v_rel|} beyond the body up close
 * @param minWidthPx the width, in screen pixels, under which the wake never narrows (finite,
 *     positive)
 * @param minLengthPx the length, in screen pixels, under which a wake at the reference speed never
 *     shrinks (finite, positive)
 * @param referenceSpeedMs the air-relative speed, in m/s, whose wake the length floor is quoted for
 *     (finite, positive)
 */
public record ReentryWakeConfig(
    float persistenceSeconds, float minWidthPx, float minLengthPx, float referenceSpeedMs) {

  public ReentryWakeConfig {
    requirePositive(persistenceSeconds, "persistenceSeconds");
    requirePositive(minWidthPx, "minWidthPx");
    requirePositive(minLengthPx, "minLengthPx");
    requirePositive(referenceSpeedMs, "referenceSpeedMs");
  }

  /**
   * The length of the wake the length floor is quoted for: the distance the reference speed covers
   * in the persistence.
   *
   * @return the reference length, in metres
   */
  public double referenceLengthMeters() {
    return (double) persistenceSeconds * referenceSpeedMs;
  }

  /**
   * Creates the default wake tuning.
   *
   * <p>Every value is a starting point, to be set on screen, not measured. A quarter of a second of
   * persistence draws the measured peaks at about 630 m for an Ariane 64 booster, 1.1 km for a
   * Falcon Heavy booster and 1.5 km for its core, beyond the body. Three pixels make it twice as
   * wide as a mission trace (1.5 px): the wake lies on the trace it runs along, a few degrees off
   * it, and at two pixels it read as that trace slightly brightened. The reference speed is about
   * the median air-relative speed of a Falcon Heavy core while it glows (6.95 km/s), so from the
   * primary that core trails some 33 px, a Falcon Heavy booster 25 px and an Ariane 64 booster 14
   * px at their peaks.
   *
   * @return the default wake configuration
   */
  public static ReentryWakeConfig defaults() {
    return new ReentryWakeConfig(0.25f, 3.0f, 40f, 7_000f);
  }

  private static void requirePositive(float value, String name) {
    if (!Float.isFinite(value) || value <= 0f) {
      throw new IllegalArgumentException(name + " must be finite and > 0, got " + value);
    }
  }
}
