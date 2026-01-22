package com.smousseur.orbitlab.app.view;

/**
 * Defines how Orekit positions (meters, typically ICRF) are interpreted before scaling to JME units.
 *
 * <p>Note: this is a rendering frame / convention, not necessarily an Orekit Frame instance.
 */
public enum RenderFrame {
  /** Absolute positions, heliocentric (e.g., body position in ICRF). */
  HELIOCENTRIC_ICRF,

  /** Positions expressed relative to a target body: r_local = r_object - r_target (still in ICRF axes). */
  PLANETOCENTRIC_RELATIVE_ICRF
}
