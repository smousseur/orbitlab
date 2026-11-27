package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.FramesFactory;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The burns a timeline reads off a raw ephemeris: where they start and end, which stage flies them,
 * and the figures that classify them.
 *
 * <p>The vehicle is a two-stage stack sized for round mass flows: the lower stage burns 10 kg/s at
 * 300 s, the upper one 2 kg/s at 400 s. The full stack weighs 6 000 kg and the upper stage 1 000
 * kg, so any mass above 1 000 kg resolves to the lower stage.
 */
class BurnTimelineTest {

  private static final double G0 = 9.80665;
  private static final double LOWER_ISP = 300.0;
  private static final double UPPER_ISP = 400.0;
  private static final double TOLERANCE_S = 1e-9;

  private static final Vehicle STACK =
      new VehicleStack(
          List.of(
              new LaunchVehicle(
                  1_000.0, 4_000.0, new PropulsionSystem(LOWER_ISP, 10.0 * LOWER_ISP * G0)),
              new LaunchVehicle(
                  200.0, 800.0, new PropulsionSystem(UPPER_ISP, 2.0 * UPPER_ISP * G0))),
          new StagingPlan(List.of(StageRole.CORE, StageRole.UPPER), null));

  private static final Vector3D POSITION = new Vector3D(7_000_000.0, 0.0, 0.0);
  private static final Vector3D VELOCITY = new Vector3D(0.0, 7_500.0, 0.0);

  private static AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void aStageBoundaryWithoutMassLossDoesNotSplitABurn() {
    BurnTimeline timeline =
        timelineOf(
            at(0, "Vertical Ascent", true, 6_000),
            at(1, "Vertical Ascent", true, 5_990),
            at(2, "Vertical Ascent", true, 5_980),
            at(2, "Vertical Ascent", true, 5_980),
            at(2, "Gravity turn", true, 5_980),
            at(3, "Gravity turn", true, 5_970),
            at(4, "Gravity turn", true, 5_960),
            at(5, "Coasting", false, 5_960),
            at(6, "Coasting", false, 5_960));

    assertEquals(1, timeline.burns().size());
    assertBounds(timeline.burns().get(0), 0.0, 4.0);
  }

  @Test
  void aJettisonEndsTheBurnAndTheNextOneFliesTheNextStage() {
    BurnTimeline timeline =
        timelineOf(
            at(0, "S1", true, 6_000),
            at(1, "S1", true, 5_990),
            at(2, "S1", true, 5_980),
            at(2, "Separation", false, 1_000),
            at(2, "S2", true, 1_000),
            at(3, "S2", true, 998),
            at(4, "S2", true, 996),
            at(5, "Coasting", false, 996));

    List<BurnTimeline.Burn> burns = timeline.burns();
    assertEquals(2, burns.size());
    assertBounds(burns.get(0), 0.0, 2.0);
    assertEquals(0, burns.get(0).stageIndex());
    assertEquals(StageRole.CORE, burns.get(0).role());
    assertBounds(burns.get(1), 2.0, 4.0);
    assertEquals(1, burns.get(1).stageIndex());
    assertEquals(StageRole.UPPER, burns.get(1).role());
  }

  @Test
  void aZeroFlowGapOfOneMillisecondEndsTheBurn() {
    BurnTimeline timeline =
        timelineOf(
            at(0, "S1", true, 6_000),
            at(1, "S1", true, 5_990),
            at(2, "S1", true, 5_980),
            at(2.001, "S1", true, 5_980),
            at(3.001, "S1", true, 5_970),
            at(4.001, "S1", true, 5_960),
            at(5.001, "Coasting", false, 5_960));

    List<BurnTimeline.Burn> burns = timeline.burns();
    assertEquals(2, burns.size());
    assertBounds(burns.get(0), 0.0, 2.0);
    assertBounds(burns.get(1), 2.001, 4.001);
  }

  @Test
  void aNonPropulsiveStageNeverBurns() {
    BurnTimeline timeline =
        timelineOf(
            at(0, "Coasting", false, 6_000),
            at(60, "Coasting", false, 5_990),
            at(120, "Coasting", false, 5_990));

    assertTrue(timeline.burns().isEmpty());
  }

  @Test
  void partialEdgePairsPlaceIgnitionAndCutoffInsideTheirStep() {
    BurnTimeline timeline = timelineOf(partialEdgesBurn());

    assertEquals(1, timeline.burns().size());
    assertBounds(timeline.burns().get(0), 1.6, 4.7);
  }

