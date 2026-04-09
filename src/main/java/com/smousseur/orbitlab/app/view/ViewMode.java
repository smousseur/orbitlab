package com.smousseur.orbitlab.app.view;

/**
 * High-level rendering view modes that determine how the scene is displayed.
 *
 * <p>This is a logical concept (not a JME ViewPort). It controls coordinate frame selection,
 * scale factors, and which objects are visible.
 */
public enum ViewMode {
  /** Solar system overview: heliocentric view showing all planetary orbits. */
  SOLAR,
  /** Planet-centered view: close-up view relative to a single celestial body. */
  PLANET,
  /**
   * Spacecraft-centered view: the camera follows a single mission's spacecraft. The parent body
   * stored on the focus view is used to drive the planet-scale render context (HUD markers,
   * orbits), while the near frame is offset so the spacecraft sits at the near-view origin.
   */
  SPACECRAFT
}
