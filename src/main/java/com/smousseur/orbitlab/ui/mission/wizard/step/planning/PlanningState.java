package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import java.util.List;

/**
 * What the planning page has to draw. Three cases and no fourth, which is what makes the page's
 * rendering a switch rather than a chain of null checks.
 */
public sealed interface PlanningState {

  /**
   * No target node: the common case, and not an error. The frames are drawn empty rather than
   * hidden, so the page fills in when a node is typed instead of reorganising itself.
   */
  record Idle() implements PlanningState {}

  /**
   * A node was asked for but no window can be shown, and the page says why: an unreadable pad, an
   * unreadable floor, a plane the site cannot reach, or a search that came back empty.
   *
   * @param reason the line shown in place of the axis
   */
  record Unavailable(String reason) implements PlanningState {}

  /**
   * The opportunities found, in chronological order.
   *
   * @param windows the slots, never empty
   * @param selected the index of the one the zoom pane shows
   */
  record Windows(List<LaunchWindow> windows, int selected) implements PlanningState {

    public Windows {
      windows = List.copyOf(windows);
      if (windows.isEmpty()) {
        throw new OrbitlabException("an empty window list is Unavailable, not Windows");
      }
      if (selected < 0 || selected >= windows.size()) {
        throw new OrbitlabException("selected out of range: " + selected);
      }
    }

    public LaunchWindow current() {
      return windows.get(selected);
    }
  }
}
