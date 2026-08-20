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
 * frame with no-op guards; this follows the same idiom, and {@link PlanningInputs} being a record
 * over a record, the "did anything change" test is its {@code equals}. A search over three
 * terrestrial opportunities is some 250 closed-form evaluations, so the cost of getting this wrong
 * is small — but an axis recomputing under an unchanged form would still flicker.
 */
public final class PlanningModel {

  /**
   * How many opportunities the axis offers. A display preference and the only count this class
   * writes down: how far that reaches in time is {@link
   * com.smousseur.orbitlab.simulation.mission.window.LaunchWindowProblem#recurrence()}'s business,
   * three days on an Earth plane alignment and three months on a translunar injection.
   */
  public static final int OPPORTUNITIES_SHOWN = 3;

  /**
   * The reasons, one per {@link PlanningInputs.Gap} that is not {@link PlanningInputs.Gap#NONE},
   * plus the two this class discovers on its own.
   *
   * <p><b>ASCII, and short.</b> They are drawn as a single monospaced caption the width of the
   * track, which clips rather than wraps, and the bundled {@code ibmPlexMono} stops at glyph 127 —
   * a dash or a degree sign draws nothing at all and reports nothing. So the model phrases its own
   * lines instead of forwarding a model exception's: {@code LaunchPlane}'s unreachable message is a
   * 155-character sentence carrying four degree signs, right for a log and unusable here.
   */
  private static final String NO_FLOOR = "launch date unreadable";

  private static final String NO_SITE = "launch site unreadable";
  private static final String NO_NODE = "target node unreadable";
  private static final String NO_TARGET = "target plane unreadable, or out of this site's reach";
  private static final String NO_WINDOW = "no opportunity found - check the target plane";

  private PlanningInputs lastInputs;
  private AbsoluteDate lastFloor;
  private PlanningState state = new PlanningState.Idle();

  /**
   * Recomputes if, and only if, something changed.
   *
   * <p><b>{@link PlanningInputs} is what separates the ways a window can be missing</b>, and they
   * are not the same news: no node at all is the common case and deserves the quiet empty frames,
   * while a node that was asked for and could not be served — an unreadable pad, an unreadable
   * node, a plane out of reach — is a reason the user has to be told. Collapsing them into a null
   * request is what made this page blame the launch site for an inclination the pad cannot reach.
   *
   * @param inputs the window inputs, or the reason the form cannot describe them
   * @param floor the launch date read as a floor, or {@code null} when the field does not parse
   */
  public void refresh(PlanningInputs inputs, AbsoluteDate floor) {
    Objects.requireNonNull(inputs, "inputs");
    if (inputs.equals(lastInputs) && Objects.equals(floor, lastFloor)) {
      return;
    }
    lastInputs = inputs;
    lastFloor = floor;
    state = compute(inputs, floor);
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
      state = new PlanningState.Windows(windows.floor(), windows.windows(), index);
    }
  }

  public PlanningState state() {
    return state;
  }

  private static PlanningState compute(PlanningInputs inputs, AbsoluteDate floor) {
    return switch (inputs.gap()) {
      case NO_NODE -> new PlanningState.Idle();
      case UNREADABLE_NODE -> new PlanningState.Unavailable(NO_NODE);
      case NO_SITE -> new PlanningState.Unavailable(NO_SITE);
      case NO_TARGET -> new PlanningState.Unavailable(NO_TARGET);
      case NONE -> search(inputs.request(), floor);
    };
  }

  /**
   * Runs the search, and answers for the two ways it can come back with nothing to draw.
   *
   * <p><b>The {@link OrbitlabException} guard is not a live path from the wizard.</b> The only
   * refusal {@code nextOpportunities} raises is a plane the pad cannot reach, and the parameters
   * step now turns that into {@link PlanningInputs.Gap#NO_TARGET} before a request is ever built. It
   * is kept because the precondition belongs to the planner and not to this class: a caller
   * assembling a request of its own — which is exactly what {@code PlanningModelTest} does — has no
   * other way of being told it was refused, and an exception escaping here would take the render
   * thread down. The reason it reports is the model's own short line, not the exception's.
   */
  private static PlanningState search(EarthLaunchWindowRequest request, AbsoluteDate floor) {
    if (floor == null) {
      return new PlanningState.Unavailable(NO_FLOOR);
    }
    try {
      List<LaunchWindow> windows =
          EarthLaunchWindowPlanner.nextOpportunities(request, floor, OPPORTUNITIES_SHOWN);
      return windows.isEmpty()
          ? new PlanningState.Unavailable(NO_WINDOW)
          : new PlanningState.Windows(floor, windows, 0);
    } catch (OrbitlabException unreachable) {
      return new PlanningState.Unavailable(NO_TARGET);
    }
  }
}
