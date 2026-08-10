package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * {@link MissionEphemeris#displayPointAt} is the single answer to "where is this spacecraft now?",
 * shared by the floating-origin state and the mission orchestrator so the two cannot disagree (spec
 * {@code docs/graphics-effects/spacecraft-view-artefacts.md} §9.1). It must therefore answer for
 * <em>any</em> date, including outside the recorded span, where {@link
 * MissionEphemeris#interpolate} is not meant to be called.
 */
class MissionEphemerisDisplayPointTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;

  /** Three samples 10 s apart, drifting along X at a constant 100 m/s from 7000 km. */
  private static MissionEphemeris straightDrift() {
    Vector3D velocity = new Vector3D(100.0, 0.0, 0.0);
    List<MissionEphemerisPoint> points =
        List.of(
            point(0, 7_000_000.0, velocity),
            point(10, 7_001_000.0, velocity),
            point(20, 7_002_000.0, velocity));
    return new MissionEphemeris(points);
  }

  private static MissionEphemerisPoint point(double seconds, double x, Vector3D velocity) {
    return new MissionEphemerisPoint(
        T0.shiftedBy(seconds), new Vector3D(x, 0, 0), velocity, "drift", false, 1_000.0, 621_000.0);
  }

  @Test
  void beforeLaunchTheSpacecraftIsOnItsPad() {
    MissionEphemeris eph = straightDrift();

    assertEquals(
        eph.firstPoint().position(), eph.displayPointAt(T0.shiftedBy(-3_600.0)).position());
    assertEquals(eph.firstPoint().position(), eph.displayPointAt(T0).position());
  }

  @Test
  void withinTheSpanThePositionIsInterpolated() {
    MissionEphemeris eph = straightDrift();

    Vector3D midway = eph.displayPointAt(T0.shiftedBy(5.0)).position();

    assertEquals(7_000_500.0, midway.getX(), 1e-3, "halfway through a constant-rate drift");
  }

  @Test
  void pastTheEndTheSpacecraftStaysWhereTheMissionEnded() {
    MissionEphemeris eph = straightDrift();

    assertEquals(eph.lastPoint().position(), eph.displayPointAt(T0.shiftedBy(20.0)).position());
    assertEquals(eph.lastPoint().position(), eph.displayPointAt(T0.shiftedBy(86_400.0)).position());
  }
}
