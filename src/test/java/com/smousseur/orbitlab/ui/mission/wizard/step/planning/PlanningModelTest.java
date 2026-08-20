package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import com.smousseur.orbitlab.simulation.mission.window.problem.EarthLaunchWindowRequest;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/** The planning page's display decision, exercised without the JME lifecycle. */
class PlanningModelTest {

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 3, 21, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static EarthLaunchWindowRequest kourou(double raanDeg) {
    return new EarthLaunchWindowRequest(
        5.23,
        -52.77,
        0.0,
        new LaunchPlane(FastMath.toRadians(51.6), NodeBranch.ASCENDING),
        raanDeg,
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0);
  }

  @Test
  @DisplayName("No node asked for means idle, and idle is not an error")
  void withoutANodeTheModelIsIdle() {
    PlanningModel model = new PlanningModel();
    model.refresh(null, epoch(), false);
    assertInstanceOf(PlanningState.Idle.class, model.state());
  }

  @Test
  @DisplayName("A node asked for but no inputs to serve it is a reason, not silence")
  void aNodeWithoutInputsIsUnavailable() {
    PlanningModel model = new PlanningModel();
    model.refresh(null, epoch(), true);
    assertInstanceOf(PlanningState.Unavailable.class, model.state());
  }

  @Test
  @DisplayName("An unreadable floor is stated, not guessed at")
  void withoutAFloorTheModelIsUnavailable() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), null, true);
    assertInstanceOf(PlanningState.Unavailable.class, model.state());
  }

  @Test
  @DisplayName("A request and a floor produce the opportunities, the first one selected")
  void aRequestProducesWindows() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch(), true);

    PlanningState.Windows windows = assertInstanceOf(PlanningState.Windows.class, model.state());
    assertEquals(PlanningModel.OPPORTUNITIES_SHOWN, windows.windows().size());
    assertEquals(0, windows.selected());
    assertTrue(windows.windows().getFirst().date().durationFrom(epoch()) >= 0.0);
  }

  @Test
  @DisplayName("Refreshing on unchanged inputs does not recompute")
  void theModelMemoisesOnItsInputs() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch(), true);
    PlanningState first = model.state();
    model.refresh(kourou(120.0), epoch(), true);

    assertSame(first, model.state());
  }

  @Test
  @DisplayName("A changed node is a changed request, and the model recomputes")
  void theModelRecomputesWhenTheRequestChanges() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch(), true);
    PlanningState first = model.state();
    model.refresh(kourou(150.0), epoch(), true);

    // Proves the memoisation test above is testing memoisation and not a fixed answer: with a
    // genuinely different request the state is a fresh instance, not the one already held.
    assertNotSame(first, model.state());
    assertInstanceOf(PlanningState.Windows.class, model.state());
  }

  @Test
  @DisplayName("A plane the pad cannot reach is a stated reason, not a crash")
  void anUnreachablePlaneIsReported() {
    PlanningModel model = new PlanningModel();
    EarthLaunchWindowRequest impossible =
        new EarthLaunchWindowRequest(
            51.0,
            0.0,
            0.0,
            new LaunchPlane(FastMath.toRadians(5.0), NodeBranch.ASCENDING),
            120.0,
            Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0);
    model.refresh(impossible, epoch(), true);

    assertInstanceOf(PlanningState.Unavailable.class, model.state());
  }

  @Test
  @DisplayName("select moves the zoom to another opportunity")
  void selectMovesTheZoom() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch(), true);

    model.select(1);

    PlanningState.Windows windows = assertInstanceOf(PlanningState.Windows.class, model.state());
    assertEquals(1, windows.selected());
  }

  @Test
  @DisplayName("select keeps the requested date, which the axis draws its origin from")
  void selectKeepsTheFloor() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch(), true);

    model.select(2);

    PlanningState.Windows windows = assertInstanceOf(PlanningState.Windows.class, model.state());
    assertEquals(epoch(), windows.floor());
  }

  @Test
  @DisplayName("select out of range is ignored")
  void selectOutOfRangeIsIgnored() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch(), true);

    model.select(PlanningModel.OPPORTUNITIES_SHOWN);
    model.select(-1);

    PlanningState.Windows windows = assertInstanceOf(PlanningState.Windows.class, model.state());
    assertEquals(0, windows.selected());
  }

  @Test
  @DisplayName("select is ignored when the state is not Windows, so a caller need not test first")
  void selectIsIgnoredOutsideWindows() {
    PlanningModel model = new PlanningModel();
    model.refresh(null, epoch(), false);

    model.select(0);

    assertInstanceOf(PlanningState.Idle.class, model.state());
  }
}
