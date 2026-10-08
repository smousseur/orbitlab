package com.smousseur.orbitlab.engine.scene.hover;

/**
 * How much a body fades back while another one is lit, as an opacity factor in {@code [floor,1]}.
 *
 * <p>The rule is the mockup's: a body is dimmed by how far it trails the most lit body, {@code 1 −
 * (1 − floor)·clamp(strongest − own, 0, 1)}. It has no fade of its own — it follows the planets'
 * intensities. With nothing lit every body stays at 1; with one planet fully lit the others sit on
 * their floor; and when the cursor moves from one planet to another, the one fading out is dimmed
 * by the gap to the one fading in, which crosses the two fades without a jump.
 */
public final class HoverDim {

  private HoverDim() {}

  /**
   * Computes a body's dimming.
   *
   * @param strongest the highest hover intensity among the planets this frame
   * @param own this body's own hover intensity
   * @param floor the opacity the body falls to when it trails by a full intensity, in {@code [0,1]}
   * @return the opacity factor, 1 for a body that is not dimmed
   */
  public static float of(float strongest, float own, float floor) {
    float gap = Math.clamp(strongest - own, 0f, 1f);
    return 1f - (1f - floor) * gap;
  }
}