  @Test
  void aBurnWithinASinglePairIsCentredInItsStep() {
    BurnTimeline timeline =
        timelineOf(
            at(0, "Trim", true, 6_000),
            at(1, "Trim", true, 6_000),
            at(2, "Trim", true, 5_996),
            at(3, "Trim", true, 5_996));

    assertEquals(1, timeline.burns().size());
    BurnTimeline.Burn burn = timeline.burns().get(0);
    assertBounds(burn, 1.3, 1.7);
    assertFalse(burn.hasInteriorFlow());
  }

  @Test
  void theInteriorFlowRangeExcludesTheEdgesAndRevealsAThrottle() {
    BurnTimeline timeline =
        timelineOf(
            at(0, "S1", true, 6_000),
            at(1, "S1", true, 5_995),
            at(2, "S1", true, 5_985),
            at(3, "S1", true, 5_976.9),
            at(4, "S1", true, 5_966.9),
            at(5, "S1", true, 5_960.9),
            at(6, "S1", true, 5_960.9));

    BurnTimeline.Burn burn = timeline.burns().get(0);
    assertTrue(burn.hasInteriorFlow());
    assertEquals(0.81, burn.minInteriorFlowRatio(), 1e-9);
    assertEquals(1.0, burn.maxInteriorFlowRatio(), 1e-9);
  }

  @Test
  void aBurnAlongTheVelocityHasCosThetaOne() {
    assertEquals(1.0, cosThetaOfAColinearBurn(+1.0), 1e-12);
  }

  @Test
  void aBurnAgainstTheVelocityHasCosThetaMinusOne() {
    assertEquals(-1.0, cosThetaOfAColinearBurn(-1.0), 1e-12);
  }

  /**
   * Half the impulse turns the velocity at constant speed, half adds to it: 100 m/s each, from 1
   * 000 m/s. Summed per sample the estimator gives (0 + 1 050·100) / (1 000·100 + 1 050·100) =
   * 21/41; the end-point form would give 105 000 / (1 050·200) = 1/2.
   */
  @Test
  void cosThetaIsWeightedSampleBySample() {
    double c = UPPER_ISP * G0;
    double m1 = 1_000.0 * Math.exp(-100.0 / c);
    double m2 = m1 * Math.exp(-100.0 / c);
    double phi = 0.1;
    Vector3D v0 = new Vector3D(0.0, 1_000.0, 0.0);
    Vector3D v1 = new Vector3D(0.0, 1_000.0 * Math.cos(phi), 1_000.0 * Math.sin(phi));
    Vector3D v2 = v1.scalarMultiply(1.1);
    BurnTimeline timeline =
        timelineOf(
            at(0, "S2", true, 1_000.0, POSITION, v0, TrajectoryArc.earth()),
            at(1, "S2", true, m1, POSITION, v1, TrajectoryArc.earth()),
            at(2, "S2", true, m2, POSITION, v2, TrajectoryArc.earth()),
            at(3, "S2", true, m2, POSITION, v2, TrajectoryArc.earth()));

    assertEquals(21.0 / 41.0, timeline.burns().get(0).cosTheta(), 1e-12);
  }

  @Test
  void cosThetaUsesTheGravitationalParameterOfTheArcsBody() {
    double c = UPPER_ISP * G0;
    double m1 = 1_000.0 * Math.exp(-100.0 / c);
    double m2 = m1 * Math.exp(-100.0 / c);
    TrajectoryArc moon = new TrajectoryArc(SolarSystemBody.MOON, FramesFactory.getGCRF());
    Vector3D v = new Vector3D(0.0, 1_600.0, 0.0);
    BurnTimeline timeline =
        timelineOf(
            at(0, "Insertion", true, 1_000.0, new Vector3D(1.80e6, 0, 0), v, moon),
            at(1, "Insertion", true, m1, new Vector3D(1.81e6, 0, 0), v, moon),
            at(2, "Insertion", true, m2, new Vector3D(1.82e6, 0, 0), v, moon),
            at(3, "Insertion", true, m2, new Vector3D(1.82e6, 0, 0), v, moon));

    double muMoon = GravitationalContext.moon().mu();
    double expected = muMoon * (1.0 / 1.80e6 - 1.0 / 1.82e6) / (1_600.0 * 200.0);
    assertEquals(expected, timeline.burns().get(0).cosTheta(), 1e-12);
  }

