package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.core.SolarSystemBody;
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
    return point(seconds, x, velocity, TrajectoryArc.earth());
  }

  private static MissionEphemerisPoint point(
      double seconds, double x, Vector3D velocity, TrajectoryArc arc) {
    return new MissionEphemerisPoint(
        T0.shiftedBy(seconds), new Vector3D(x, 0, 0), velocity, "drift", false, 1_000.0, 621_000.0,
        arc);
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

  // ════════════════════════════════════════════════════════════════════════
  // Across an arc boundary (PHY-4 / L3, spec docs/multi-corps/05-conception-L3.md §3.3)
  // ════════════════════════════════════════════════════════════════════════

  /** The same drift, with the middle sample opening a second arc. */
  private static MissionEphemeris driftAcrossAnArcBoundary() {
    Vector3D velocity = new Vector3D(100.0, 0.0, 0.0);
    TrajectoryArc other =
        new TrajectoryArc(SolarSystemBody.MOON, TrajectoryArc.earth().frame());
    return new MissionEphemeris(
        List.of(
            point(0, 7_000_000.0, velocity),
            point(10, 7_001_000.0, velocity, other),
            point(20, 7_002_000.0, velocity, other)));
  }

  /**
   * <b>Nothing is interpolated across a change of frame.</b> A cubic Hermite between a geocentric
   * vector and a selenocentric one is not an approximation, it is meaningless. The bracketing sample
   * is returned unchanged instead — the floor semantics this method already applies to the stage name
   * and the mass, extended to the arc.
   *
   * <p>Compare with {@link #withinTheSpanThePositionIsInterpolated()}, which is the same geometry
   * inside one arc and does land halfway.
   */
  @Test
  void acrossAnArcBoundaryNothingIsInterpolated() {
    MissionEphemeris eph = driftAcrossAnArcBoundary();

    MissionEphemerisPoint midway = eph.displayPointAt(T0.shiftedBy(5.0));

    assertEquals(
        7_000_000.0,
        midway.position().getX(),
        0.0,
        "the outgoing sample, exactly — not a blend of two frames");
    assertEquals(TrajectoryArc.earth(), midway.arc(), "and it is still in the outgoing arc");
  }

  /**
   * The consequence that matters to the renderer: the arc — and therefore the render context the
   * three drawing states derive from it — flips <em>atomically</em> at the incoming sample. There is
   * no instant at which one of them could hold the old arc and another the new one.
   */
  @Test
  void theArcFlipsAtTheIncomingSampleAndNotBefore() {
    MissionEphemeris eph = driftAcrossAnArcBoundary();

    assertEquals(TrajectoryArc.earth(), eph.displayPointAt(T0.shiftedBy(9.999)).arc());
    assertEquals(SolarSystemBody.MOON, eph.displayPointAt(T0.shiftedBy(10.0)).arc().body());
    assertEquals(SolarSystemBody.MOON, eph.displayPointAt(T0.shiftedBy(15.0)).arc().body());
  }

  /** Inside the second arc, interpolation resumes: the guard is per interval, not per ephemeris. */
  @Test
  void insideTheSecondArcInterpolationResumes() {
    MissionEphemeris eph = driftAcrossAnArcBoundary();

    assertEquals(
        7_001_500.0,
        eph.displayPointAt(T0.shiftedBy(15.0)).position().getX(),
        1e-3,
        "halfway between the two samples of the second arc");
  }
}
