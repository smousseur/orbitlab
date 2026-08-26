package com.smousseur.orbitlab.ui.mission.component;

/**
 * The cadence of the spinner, separated from the geometry it drives so that it can be tested
 * without a JME context — the same split {@code MissionDisplayPanelRules} makes for the display
 * panel.
 *
 * <p>Both constants come from the icon rather than from taste: {@code icon-spinner.png} carries
 * twelve spokes spaced 30 degrees apart, so a step of one spoke is the only rotation that lands the
 * drawing back on itself, and twelve steps at ten per second give the conventional revolution in
 * 1.2 s. The direction is measured too: the comet's tail trails counter-clockwise from its
 * brightest spoke, so the icon reads as turning clockwise, which is a negative angle about Z.
 */
public final class SpinnerRotation {

  /** Spokes in the icon, and therefore steps in a revolution. */
  public static final int STEPS_PER_TURN = 12;

  /** Seconds between two steps. */
  public static final float STEP_SECONDS = 0.1f;

  private static final float STEP_RADIANS = (float) (-2.0 * Math.PI / STEPS_PER_TURN);

  private float accumulated;
  private int step;

  /**
   * Advances the cadence by one frame.
   *
   * @param tpf the frame time in seconds
   * @return whether the step index changed, and therefore whether the caller has to touch the scene
   *     graph at all this frame
   */
  public boolean advance(float tpf) {
    if (!(tpf > 0f)) {
      return false;
    }
    accumulated += tpf;
    if (accumulated < STEP_SECONDS) {
      return false;
    }
    // A frame longer than the period advances by as many steps as it covers, rather than dropping
    // them: a stall would otherwise leave the spinner behind the elapsed time for good.
    int taken = (int) (accumulated / STEP_SECONDS);
    accumulated -= taken * STEP_SECONDS;
    step = Math.floorMod(step + taken, STEPS_PER_TURN);
    return true;
  }

  /**
   * @return the current step, in {@code [0, STEPS_PER_TURN)}
   */
  public int step() {
    return step;
  }

  /**
   * @return the current rotation about Z, in radians, negative for the clockwise direction the icon
   *     is drawn to turn in
   */
  public float angleRadians() {
    return step * STEP_RADIANS;
  }
}
