package com.smousseur.orbitlab.simulation.flight;

/**
 * Which atmosphere a mission is flown against — the user-facing switch of PHY-1 (spec {@code
 * docs/atmosphere/04-conception-L1.md} §2).
 *
 * <p>The enum names a model, not a built object: an {@code Atmosphere} instance is built against a
 * body shape, so it cannot be resolved before the central body is known. {@code OrekitService}
 * resolves the pair {@code (model, shape)} at propagator construction, which is what lets a {@link
 * DragContext} cross a sphere-of-influence boundary unchanged and still be right on the other side
 * (spec §1.2).
 */
public enum AtmosphereModel {
  /**
   * No atmosphere at all. The absence of drag, expressed as a choice rather than as a model — every
   * mission carries this value until PHY-2 lets one carry another, and it is why the lot changes
   * nothing to the bit.
   *
   * <p>{@link DragContext} <b>rejects</b> it: once a context exists, "no drag" already has its own
   * representation, a null context.
   */
  NONE,

  /**
   * Harris-Priester: a static density table with a diurnal bulge, no solar or geomagnetic activity
   * input. Cheap and reproducible — the same date always gives the same density.
   */
  HARRIS_PRIESTER,

  /**
   * NRLMSISE-00: the reference empirical model, driven by solar flux and geomagnetic indices. More
   * faithful than {@link #HARRIS_PRIESTER} and more expensive, and its answer depends on the space
   * weather data the date resolves to.
   */
  NRLMSISE
}
