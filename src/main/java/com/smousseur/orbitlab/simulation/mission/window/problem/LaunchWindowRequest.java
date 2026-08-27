package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowProblem;

/**
 * What a caller hands a planner to get opportunities back: the inputs of a window, and nothing else
 * (MIS-4 / L5 §4.2).
 *
 * <p><b>Sealed over records, and that is the point.</b> The wizard's planning page recomputes on a
 * polled loop and memoises on {@code equals}; a record's equality is by value all the way down,
 * where a {@link LaunchWindowProblem} implementation's is by identity. Carrying the problem
 * directly through the page's inputs would make the memoisation stop biting on something redrawn
 * every frame.
 *
 * <p><b>One member, and it is the only thing the two branches share.</b> An Earth window is a plane
 * with a node to meet; a lunar one is a direction to contain. They have no common component — the
 * launch site's three numbers happen to appear in both, and nothing else — so the abstraction is
 * the problem they pose, not the data they hold.
 *
 * <p>Both branches live in this package because the project has no {@code module-info}: a sealed
 * type of an unnamed module only admits permitted subclasses of its own package.
 */
public sealed interface LaunchWindowRequest
    permits EarthLaunchWindowRequest, LunarLaunchWindowRequest {

  /**
   * @return the problem this request poses
   * @throws com.smousseur.orbitlab.core.OrbitlabException if the inputs describe no solvable
   *     problem — an Earth site that cannot reach the plane asked for
   */
  LaunchWindowProblem toProblem();
}
