package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.window.problem.EarthLaunchWindowRequest;
import java.util.Objects;

/**
 * What the form could hand the planner, or the reason it could not.
 *
 * <p><b>Why a gap and not a bare null.</b> The parameters step is the only layer that knows
 * <em>which</em> input failed — the pad, the target orbit, the node — and a null request throws
 * that knowledge away on the way to {@link PlanningModel}, which then has to guess at a single
 * reason for every absence. Guessing showed the launch site as unreadable when what was actually
 * refused was an inclination the pad cannot reach, which is a false statement on screen.
 *
 * <p><b>It is also the memoisation key</b>, and that is why the gap travels with the request
 * instead of beside it: one {@code equals} decides whether anything moved, exactly as {@link
 * EarthLaunchWindowRequest}'s does for the six numbers inside it.
 *
 * <p>The wording of each gap stays in {@link PlanningModel}: the axis caption is a clipped,
 * monospaced ASCII line, so what a gap reads as is a display decision and not the step's.
 *
 * @param request the window inputs, or null when {@code gap} says why there are none
 * @param gap what is missing, {@link Gap#NONE} when the request is there
 */
public record PlanningInputs(EarthLaunchWindowRequest request, Gap gap) {

  /** The ways the form can fail to describe a window, each one different news for the user. */
  public enum Gap {
    /** Nothing is missing: the request is there. */
    NONE,

    /** No target node at all — the common case, and not an error: no plane is being waited for. */
    NO_NODE,

    /** A node was typed and does not read as a number. An intention, not a preference. */
    UNREADABLE_NODE,

    /** One of the pad's three numbers does not read, so no window can be sited. */
    NO_SITE,

    /**
     * The pad reads, but the target orbit does not: an unreadable entry, or a plane out of reach.
     */
    NO_TARGET
  }

  public PlanningInputs {
    Objects.requireNonNull(gap, "gap");
    if ((request == null) != (gap != Gap.NONE)) {
      throw new OrbitlabException("a request and a gap are exclusive, got " + gap);
    }
  }

  /**
   * @param request the inputs the form assembled
   * @return the complete inputs
   */
  public static PlanningInputs of(EarthLaunchWindowRequest request) {
    return new PlanningInputs(Objects.requireNonNull(request, "request"), Gap.NONE);
  }

  /**
   * @param gap why the form cannot describe a window; never {@link Gap#NONE}
   * @return the absence, carrying its reason
   */
  public static PlanningInputs missing(Gap gap) {
    return new PlanningInputs(null, gap);
  }
}
