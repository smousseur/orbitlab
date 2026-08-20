package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.problem.EarthLaunchWindowPlanner;
import com.smousseur.orbitlab.simulation.mission.window.problem.EarthLaunchWindowRequest;
import java.util.List;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * The planning page's display decision, and the only place that decides when to search again.
 *
 * <p><b>Extracted as a plain object</b> so the rules are exercised by unit tests without the JME
 * lifecycle — the precedent is {@code MissionDisplayPanelRules}.
 *
 * <p><b>Memoised on its inputs, because it is polled.</b> The parameters step already runs on every
 * frame with no-op guards; this follows the same idiom, and {@link EarthLaunchWindowRequest} being
 * a record, the "did anything change" test is its {@code equals}. A search over three terrestrial
 * opportunities is some 250 closed-form evaluations, so the cost of getting this wrong is small —
 * but an axis recomputing under an unchanged form would still flicker.
 */
public final class PlanningModel {

  /**
   * How many opportunities the axis offers. A display preference and the only count this class
   * writes down: how far that reaches in time is {@link
   * com.smousseur.orbitlab.simulation.mission.window.LaunchWindowProblem#recurrence()}'s business,
   * three days on an Earth plane alignment and three months on a translunar injection.
   */
  public static final int OPPORTUNITIES_SHOWN = 3;

  private static final String NO_FLOOR = "launch date unreadable";
  private static final String NO_SITE = "launch site unreadable";
  private static final String NO_WINDOW = "no opportunity found — check the target plane";

  private EarthLaunchWindowRequest lastRequest;
  private AbsoluteDate lastFloor;
  private boolean lastNodeRequested;
  private PlanningState state = new PlanningState.Idle();

  /**
   * Recomputes if, and only if, something changed.
   *
   * <p><b>{@code nodeRequested} is what separates the two ways a request can be missing</b>, and
   * they are not the same news: no node at all is the common case and deserves the quiet empty
   * frames, while a node that was asked for and could not be served — an unreadable pad, a plane
   * out of reach — is a reason the user has to be told. Without this flag both arrive as a null
   * request and the page stays silent about a failure.
   *
   * @param request the window inputs, or {@code null} when there is no node or the pad is
   *     unreadable
   * @param floor the launch date read as a floor, or {@code null} when the field does not parse
   * @param nodeRequested whether the node field holds a number
   */
  public void refresh(EarthLaunchWindowRequest request, AbsoluteDate floor, boolean nodeRequested) {
    if (Objects.equals(request, lastRequest)
        && Objects.equals(floor, lastFloor)
        && nodeRequested == lastNodeRequested) {
      return;
    }
    lastRequest = request;
    lastFloor = floor;
    lastNodeRequested = nodeRequested;
    state = compute(request, floor, nodeRequested);
  }

  /**
   * Points the zoom pane at another opportunity. Ignored when there is nothing to point at, so the
   * caller does not have to test the state before forwarding a click.
   *
   * @param index the opportunity to show
   */
  public void select(int index) {
    if (state instanceof PlanningState.Windows windows
        && index >= 0
        && index < windows.windows().size()) {
      state = new PlanningState.Windows(windows.windows(), index);
    }
  }

  public PlanningState state() {
    return state;
  }

  private static PlanningState compute(
      EarthLaunchWindowRequest request, AbsoluteDate floor, boolean nodeRequested) {
    if (!nodeRequested) {
      return new PlanningState.Idle();
    }
    if (request == null) {
      return new PlanningState.Unavailable(NO_SITE);
    }
    if (floor == null) {
      return new PlanningState.Unavailable(NO_FLOOR);
    }
    try {
      List<LaunchWindow> windows =
          EarthLaunchWindowPlanner.nextOpportunities(request, floor, OPPORTUNITIES_SHOWN);
      return windows.isEmpty()
          ? new PlanningState.Unavailable(NO_WINDOW)
          : new PlanningState.Windows(windows, 0);
    } catch (OrbitlabException refused) {
      // A plane the pad cannot reach. The parameters step refuses it too, but this runs on every
      // frame, so it sees the state between the bad keystroke and the Next that rejects it.
      return new PlanningState.Unavailable(refused.getMessage());
    }
  }
}
