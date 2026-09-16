package com.smousseur.orbitlab.app;

/**
 * Mutable display preferences the user toggles at runtime (from the app menu), read by the
 * renderers each frame. Held in {@link ApplicationContext}, like {@link
 * com.smousseur.orbitlab.app.view.FocusView}, so a state can push a preference and another can read
 * it without a {@code getState(...)} hop.
 */
public final class DisplaySettings {

  /**
   * Whether jettisoned debris are shown beyond their close-range 3D mesh (their far-range icon and
   * their ground track). Off by default: debris are decluttered unless the user asks for them
   * (PHY-5 / L7, spec {@code docs/multi-objets/09-conception-L7.md} §D1).
   */
  private boolean debrisVisible;

  /**
   * @return whether the debris' secondary display (far-range icon and ground track) is on
   */
  public boolean isDebrisVisible() {
    return debrisVisible;
  }

  /**
   * @param debrisVisible whether to show the debris' secondary display
   */
  public void setDebrisVisible(boolean debrisVisible) {
    this.debrisVisible = debrisVisible;
  }
}
