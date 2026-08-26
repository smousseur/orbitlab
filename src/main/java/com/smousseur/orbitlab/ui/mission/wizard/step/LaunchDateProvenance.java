package com.smousseur.orbitlab.ui.mission.wizard.step;

/**
 * Where the launch date on screen comes from, which is what its helper line has to say.
 *
 * <p>The field has no state of its own to read: it holds a string, and a string written by the
 * planner is indistinguishable from the same string typed by hand. What tells them apart is whether
 * the text still <em>is</em> the one the step last wrote — the same bargain the mission duration's
 * auto state makes, and the reason neither needs a widget to carry it.
 *
 * <p>Extracted as a plain function so the two behaviours that are easy to break can be asserted
 * without an initialised {@code AssetManager}: that a refusal raised and then cleared does not
 * relabel a planned date as a typed one, and that a hand edit drops the planner's claim on the very
 * next frame. The same split as {@code RefusedPage} and {@code RaanEntry}.
 */
final class LaunchDateProvenance {

  /** What the helper line under the launch date field has to say. */
  enum Source {
    /** The date is whatever was typed; the field's default helper stands. */
    TYPED,
    /** The date is the one a clicked opportunity wrote. */
    PLANNED,
    /** The date was refused; the refusal's own reason stands and must not be overwritten. */
    REFUSED
  }

  private LaunchDateProvenance() {}

  /**
   * Reads the provenance of the text currently in the field.
   *
   * <p><b>A refusal hides the provenance without erasing it.</b> A planned date can be refused — an
   * opportunity falling outside the ephemeris coverage is the case that exists — and the reason has
   * to take the line while it stands. The claim itself survives, so clearing the refusal shows the
   * planner's line again rather than silently demoting the date to a typed one.
   *
   * @param planned whether the planner's claim still stands
   * @param written the text the planner last wrote, empty when it never wrote any
   * @param current the text the field holds now
   * @param refused whether a refusal is currently painted on the field
   * @return what the helper line has to say
   */
  static Source read(boolean planned, String written, String current, boolean refused) {
    if (refused) {
      return Source.REFUSED;
    }
    return planned && written.equals(current) ? Source.PLANNED : Source.TYPED;
  }
}
