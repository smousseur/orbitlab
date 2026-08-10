package com.smousseur.orbitlab.engine;

/**
 * Timing curves applied to a normalised animation progress.
 *
 * <p>Every constant maps {@code [0,1]} onto {@code [0,1]} with {@code apply(0) == 0} and {@code
 * apply(1) == 1}, so a caller can always interpolate between its endpoints without special-casing
 * the extremities.
 */
public enum Easing {
  /** No easing: progress is returned unchanged. */
  LINEAR {
    @Override
    public float apply(float t) {
      return t;
    }
  },

  /** Cubic Hermite ramp {@code t²(3 − 2t)}: zero derivative at both ends. */
  SMOOTHSTEP {
    @Override
    public float apply(float t) {
      return t * t * (3f - 2f * t);
    }
  },

  /** Raised cosine: same endpoints as {@link #SMOOTHSTEP}, slightly softer in the middle. */
  COSINE {
    @Override
    public float apply(float t) {
      return 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
    }
  };

  /**
   * Maps a normalised progress onto the eased progress.
   *
   * @param t the linear progress, expected in {@code [0,1]}
   * @return the eased progress in {@code [0,1]}
   */
  public abstract float apply(float t);
}
