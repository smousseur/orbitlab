package com.smousseur.orbitlab.simulation.mission.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.disposal.DeorbitSequence;
import com.smousseur.orbitlab.simulation.mission.disposal.DeorbitTail;
import com.smousseur.orbitlab.simulation.mission.disposal.DisposalFixtures;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.runtime.AchievedOrbit;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizerResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPerformanceReport;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageNames;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;

/**
 * The hook {@link MissionPlanOptimizer#compute()} ends on: a plan whose mission carries a disposal
 * tail comes back with the tail flown past the horizon — followed by the payload's fall to the
 * ground when the tail leaves it falling — and any other plan comes back untouched.
 *
 * <p>Built on a hand-made plan rather than a planner run: what is under test is the hook, and the
 * planners in front of it are the slow part.
 */
class MissionPlanDisposalTailTest {

  private static final PayloadModel MODEL = Payloads.EARTH_OBSERVATION_SAT;

  private static final double ORBIT_ALTITUDE = 400_000.0;

  /** The altitude below which the renderer reads a last sample as landed. */
  private static final double LANDED_ALTITUDE_METERS = 1000.0;

  /** Coast of the hand-made plan's own chain, before its horizon (s). */
  private static final double TERMINAL_COAST_SECONDS = 600.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void aPlanWithoutATailIsReturnedUntouched() {
    MissionPlan plan =
        computedPlan(
            DisposalFixtures::payloadMission,
            sizedReserve(),
            circularInsertion(sizedReserve()),
            TERMINAL_COAST_SECONDS);

    assertSame(plan, MissionPlanOptimizer.withDisposalTail(plan));
  }

  @Test
  void aTailReachingItsTargetIsFollowedByTheFallToTheGround() {
    MissionPlan plan =
        computedPlan(
            DisposalFixtures::payloadMissionWithTail,
            sizedReserve(),
            circularInsertion(sizedReserve()),
            TERMINAL_COAST_SECONDS);
    MissionComputeResult before = plan.computation();
    Mission mission = before.mission();
    SpacecraftState horizon = mission.getCurrentState();
    assertEquals(DeorbitSequence.End.TARGET_REACHED, planFrom(horizon, mission).end());

    MissionPlan extended = MissionPlanOptimizer.withDisposalTail(plan);

    MissionComputeResult after = extended.computation();
    assertSame(before.optimizerResult(), after.optimizerResult());
    assertSame(before.performanceReport(), after.performanceReport());
    assertSame(mission, after.mission());
    assertSame(before.achievedOrbit(), after.achievedOrbit());
    assertEquals(before.debris(), after.debris());
    assertSame(plan.sizing(), extended.sizing());
    assertSame(horizon, mission.getCurrentState(), "the horizon state is left where it was");

    List<MissionEphemerisPoint> original = before.ephemeris().allPoints();
    List<MissionEphemerisPoint> points = after.ephemeris().allPoints();
    assertEquals(original, points.subList(0, original.size()), "the mission's own trajectory");
    assertEquals(horizon.getDate(), points.get(original.size()).time(), "the tail opens there");
    assertEquals(StageNames.DEORBIT_COAST, points.get(original.size()).stageName());

    int fallFrom = firstIndexOf(points, StageNames.REENTRY);
    assertEquals(StageNames.DEORBIT_BURN, points.get(fallFrom - 1).stageName());
    assertEquals(
        points.get(fallFrom - 1).time(), points.get(fallFrom).time(), "the fall opens there");
    assertTrue(
        points.subList(fallFrom, points.size()).stream()
            .allMatch(point -> StageNames.REENTRY.equals(point.stageName())),
        "nothing follows the fall");
    assertTrue(
        points.getLast().altitudeMeters() < LANDED_ALTITUDE_METERS,
        "the trajectory ends on the ground, not at " + points.getLast().altitudeMeters() + " m");
    assertTrue(after.ephemeris().isComplete());
  }

