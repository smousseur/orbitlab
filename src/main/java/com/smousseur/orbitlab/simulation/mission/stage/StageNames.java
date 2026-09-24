package com.smousseur.orbitlab.simulation.mission.stage;

/**
 * The stage names that are <b>matched</b> rather than displayed.
 *
 * <p>Every other name in a mission chain is a label: it reaches a log line or the trajectory panel
 * and nothing reads it back, so it can be spelled at its construction site. The ones below cannot,
 * or are kept here for the reason each gives.
 *
 * <p><b>{@link #TERMINAL_COAST} is the load-bearing one.</b> {@code MissionLoadEvaluator} selects
 * the samples the insertion objective is scored on by comparing against it, and four missions build
 * that coast — two of them with a coast class of their own. A typo would not fail: it would score
 * the mission on an empty sample set.
 *
 * <p><b>{@link #UPPER_SEPARATION} is matched by nothing today.</b> It is here because two missions
 * spell the same jettison and nothing keeps them in step, not because a mismatch would break
 * anything now.
 */
public final class StageNames {

  /** Name of the terminal coast, the stage the insertion objective is scored on. */
  public static final String TERMINAL_COAST = "Coasting";

  /** Name of the jettison that drops the launcher's upper stage. */
  public static final String UPPER_SEPARATION = "S2 separation";

  /**
   * Name of each burn of the disposal tail. Matched by nothing yet: it is here so that whatever
   * later tells the tail apart from the mission's own stages reads one spelling.
   */
  public static final String DEORBIT_BURN = "Deorbit burn";

  /**
   * Name of each coast of the disposal tail, from one burn to the apogee the next is centred on.
   */
  public static final String DEORBIT_COAST = "Deorbit coast";

  /**
   * Name of the payload's fall to the ground after its disposal tail. Its own name rather than the
   * debris one: the payload is not a debris, and a reader sorting samples by name must not mistake
   * one for the other.
   */
  public static final String REENTRY = "Reentry";

  private StageNames() {}
}
