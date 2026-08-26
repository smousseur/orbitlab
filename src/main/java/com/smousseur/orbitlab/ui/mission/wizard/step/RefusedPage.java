package com.smousseur.orbitlab.ui.mission.wizard.step;

/**
 * Which page of the parameters step a refusal has to reveal.
 *
 * <p>Extracted as a plain function so the choice can be exercised without an initialised {@code
 * AssetManager} — the step itself cannot be built headless, and this is the only decision in it.
 * The same split as {@code MissionDisplayPanelRules} and {@code RaanEntry}.
 */
final class RefusedPage {

  /** The page to mount, or {@link #NONE} when nothing was refused. */
  enum Target {
    NONE,
    FIELDS,
    PLANNING
  }

  private RefusedPage() {}

  /**
   * Chooses the page to mount from the marks the validators left.
   *
   * <p><b>The fields page wins when anything on it was refused</b>, even if the node was refused
   * too. Two pages cannot both be shown, so one mark is always hidden; travelling away from a red
   * field the user can already see is the worse of the two, and the node is revealed on the next
   * press once the fields are clean.
   *
   * @param dateRefused whether the launch date was refused
   * @param horizonRefused whether the mission duration was refused
   * @param inclinationRefused whether the target inclination was refused
   * @param nodeRefused whether the target node was refused
   * @return the page to mount
   */
  static Target choose(
      boolean dateRefused,
      boolean horizonRefused,
      boolean inclinationRefused,
      boolean nodeRefused) {
    if (dateRefused || horizonRefused || inclinationRefused) {
      return Target.FIELDS;
    }
    return nodeRefused ? Target.PLANNING : Target.NONE;
  }
}
