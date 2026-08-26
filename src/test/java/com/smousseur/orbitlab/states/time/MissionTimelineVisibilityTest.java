package com.smousseur.orbitlab.states.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/** The five display conditions of the mission timeline (spec §10.1). */
class MissionTimelineVisibilityTest {

  /**
   * Minimal {@link Mission} stub. Only status is exercised by the visibility rules, so
   * vehicle/stages/objective are intentionally {@code null}. Copied verbatim from {@code
   * MissionDisplayPanelRulesTest} — that shape is known to compile against the real {@link Mission}
   * constructor and abstract method.
   */
  private static final class StubMission extends Mission {
    StubMission(String name) {
      super(name, null, null, null);
    }

    @Override
    public SpacecraftState getInitialState(AbsoluteDate initialDate) {
      throw new UnsupportedOperationException("Stub mission has no propagation");
    }
  }

  private static MissionEphemeris ephemeris(boolean complete) {
    AbsoluteDate t0 = AbsoluteDate.J2000_EPOCH;
    List<MissionEphemerisPoint> points =
        List.of(
            new MissionEphemerisPoint(
                t0,
                new Vector3D(7.0e6, 0, 0),
                new Vector3D(0, 7500, 0),
                "Ascent",
                true,
                5.0e5,
                0,
                TrajectoryArc.earth()),
            new MissionEphemerisPoint(
                t0.shiftedBy(600),
                new Vector3D(0, 7.0e6, 0),
                new Vector3D(-7500, 0, 0),
                "Coast",
                false,
                4.0e5,
                4.0e5,
                TrajectoryArc.earth()));
    return new MissionEphemeris(points, complete);
  }

  /** A mission entry that satisfies conditions 2 to 5, already focused. */
  private static MissionEntry focusedReadyEntry(MissionContext mc, boolean complete) {
    Mission m = new StubMission("GEO-1");
    m.setStatus(MissionStatus.READY);
    MissionEntry entry = new MissionEntry(m);
    entry.setVisible(true);
    entry.setEphemeris(ephemeris(complete));
    mc.addMission(entry);
    mc.setTelemetryFocusMissionId(entry.id());
    return entry;
  }

  @Test
  void aFocusedReadyVisibleMissionWithAnEphemerisIsAvailable() {
    MissionContext mc = new MissionContext();
    MissionEntry entry = focusedReadyEntry(mc, true);
    assertTrue(MissionTimelineVisibility.isAvailable(mc));
    assertSame(entry, MissionTimelineVisibility.availableMission(mc).orElseThrow());
  }

  @Test
  void withoutTelemetryFocusNothingIsAvailable() {
    MissionContext mc = new MissionContext();
    focusedReadyEntry(mc, true);
    mc.setTelemetryFocusMissionId(null);
    assertFalse(MissionTimelineVisibility.isAvailable(mc));
  }

  @Test
  void aMissionThatIsNotReadyIsNotAvailable() {
    MissionContext mc = new MissionContext();
    MissionEntry entry = focusedReadyEntry(mc, true);
    entry.mission().setStatus(MissionStatus.DRAFT);
    assertFalse(MissionTimelineVisibility.isAvailable(mc));
  }

  @Test
  void anInvisibleMissionIsNotAvailable() {
    MissionContext mc = new MissionContext();
    MissionEntry entry = focusedReadyEntry(mc, true);
    entry.setVisible(false);
    assertFalse(MissionTimelineVisibility.isAvailable(mc));
  }

  @Test
  void aReadyMissionWithoutAnEphemerisIsNotAvailable() {
    MissionContext mc = new MissionContext();
    MissionEntry entry = focusedReadyEntry(mc, true);
    entry.setEphemeris(null);
    assertFalse(MissionTimelineVisibility.isAvailable(mc));
  }

  @Test
  void aTruncatedFlightIsStillShown() {
    // The right bound gets a distinct terminator (§10.3); it does not remove the widget.
    MissionContext mc = new MissionContext();
    focusedReadyEntry(mc, false);
    assertTrue(MissionTimelineVisibility.isAvailable(mc));
  }

  @Test
  void theToggleIsTheFifthConditionAndIsIndependentOfTheOtherFour() {
    MissionContext mc = new MissionContext();
    focusedReadyEntry(mc, true);
    assertTrue(MissionTimelineVisibility.shownMission(mc, true).isPresent());
    assertFalse(MissionTimelineVisibility.shownMission(mc, false).isPresent());
  }
}
