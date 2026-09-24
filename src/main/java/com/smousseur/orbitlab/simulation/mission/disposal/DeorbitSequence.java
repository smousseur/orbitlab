package com.smousseur.orbitlab.simulation.mission.disposal;

import com.smousseur.orbitlab.simulation.mission.MissionStage;
import java.util.List;
import java.util.Objects;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * A planned disposal tail: the stages to fly after the mission's horizon, what flying them ends on,
 * and why the planning stopped.
 *
 * @param stages the stages to fly, in order — a coast to the next apogee, then a burn, as many
 *     times as there are burns; empty when the sequence stopped before its first burn
 * @param burns each burn as it was planned, in flight order
 * @param finalState the state flying {@code stages} ends on, or the horizon state when there are
 *     none
 * @param end why the planning stopped
 */
public record DeorbitSequence(
    List<MissionStage> stages, List<Burn> burns, SpacecraftState finalState, End end) {

  /** Why a deorbit sequence stopped. */
  public enum End {
    /** A burn brought the osculating perigee down to the target. */
    TARGET_REACHED(true),
    /** The last burn spent every kilogram above the depletion floor without reaching the target. */
    PROPELLANT_SPENT(false),
    /**
     * The coast to the next apogee was stopped by the re-entry guard: the payload is falling
     * already, and needs no further burn. A success, like {@link #TARGET_REACHED}.
     */
    FELL_BEFORE_NEXT_BURN(true),
    /** The safety bound on the number of burns was reached. */
    MAX_BURNS(false),
    /**
     * A segment stopped short of its own end for another reason than its cutoff, or did not
     * propagate at all. It is kept in the stages, so that flying them reports the same truncation.
     */
    TRUNCATED(false);

    private final boolean leadsToReentry;

    End(boolean leadsToReentry) {
      this.leadsToReentry = leadsToReentry;
    }

    /**
     * Whether a sequence ending this way leaves the payload falling, so that its fall to the ground
     * is flown next. Every measured sequence ending on the target — both payloads, from 400 to 1
     * 800 km, at three inclinations — reached the ground within 33 to 49 % of its final orbit's
     * period, and so did the production flight ending on a fall before its next burn. The other
     * ends leave a payload whose orbit was not brought down: flown for days, an under-sized reserve
     * never re-entered.
     *
     * @return {@code true} for {@link #TARGET_REACHED} and {@link #FELL_BEFORE_NEXT_BURN}
     */
    public boolean leadsToReentry() {
      return leadsToReentry;
    }
  }

  /**
   * One burn as it was planned.
   *
   * @param apogee the apogee the burn is centred on
   * @param plannedSeconds the duration it was centred with (s) — the arc cap or the propellant
   *     left, whichever is shorter. The burn actually flown can be shorter: the last one stops on
   *     the target perigee.
   */
  public record Burn(AbsoluteDate apogee, double plannedSeconds) {}

  public DeorbitSequence {
    stages = List.copyOf(stages);
    burns = List.copyOf(burns);
    Objects.requireNonNull(finalState, "finalState");
    Objects.requireNonNull(end, "end");
  }
}
