package com.smousseur.orbitlab.engine.events;

/**
 * Which of its two modes the scenario browser opens in (spec {@code
 * docs/scenario/01-persistance-missions.md} §6.2).
 *
 * <p>A top-level type rather than one nested in the event that carries it: the menu publishes it,
 * the app state reads it, the pure model branches on it and the widget draws from it — four
 * packages, none of which should have to name a navigation event to say "open" or "save".
 */
public enum ScenarioBrowserMode {
  /** Read a scenario: the current session is replaced by the one the file describes. */
  OPEN,
  /** Write the current session under a name. */
  SAVE
}
