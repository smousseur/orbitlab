package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import java.util.Optional;

/**
 * What the target-node field accepts, with no widget attached.
 *
 * <p>Extracted from {@link PlanningPage} so the contract can be exercised without an initialised
 * {@code AssetManager} — the page itself cannot be built headless, and this is the only part of it
 * with a decision in it. The same split as {@code MissionDisplayPanelRules}.
 *
 * <p><b>Blank and unreadable are different answers.</b> Blank is the mission saying it waits for no
 * plane, and it is the state of every mission that meets nothing in orbit; unreadable is an
 * intention that could not be honoured, and degrading it to blank would silently turn "meet this
 * plane" into "launch whenever".
 */
final class RaanEntry {

  private RaanEntry() {}

  /**
   * @param text the field's raw content
   * @return the node in degrees, or empty when the entry is blank or unreadable
   */
  static Optional<Double> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Double.parseDouble(text.trim()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /**
   * No range check, on purpose: the criterion the node feeds is periodic, so a negative reading and
   * one past a full turn both name a real plane.
   *
   * @param text the field's raw content
   * @return the reason the entry is refused, or empty when it is blank or usable
   */
  static Optional<String> refusal(String text) {
    String trimmed = text == null ? "" : text.trim();
    if (trimmed.isEmpty() || parse(trimmed).isPresent()) {
      return Optional.empty();
    }
    return Optional.of("Target RAAN is not a number: " + trimmed);
  }
}
