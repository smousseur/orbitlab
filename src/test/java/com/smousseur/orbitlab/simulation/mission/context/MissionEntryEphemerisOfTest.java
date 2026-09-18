package com.smousseur.orbitlab.simulation.mission.context;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.FollowedObject;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * {@link MissionEntry#ephemerisOf(FollowedObject)} — the SEL-1 / L1 resolver from a {@link
 * FollowedObject} to the object's display ephemeris. No Orekit needed: the fixtures build points
 * directly, like {@code MissionTimelineVisibilityTest}.
 */
class MissionEntryEphemerisOfTest {

  /** Minimal {@link Mission}: {@code ephemerisOf} never touches it. */
  private static final class StubMission extends Mission {
    StubMission(String name) {
      super(name, null, null, null);
    }

    @Override
    public SpacecraftState getInitialState(AbsoluteDate initialDate) {
      throw new UnsupportedOperationException("Stub mission has no propagation");
    }
  }

  private static MissionEphemeris newEphemeris() {
    MissionEphemerisPoint p0 = point(AbsoluteDate.J2000_EPOCH);
    MissionEphemerisPoint p1 = point(AbsoluteDate.J2000_EPOCH.shiftedBy(10.0));
    return new MissionEphemeris(List.of(p0, p1));
  }

  private static MissionEphemerisPoint point(AbsoluteDate time) {
    return new MissionEphemerisPoint(
        time,
        new Vector3D(7.0e6, 0, 0),
        new Vector3D(0, 7500, 0),
        "phase",
        false,
        5.0e5,
        0,
        TrajectoryArc.earth());
  }

  @Test
  void primaryResolvesToThePrimaryEphemeris() {
    MissionEntry entry = new MissionEntry(new StubMission("m"));
    MissionEphemeris primary = newEphemeris();
    entry.setEphemeris(primary);

    assertSame(primary, entry.ephemerisOf(new FollowedObject.Primary(entry.id())).orElseThrow());
  }

  @Test
  void debrisResolvesToItsOwnTrackByRoleAndExemplar() {
    MissionEntry entry = new MissionEntry(new StubMission("m"));
    MissionEphemeris booster1 = newEphemeris();
    MissionEphemeris booster2 = newEphemeris();
    MissionEphemeris core = newEphemeris();
    entry.setDebris(
        List.of(
            new DebrisTrack(booster1, StageRole.BOOSTER, 1),
            new DebrisTrack(booster2, StageRole.BOOSTER, 2),
            new DebrisTrack(core, StageRole.CORE, 1)));

    assertSame(
        booster1,
        entry
            .ephemerisOf(new FollowedObject.Debris(entry.id(), StageRole.BOOSTER, 1))
            .orElseThrow());
    assertSame(
        booster2,
        entry
            .ephemerisOf(new FollowedObject.Debris(entry.id(), StageRole.BOOSTER, 2))
            .orElseThrow());
    assertSame(
        core,
        entry.ephemerisOf(new FollowedObject.Debris(entry.id(), StageRole.CORE, 1)).orElseThrow());
  }

  @Test
  void debrisThisMissionDoesNotCarryIsEmpty() {
    MissionEntry entry = new MissionEntry(new StubMission("m"));
    entry.setDebris(List.of(new DebrisTrack(newEphemeris(), StageRole.BOOSTER, 1)));

    assertTrue(
        entry.ephemerisOf(new FollowedObject.Debris(entry.id(), StageRole.UPPER, 1)).isEmpty());
  }

  @Test
  void primaryWithoutAnEphemerisIsEmpty() {
    MissionEntry entry = new MissionEntry(new StubMission("m"));

    assertTrue(entry.ephemerisOf(new FollowedObject.Primary(entry.id())).isEmpty());
  }
}
