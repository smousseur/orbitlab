package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.objective.FlybyObjective;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * MIS-4 / L3 §5.1 — the flyby branch of {@link ObjectiveEvaluator}, on synthetic ephemerides, with
 * no propagation.
 *
 * <p><b>The pivot fixture flies three arcs, and the lunar demo does not.</b> At its 4.5 d horizon
 * the demo stops inside the lunar sphere, so the arc it ends in happens to be the one an objective
 * is about and the old predicate's selection falls right by accident. The round trip below is the
 * case that accident does not survive — the sequence L4 will produce at ~7 d — and until L4 exists
 * it is pinned here and nowhere else.
 *
 * <p>Altitudes are the ones L0 §4 measured on day 0 of the demo: 100.4 km at perilune and 67 348 km
 * at the lunar sphere boundary, so the fixture describes a flight that happened rather than one
 * that was invented.
 */
class ObjectiveEvaluatorTest {

  private static final double TOL = MissionLoadEvaluator.DEFAULT_OBJECTIVE_TOLERANCE_RATIO;
  private static final double PERILUNE = 100_400.0;
  private static final double LUNAR_SPHERE_ALTITUDE = 67_348_000.0;
  private static final double BAND = 10_000.0;

  /**
   * The lunar arc of the fixture carries the Moon-centred frame the flight would really record, and
   * resolving it goes through {@code OrekitService}. Nothing here propagates; this is the archive
   * load and nothing else.
   */
  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
  }

  /**
   * A round trip: geocentric outbound, a lunar arc down to {@code perilune} and back out, then a
   * geocentric return. Every sample is a coast, so the insertion predicate sees the same points the
   * flyby one does and the two can be contrasted on one flight.
   */
  private static MissionEphemeris roundTrip(double perilune) {
    List<MissionEphemerisPoint> points = new ArrayList<>();
    AbsoluteDate t = AbsoluteDate.J2000_EPOCH;
    TrajectoryArc earth = TrajectoryArc.earth();
    TrajectoryArc moon = TrajectoryArc.forBody(SolarSystemBody.MOON);
    points.add(coastPoint(t, 200_000.0, earth));
    points.add(coastPoint(t.shiftedBy(60), 320_000_000.0, earth));
    points.add(coastPoint(t.shiftedBy(120), LUNAR_SPHERE_ALTITUDE, moon));
    points.add(coastPoint(t.shiftedBy(180), perilune, moon));
    points.add(coastPoint(t.shiftedBy(240), LUNAR_SPHERE_ALTITUDE, moon));
    points.add(coastPoint(t.shiftedBy(300), 340_000_000.0, earth));
    points.add(coastPoint(t.shiftedBy(360), 210_000.0, earth));
    return new MissionEphemeris(points);
  }

  private static MissionEphemerisPoint coastPoint(
      AbsoluteDate date, double altitude, TrajectoryArc arc) {
    return new MissionEphemerisPoint(
        date, Vector3D.ZERO, Vector3D.ZERO, "Coasting", false, 1_000.0, altitude, arc);
  }

  @Test
  void flyby_isMeasuredOnTheTargetBodyWhereverItsArcFallsInTheSequence() {
    assertTrue(
        ObjectiveEvaluator.met(
            roundTrip(PERILUNE), new FlybyObjective(SolarSystemBody.MOON, 100_000.0, BAND), TOL),
        "the lunar arc is neither the first nor the last of the flight, and must still be the one"
            + " measured");
  }

  @Test
  void flyby_offTargetClosestApproach_false() {
    assertFalse(
        ObjectiveEvaluator.met(
            roundTrip(140_000.0), new FlybyObjective(SolarSystemBody.MOON, 100_000.0, BAND), TOL));
  }

  /**
   * The contrast, and the only executable trace of what L3 corrects: the same flight, judged by the
   * objective the lunar demo carried before this lot.
   *
   * <p>An insertion objective measures the arc the terminal coast <em>ends</em> in, which on a
   * round trip is the geocentric return. The lunar arrival is not looked at at all — so the flyby
   * is not scored loosely, it is not scored.
   */
  @Test
  void insertion_onTheSameFlight_neverLooksAtTheLunarArc() {
    MissionEphemeris flight = roundTrip(PERILUNE);
    assertFalse(
        ObjectiveEvaluator.met(
            flight, OrbitInsertionObjective.circular(SolarSystemBody.MOON, 100_000.0, 0.0), TOL),
        "a perfectly flown perilune must be reported as missed by the objective it replaces —"
            + " otherwise this lot corrects nothing");
  }

  @Test
  void flyby_bodyNeverReached_false() {
    assertFalse(
        ObjectiveEvaluator.met(
            roundTrip(PERILUNE), new FlybyObjective(SolarSystemBody.MARS, 100_000.0, BAND), TOL),
        "a flight that never reached the body must be distinguishable from one that flew past it");
  }

  /**
   * An impact is refused whatever band was declared. The band here is wide enough to swallow the
   * −53 km the first version of the translunar aim computed and flew (spec §12, reported by {@code
   * LunarTransferFlightTest.theAimConvergesOrRefusesAcrossALunarMonth}), which is the point: the
   * property must not rest on the tolerance being smaller than the target.
   */
  @Test
  void flyby_impact_falseEvenUnderABandThatWouldCoverIt() {
    assertFalse(
        ObjectiveEvaluator.met(
            roundTrip(-53_000.0),
            new FlybyObjective(SolarSystemBody.MOON, 100_000.0, 200_000.0),
            TOL));
  }

  @Test
  void flyby_targetingTheBodyTheFlightStartsAt_throws() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ObjectiveEvaluator.met(
                    roundTrip(PERILUNE),
                    new FlybyObjective(SolarSystemBody.EARTH, 200_000.0, BAND),
                    TOL));
    assertTrue(
        thrown.getMessage().contains("starts at"),
        "the refusal must say what is wrong with the objective, got: " + thrown.getMessage());
  }

  /** The insertion branch is a delegation and must stay one, on both answers. */
  @Test
  void insertion_delegatesToMissionLoadEvaluator() {
    OrbitInsertionObjective leo =
        OrbitInsertionObjective.circular(SolarSystemBody.EARTH, 400_000, 0.0);
    for (MissionEphemeris flight :
        List.of(earthCoast(399_000.0, 401_000.0), earthCoast(360_000.0, 401_000.0))) {
      boolean delegate = MissionLoadEvaluator.objectiveMet(flight, leo, TOL);
      assertEquals(
          delegate,
          ObjectiveEvaluator.met(flight, leo, TOL),
          "insertion scoring must not be reimplemented");
    }
    // And the two answers really are different, so the loop above compared something.
    assertTrue(ObjectiveEvaluator.met(earthCoast(399_000.0, 401_000.0), leo, TOL));
    assertFalse(ObjectiveEvaluator.met(earthCoast(360_000.0, 401_000.0), leo, TOL));
  }

  /** A single-arc terminal coast at the given altitudes — the shape every Earth mission flies. */
  private static MissionEphemeris earthCoast(double... altitudes) {
    List<MissionEphemerisPoint> points = new ArrayList<>();
    AbsoluteDate t = AbsoluteDate.J2000_EPOCH;
    for (int i = 0; i < altitudes.length; i++) {
      points.add(coastPoint(t.shiftedBy(60L * i), altitudes[i], TrajectoryArc.earth()));
    }
    return new MissionEphemeris(points);
  }

  /** A flyby objective cannot describe a target below the surface in the first place. */
  @Test
  void flybyObjective_rejectsATargetUnderTheSurface() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FlybyObjective(SolarSystemBody.MOON, -1_000.0, BAND));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FlybyObjective(SolarSystemBody.MOON, 100_000.0, 0.0));
  }
}
