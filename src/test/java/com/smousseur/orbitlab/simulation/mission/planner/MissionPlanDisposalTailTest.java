package com.smousseur.orbitlab.simulation.mission.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
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
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.PVCoordinates;

/**
 * The hook {@link MissionPlanOptimizer#compute()} ends on: a plan whose mission carries a disposal
 * tail comes back with the tail flown past the horizon; any other plan comes back untouched.
 *
 * <p>Built on a hand-made plan rather than a planner run: what is under test is the hook, and the
 * planners in front of it are the slow part.
 */
class MissionPlanDisposalTailTest {

  private static final PayloadModel MODEL = Payloads.EARTH_OBSERVATION_SAT;

  private static final double ORBIT_ALTITUDE = 400_000.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void aPlanWithoutATailIsReturnedUntouched() {
    MissionPlan plan = computedPlan(DisposalFixtures::payloadMission);

    assertSame(plan, MissionPlanOptimizer.withDisposalTail(plan));
  }

  @Test
  void aTailIsFlownPastTheHorizonAndAppendedToTheEphemeris() {
    MissionPlan plan = computedPlan(DisposalFixtures::payloadMissionWithTail);
    MissionComputeResult before = plan.computation();
    Mission mission = before.mission();
    SpacecraftState horizon = mission.getCurrentState();

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
    MissionEphemerisPoint last = points.getLast();
    assertEquals(StageNames.DEORBIT_BURN, last.stageName());
    assertTrue(after.ephemeris().isComplete());
    double perigee = DisposalFixtures.perigeeAltitude(stateOf(last));
    assertTrue(
        perigee <= DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M,
        "the trajectory ends on the target perigee, not " + perigee + " m");
  }

  /**
   * A plan as a planner hands it back: a mission whose current state is the end of its restitution
   * pass, and an ephemeris ending there. The mission's own chain is a single terminal coast.
   */
  private static MissionPlan computedPlan(Function<Spacecraft, Mission> missionOf) {
    double dry = MODEL.defaultDryMass();
    double reserve =
        PropellantBudget.disposalReserveFor(
            MODEL, dry, ORBIT_ALTITUDE, DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M);
    Mission mission = missionOf.apply(MODEL.toSpacecraft(dry, 0, reserve));
    SpacecraftState insertion =
        DisposalFixtures.orbitState(ORBIT_ALTITUDE, ORBIT_ALTITUDE, 0.0, dry + reserve);
    MissionEphemeris ephemeris =
        new MissionEphemerisGenerator()
            .generateChain(
                mission, List.of(new CoastingStage(StageNames.TERMINAL_COAST, 600.0)), insertion);
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

  private static SpacecraftState stateOf(MissionEphemerisPoint point) {
    GravitationalContext earth = GravitationalContext.earth();
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(point.position(), point.velocity()),
                earth.inertialFrame(),
                point.time(),
                earth.mu()))
        .withMass(point.mass());
  }
}