  @Test
  void deltaVFollowsTsiolkovskyOnTheActiveStage() {
    BurnTimeline timeline =
        timelineOf(
            at(0, "S1", true, 6_000),
            at(1, "S1", true, 5_990),
            at(2, "S1", true, 5_980),
            at(3, "S1", true, 5_970),
            at(4, "S1", true, 5_960),
            at(5, "S1", true, 5_950),
            at(6, "Coasting", false, 5_950));

    BurnTimeline.Burn burn = timeline.burns().get(0);
    assertEquals(LOWER_ISP * G0 * Math.log(6_000.0 / 5_950.0), burn.deltaV(), 1e-9);
  }

  @Test
  void burnAtIsHalfOpenOnTheRecalibratedBounds() {
    BurnTimeline timeline = timelineOf(partialEdgesBurn());
    BurnTimeline.Burn burn = timeline.burns().get(0);

    assertTrue(timeline.burnAt(burn.start()).isPresent());
    assertFalse(timeline.burnAt(burn.end()).isPresent());
    assertFalse(timeline.burnAt(epoch.shiftedBy(1.59)).isPresent());
    assertTrue(timeline.burnAt(epoch.shiftedBy(1.6)).isPresent());
    assertTrue(timeline.burnAt(epoch.shiftedBy(3.0)).isPresent());
    assertTrue(timeline.burnAt(epoch.shiftedBy(4.69)).isPresent());
    assertFalse(timeline.burnAt(epoch.shiftedBy(4.7)).isPresent());
  }

  /** A burn opening at 0.4 of nominal flow and closing at 0.7, after and before a coast. */
  private static MissionEphemerisPoint[] partialEdgesBurn() {
    return new MissionEphemerisPoint[] {
      at(0, "Transfer", true, 6_000),
      at(1, "Transfer", true, 6_000),
      at(2, "Transfer", true, 5_996),
      at(3, "Transfer", true, 5_986),
      at(4, "Transfer", true, 5_976),
      at(5, "Transfer", true, 5_969),
      at(6, "Transfer", true, 5_969)
    };
  }

  /**
   * A two-pair upper-stage burn whose speed changes by exactly the Tsiolkovsky ΔV of each pair, in
   * the given sense, at a fixed position.
   */
  private static double cosThetaOfAColinearBurn(double sense) {
    double c = UPPER_ISP * G0;
    double[] masses = {1_000.0, 990.0, 980.0};
    List<MissionEphemerisPoint> points = new ArrayList<>();
    double speed = 7_500.0;
    for (int i = 0; i < masses.length; i++) {
      if (i > 0) {
        speed += sense * c * Math.log(masses[i - 1] / masses[i]);
      }
      points.add(
          at(
              i,
              "S2",
              true,
              masses[i],
              POSITION,
              new Vector3D(0.0, speed, 0.0),
              TrajectoryArc.earth()));
    }
    points.add(
        at(3, "S2", true, 980.0, POSITION, new Vector3D(0.0, speed, 0.0), TrajectoryArc.earth()));
    return BurnTimeline.of(new MissionEphemeris(points), STACK).burns().get(0).cosTheta();
  }

  private static BurnTimeline timelineOf(MissionEphemerisPoint... points) {
    return BurnTimeline.of(new MissionEphemeris(List.of(points)), STACK);
  }

  private static void assertBounds(BurnTimeline.Burn burn, double start, double end) {
    assertEquals(start, burn.start().durationFrom(epoch), TOLERANCE_S, "start");
    assertEquals(end, burn.end().durationFrom(epoch), TOLERANCE_S, "end");
  }

  private static MissionEphemerisPoint at(
      double seconds, String stage, boolean propulsive, double mass) {
    return at(seconds, stage, propulsive, mass, POSITION, VELOCITY, TrajectoryArc.earth());
  }

  private static MissionEphemerisPoint at(
      double seconds,
      String stage,
      boolean propulsive,
      double mass,
      Vector3D position,
      Vector3D velocity,
      TrajectoryArc arc) {
    return new MissionEphemerisPoint(
        epoch.shiftedBy(seconds), position, velocity, stage, propulsive, mass, 400_000.0, arc);
  }
}