  @Test
  void aTailRunningOutOfPropellantEndsOnItsLastBurnAsBefore() {
    double reserve = 50.0;
    MissionPlan plan =
        computedPlan(
            DisposalFixtures::payloadMissionWithTail,
            reserve,
            circularInsertion(reserve),
            TERMINAL_COAST_SECONDS);
    Mission mission = plan.computation().mission();
    assertEquals(
        DeorbitSequence.End.PROPELLANT_SPENT, planFrom(mission.getCurrentState(), mission).end());

    MissionEphemeris ephemeris =
        MissionPlanOptimizer.withDisposalTail(plan).computation().ephemeris();

    assertEquals(StageNames.DEORBIT_BURN, ephemeris.lastPoint().stageName());
    assertTrue(
        ephemeris.allPoints().stream()
            .noneMatch(point -> StageNames.REENTRY.equals(point.stageName())),
        "no fall follows a tail that left the payload in orbit");
    assertTrue(ephemeris.isComplete());
  }

  @Test
  void aTailWithoutBurnOnAFallingPayloadFliesTheFallAlone() {
    double reserve = 300.0;
    // Heading for a 20 km perigee: the atmosphere takes it before it can climb back to an apogee,
    // so the tail stops before its first burn.
    SpacecraftState insertion =
        DisposalFixtures.orbitState(20_000.0, 400_000.0, 300.0, MODEL.defaultDryMass() + reserve);
    MissionPlan plan =
        computedPlan(DisposalFixtures::payloadMissionWithTail, reserve, insertion, 60.0);
    MissionComputeResult before = plan.computation();
    Mission mission = before.mission();
    SpacecraftState horizon = mission.getCurrentState();
    DeorbitSequence sequence = planFrom(horizon, mission);
    assertEquals(DeorbitSequence.End.FELL_BEFORE_NEXT_BURN, sequence.end());
    assertTrue(sequence.stages().isEmpty());

    MissionEphemeris ephemeris =
        MissionPlanOptimizer.withDisposalTail(plan).computation().ephemeris();

    List<MissionEphemerisPoint> original = before.ephemeris().allPoints();
    List<MissionEphemerisPoint> appended =
        ephemeris.allPoints().subList(original.size(), ephemeris.size());
    assertEquals(horizon.getDate(), appended.getFirst().time(), "the fall opens on the horizon");
    assertTrue(
        appended.stream().allMatch(point -> StageNames.REENTRY.equals(point.stageName())),
        "only the fall is appended");
    assertTrue(ephemeris.lastPoint().altitudeMeters() < LANDED_ALTITUDE_METERS);
  }

  /** The tail the hook will plan, planned here from the same state to read how it ends. */
  private static DeorbitSequence planFrom(SpacecraftState horizon, Mission mission) {
    DeorbitSequence sequence = new DeorbitTail().plan(horizon, mission);
    mission.setCurrentState(horizon);
    return sequence;
  }

  private static double sizedReserve() {
    return PropellantBudget.disposalReserveFor(
        MODEL, MODEL.defaultDryMass(), ORBIT_ALTITUDE, DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M);
  }

  private static SpacecraftState circularInsertion(double reserve) {
    return DisposalFixtures.orbitState(
        ORBIT_ALTITUDE, ORBIT_ALTITUDE, 0.0, MODEL.defaultDryMass() + reserve);
  }

  /**
   * A plan as a planner hands it back: a mission whose current state is the end of its restitution
   * pass, and an ephemeris ending there. The mission's own chain is a single terminal coast.
   */
  private static MissionPlan computedPlan(
      Function<Spacecraft, Mission> missionOf,
      double reserve,
      SpacecraftState insertion,
      double coastSeconds) {
    Mission mission = missionOf.apply(MODEL.toSpacecraft(MODEL.defaultDryMass(), 0, reserve));
    MissionEphemeris ephemeris =
        new MissionEphemerisGenerator()
            .generateChain(
                mission,
                List.of(new CoastingStage(StageNames.TERMINAL_COAST, coastSeconds)),
                insertion);
    GravitationalContext earth = GravitationalContext.earth();
    MissionComputeResult computation =
        new MissionComputeResult(
            new MissionOptimizerResult(Map.of()),
            ephemeris,
            new MissionPerformanceReport(List.of(), 0.0, 0.0, 0.0),
            mission,
            AchievedOrbit.of(mission.getCurrentState(), earth.equatorialRadius()),
            List.of());
    return new MissionPlan(computation);
  }

  private static int firstIndexOf(List<MissionEphemerisPoint> points, String stageName) {
    for (int index = 0; index < points.size(); index++) {
      if (stageName.equals(points.get(index).stageName())) {
        return index;
      }
    }
    throw new AssertionError("no '" + stageName + "' sample in the ephemeris");
  }
}
