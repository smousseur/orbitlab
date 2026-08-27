package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.problem.EarthLaunchWindowRequest;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowProblem;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowRequest;
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

  private static PlanningInputs kourou(double raanDeg) {
    return PlanningInputs.of(
        new EarthLaunchWindowRequest(
            5.23,
            -52.77,
            0.0,
            new LaunchPlane(FastMath.toRadians(51.6), NodeBranch.ASCENDING),
            raanDeg,
            Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0));
  }

  private static PlanningInputs missing(PlanningInputs.Gap gap) {
    return PlanningInputs.missing(gap);
  }

  /** Canaveral, 400 km parking, 100 km perilune — the reference geometry of the chantier. */
  private static PlanningInputs lunar(double periluneAltitude) {
    return PlanningInputs.of(
        new LunarLaunchWindowRequest(28.562, -80.577, 3.0, 400_000.0, periluneAltitude));
  }

  @Test
  @DisplayName("No node asked for means idle, and idle is not an error")
  void withoutANodeTheModelIsIdle() {
    PlanningModel model = new PlanningModel();
    model.refresh(missing(PlanningInputs.Gap.NO_NODE), epoch());
    assertInstanceOf(PlanningState.Idle.class, model.state());
  }

  @Test
  @DisplayName("An unreadable node erases the axis and says so, rather than claiming idleness")
  void anUnreadableNodeIsItsOwnReason() {
    PlanningModel model = new PlanningModel();
    model.refresh(missing(PlanningInputs.Gap.UNREADABLE_NODE), epoch());

    PlanningState.Unavailable state =
        assertInstanceOf(PlanningState.Unavailable.class, model.state());
    // The field is painted red in the same breath, so an axis announcing "no plane is being waited
    // for" underneath it would have the two controls contradicting each other (spec §6).
    assertTrue(state.reason().contains("target node"), state.reason());
  }

  @Test
  @DisplayName("A pad that cannot be read names the pad")
  void anUnreadableSiteIsUnavailable() {
    PlanningModel model = new PlanningModel();
    model.refresh(missing(PlanningInputs.Gap.NO_SITE), epoch());

    PlanningState.Unavailable state =
        assertInstanceOf(PlanningState.Unavailable.class, model.state());
    assertTrue(state.reason().contains("launch site"), state.reason());
  }

  @Test
  @DisplayName("A target orbit the form cannot describe names the plane, not the launch site")
  void anUndescribableTargetIsNotBlamedOnTheSite() {
    PlanningModel model = new PlanningModel();
    model.refresh(missing(PlanningInputs.Gap.NO_TARGET), epoch());
    PlanningState.Unavailable target =
        assertInstanceOf(PlanningState.Unavailable.class, model.state());

    model.refresh(missing(PlanningInputs.Gap.NO_SITE), epoch());
    PlanningState.Unavailable site =
        assertInstanceOf(PlanningState.Unavailable.class, model.state());

    // The regression this whole distinction exists for: an inclination the pad cannot reach used to
    // arrive here as a null request and be reported as an unreadable launch site.
    assertTrue(target.reason().contains("target plane"), target.reason());
    assertNotEquals(site.reason(), target.reason());
  }

  @Test
  @DisplayName("The reasons stay inside the caption the axis draws them in")
  void everyReasonFitsTheTrack() {
    PlanningModel model = new PlanningModel();
    for (PlanningInputs.Gap gap : PlanningInputs.Gap.values()) {
      if (gap == PlanningInputs.Gap.NONE || gap == PlanningInputs.Gap.NO_NODE) {
        continue;
      }
      model.refresh(missing(gap), epoch());
      String reason = assertInstanceOf(PlanningState.Unavailable.class, model.state()).reason();
      // The caption is monospaced ibmPlexMono(10) at 6 px per character across a 752 px track, and
      // it clips rather than wraps; the same font carries no glyph past 127, where a miss draws
      // nothing at all and reports nothing.
      assertTrue(reason.length() <= 125, gap + ": " + reason);
      assertTrue(reason.chars().allMatch(c -> c >= 0x20 && c < 0x7F), gap + ": " + reason);
    }
  }

  @Test
  @DisplayName("An unreadable floor is stated, not guessed at")
  void withoutAFloorTheModelIsUnavailable() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), null);
    assertInstanceOf(PlanningState.Unavailable.class, model.state());
  }

  @Test
  @DisplayName("A request and a floor produce the opportunities, the first one selected")
  void aRequestProducesWindows() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch());

    PlanningState.Windows windows = assertInstanceOf(PlanningState.Windows.class, model.state());
    assertEquals(PlanningModel.OPPORTUNITIES_SHOWN, windows.windows().size());
    assertEquals(0, windows.selected());
    assertTrue(windows.windows().getFirst().date().durationFrom(epoch()) >= 0.0);
  }

  @Test
  @DisplayName("Refreshing on unchanged inputs does not recompute")
  void theModelMemoisesOnItsInputs() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch());
    PlanningState first = model.state();
    model.refresh(kourou(120.0), epoch());

    assertSame(first, model.state());
  }

  @Test
  @DisplayName("A changed node is a changed request, and the model recomputes")
  void theModelRecomputesWhenTheRequestChanges() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch());
    PlanningState first = model.state();
    model.refresh(kourou(150.0), epoch());

    // Proves the memoisation test above is testing memoisation and not a fixed answer: with a
    // genuinely different request the state is a fresh instance, not the one already held.
    assertNotSame(first, model.state());
    assertInstanceOf(PlanningState.Windows.class, model.state());
  }

  @Test
  @DisplayName("A plane the pad cannot reach is a stated reason, not a crash")
  void anUnreachablePlaneIsReported() {
    PlanningModel model = new PlanningModel();
    // Assembled here rather than by the step, which filters this case into Gap.NO_TARGET before a
    // request exists: this is the planner's own precondition, and the guard that answers it.
    PlanningInputs impossible =
        PlanningInputs.of(
            new EarthLaunchWindowRequest(
                51.0,
                0.0,
                0.0,
                new LaunchPlane(FastMath.toRadians(5.0), NodeBranch.ASCENDING),
                120.0,
                Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0));
    model.refresh(impossible, epoch());

    PlanningState.Unavailable state =
        assertInstanceOf(PlanningState.Unavailable.class, model.state());
    // The model's own short line, not the exception's 155-character sentence with four degree
    // signs — none of which the bundled ibmPlexMono would draw.
    assertTrue(state.reason().length() <= 125, state.reason());
  }

  @Test
  @DisplayName("select moves the zoom to another opportunity")
  void selectMovesTheZoom() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch());

    model.select(1);

    PlanningState.Windows windows = assertInstanceOf(PlanningState.Windows.class, model.state());
    assertEquals(1, windows.selected());
  }

  @Test
  @DisplayName("select keeps the requested date, which the axis draws its origin from")
  void selectKeepsTheFloor() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch());

    model.select(2);

    PlanningState.Windows windows = assertInstanceOf(PlanningState.Windows.class, model.state());
    assertEquals(epoch(), windows.floor());
  }

  @Test
  @DisplayName("select out of range is ignored")
  void selectOutOfRangeIsIgnored() {
    PlanningModel model = new PlanningModel();
    model.refresh(kourou(120.0), epoch());

    model.select(PlanningModel.OPPORTUNITIES_SHOWN);
    model.select(-1);

    PlanningState.Windows windows = assertInstanceOf(PlanningState.Windows.class, model.state());
    assertEquals(0, windows.selected());
  }

  /**
   * MIS-4 / L5 §4.1 — the axis draws a lunar criterion through the same model, and the request
   * being a record is what keeps the memoisation biting on a page redrawn every frame.
   */
  @Test
  @DisplayName("A lunar request produces opportunities on the same axis")
  void aLunarRequestIsPlannedToo() {
    PlanningModel model = new PlanningModel();
    model.refresh(lunar(100_000.0), epoch());

    PlanningState.Windows windows = assertInstanceOf(PlanningState.Windows.class, model.state());
    assertFalse(windows.windows().isEmpty(), "the lunar geometry must offer a slot");

    PlanningState held = model.state();
    model.refresh(lunar(100_000.0), epoch());
    assertSame(held, model.state(), "an unchanged lunar request must not search again");
    model.refresh(lunar(200_000.0), epoch());
    assertNotSame(held, model.state(), "a different perilune is a different request");
  }

  /**
   * MIS-4 / L5 §4.1 — what keeps the page at its terrestrial cost. Confirming a candidate flies the
   * aim: 4.5 s apiece, on the render thread, in a loop polled every frame. The screening problem
   * carries no vehicle and hands the candidate straight back.
   */
  @Test
  @DisplayName("The screening problem never confirms")
  void theScreeningProblemNeverConfirms() {
    LunarLaunchWindowProblem problem =
        LunarLaunchWindowProblem.screening(28.562, -80.577, 3.0, 400_000.0, 100_000.0);
    LaunchWindowCandidate candidate = LaunchWindowCandidate.of(epoch(), 3_100.0);

    assertSame(candidate, problem.confirm(candidate));
  }

  @Test
  @DisplayName("select is ignored when the state is not Windows, so a caller need not test first")
  void selectIsIgnoredOutsideWindows() {
    PlanningModel model = new PlanningModel();
    model.refresh(missing(PlanningInputs.Gap.NO_NODE), epoch());

    model.select(0);

    assertInstanceOf(PlanningState.Idle.class, model.state());
  }
}
