package com.smousseur.orbitlab.simulation.mission.disposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.DeorbitBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageNames;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * The deorbit sequence as planned from a horizon state, without the optimizer: where each burn
 * sits, how long it lasts, and why the sequence stops.
 */
class DeorbitTailTest {

  private static final double TARGET = DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M;

  /** The shortest coast the tail schedules: the burn then lights at the earliest it may. */
  private static final double CLAMPED_COAST_SECONDS = 1.0e-3;

  /** Half-width of the window a burn's centre must bracket an apogee within (s). */
  private static final double CENTRING_WINDOW_SECONDS = 10.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void aCircularOrbitReachesTheTargetInArcCappedBurnsCentredOnTheApogee() {
    PayloadModel model = Payloads.EARTH_OBSERVATION_SAT;
    double dry = model.defaultDryMass();
    double reserve = PropellantBudget.disposalReserveFor(model, dry, 400_000.0, TARGET);
    Mission mission = DisposalFixtures.payloadMission(model.toSpacecraft(dry, 0, reserve));
    SpacecraftState horizon = DisposalFixtures.orbitState(400_000.0, 400_000.0, 0.0, dry + reserve);

    DeorbitSequence sequence = new DeorbitTail().plan(horizon, mission);

    assertEquals(DeorbitSequence.End.TARGET_REACHED, sequence.end());
    assertReachedTheTarget(sequence);
    assertTrue(sequence.burns().size() < DeorbitTail.MAX_BURNS);
    assertBurnsCappedAndCentredOnTheApogee(sequence, horizon, mission);
  }

  @Test
  void anEllipticOrbitBurnsAtItsApogeeAndReachesTheTarget() {
    PayloadModel model = Payloads.GEO_SAT;
    double dry = model.defaultDryMass();
    // Sized as if circular at the perigee — the convention the tail is designed around.
    double reserve = PropellantBudget.disposalReserveFor(model, dry, 200_000.0, TARGET);
    Mission mission = DisposalFixtures.payloadMission(model.toSpacecraft(dry, 0, reserve));
    SpacecraftState horizon =
        DisposalFixtures.orbitState(200_000.0, 1_000_000.0, 0.0, dry + reserve);

    DeorbitSequence sequence = new DeorbitTail().plan(horizon, mission);

    assertEquals(DeorbitSequence.End.TARGET_REACHED, sequence.end());
    assertReachedTheTarget(sequence);
    assertBurnsCappedAndCentredOnTheApogee(sequence, horizon, mission);
    List<StageChainRunner.StageRun> runs = fly(sequence, horizon, mission);
    assertTrue(
        coastSeconds(runs.getFirst()) > CLAMPED_COAST_SECONDS,
        "from the perigee, the first burn waits for the apogee rather than lighting at once");
  }

  @Test
  void aStateFallingBeforeTheNextApogeeEndsTheSequenceCleanly() {
    PayloadModel model = Payloads.EARTH_OBSERVATION_SAT;
    double dry = model.defaultDryMass();
    Mission mission = DisposalFixtures.payloadMission(model.toSpacecraft(dry, 0, 300.0));
    // Heading for a 20 km perigee: the atmosphere takes it before it can climb back to an apogee.
    SpacecraftState horizon = DisposalFixtures.orbitState(20_000.0, 400_000.0, 300.0, dry + 300.0);

    DeorbitSequence sequence = new DeorbitTail().plan(horizon, mission);

    assertEquals(DeorbitSequence.End.FELL_BEFORE_NEXT_BURN, sequence.end());
    assertTrue(sequence.stages().isEmpty());
    assertTrue(sequence.burns().isEmpty());
    assertSame(horizon, sequence.finalState());
  }

  /**
   * The plan is only worth something if flying it lands where it says. The materialized stages,
   * sampled the way a mission's own stages are, must end on the planned state to the bit — not to a
   * tolerance — and the sampling must be the per-stage one: a burn second by second, a coast minute
   * by minute.
   */
  @Test
  void flyingThePlannedStagesEndsOnThePlannedStateToTheBit() {
    PayloadModel model = Payloads.EARTH_OBSERVATION_SAT;
    double dry = model.defaultDryMass();
    double reserve = PropellantBudget.disposalReserveFor(model, dry, 400_000.0, TARGET);
    Mission mission = DisposalFixtures.payloadMission(model.toSpacecraft(dry, 0, reserve));
    SpacecraftState horizon = DisposalFixtures.orbitState(400_000.0, 400_000.0, 0.0, dry + reserve);
    DeorbitSequence sequence = new DeorbitTail().plan(horizon, mission);

    MissionEphemeris tail =
        new MissionEphemerisGenerator().generateChain(mission, sequence.stages(), horizon);

    SpacecraftState planned = sequence.finalState();
    MissionEphemerisPoint last = tail.lastPoint();
    assertEquals(planned.getDate(), last.time());
    assertEquals(planned.getPosition(), last.position());
    assertEquals(planned.getPVCoordinates().getVelocity(), last.velocity());
    assertEquals(planned.getMass(), last.mass(), 0.0);
    assertEquals(horizon.getDate(), tail.firstPoint().time());
    assertTrue(tail.isComplete());

    double burnSeconds =
        sequence.stages().stream()
            .filter(DeorbitBurnStage.class::isInstance)
            .mapToDouble(stage -> ((DeorbitBurnStage) stage).durationSeconds())
            .sum();
    double coastSeconds = last.time().durationFrom(horizon.getDate()) - burnSeconds;
    // One sample per step, plus at each stage the opening sample and the closing point.
    double bound = burnSeconds / 1.0 + coastSeconds / 60.0 + 3.0 * sequence.stages().size();
    assertTrue(tail.size() <= bound, tail.size() + " points for a bound of " + bound);
  }

  private static void assertReachedTheTarget(DeorbitSequence sequence) {
    double perigee = DisposalFixtures.perigeeAltitude(sequence.finalState());
    assertTrue(perigee <= TARGET, "final perigee " + perigee + " m above the target");
  }

  /**
   * Every burn lasts at most a quarter of the period it was planned from, and — unless it had to
   * light at the earliest — its planned centre is an apogee of the trajectory it interrupts. That
   * apogee is checked on the physics, not on the planner's word: the same trajectory flown without
   * the burn has its radial velocity changing sign from positive to negative across the centre.
   */
  private static void assertBurnsCappedAndCentredOnTheApogee(
      DeorbitSequence sequence, SpacecraftState horizon, Mission mission) {
    List<StageChainRunner.StageRun> runs = fly(sequence, horizon, mission);
    assertEquals(2 * sequence.burns().size(), runs.size());
    for (int index = 0; index < sequence.burns().size(); index++) {
      DeorbitSequence.Burn burn = sequence.burns().get(index);
      StageChainRunner.StageRun coast = runs.get(2 * index);
      StageChainRunner.StageRun burnRun = runs.get(2 * index + 1);

      assertInstanceOf(CoastingStage.class, coast.stage());
      assertEquals(StageNames.DEORBIT_COAST, coast.stage().getName());
      DeorbitBurnStage burnStage = assertInstanceOf(DeorbitBurnStage.class, burnRun.stage());
      assertEquals(StageNames.DEORBIT_BURN, burnStage.getName());

      double period = coast.entryState().getOrbit().getKeplerianPeriod();
      assertTrue(
          burn.plannedSeconds() <= DeorbitTail.ARC_CAP_FRACTION * period,
          "burn "
              + index
              + " planned "
              + burn.plannedSeconds()
              + " s for a "
              + period
              + " s orbit");
      assertTrue(
          burnStage.durationSeconds() <= burn.plannedSeconds(),
          "burn " + index + " flown longer than planned");

      if (coastSeconds(coast) > CLAMPED_COAST_SECONDS) {
        AbsoluteDate ignition = burnRun.entryState().getDate();
        assertEquals(
            0.0,
            ignition.durationFrom(burn.apogee().shiftedBy(-burn.plannedSeconds() / 2.0)),
            1.0e-6,
            "burn " + index + " not centred on its apogee");
        assertTrue(
            radialVelocity(
                    ballistic(
                        coast.entryState(),
                        burn.apogee().shiftedBy(-CENTRING_WINDOW_SECONDS),
                        mission))
                > 0.0,
            "burn " + index + ": still climbing before the centre");
        assertTrue(
            radialVelocity(
                    ballistic(
                        coast.entryState(),
                        burn.apogee().shiftedBy(CENTRING_WINDOW_SECONDS),
                        mission))
                < 0.0,
            "burn " + index + ": already falling after the centre");
      } else {
        assertEquals(CLAMPED_COAST_SECONDS, coastSeconds(coast), 1.0e-9);
      }
    }
  }

  private static List<StageChainRunner.StageRun> fly(
      DeorbitSequence sequence, SpacecraftState horizon, Mission mission) {
    List<StageChainRunner.StageRun> runs = new ArrayList<>();
    StageChainRunner.sampling(null, 0.0, runs::add).run(sequence.stages(), horizon, mission);
    return runs;
  }

  private static double coastSeconds(StageChainRunner.StageRun coast) {
    return coast.finalState().getDate().durationFrom(coast.entryState().getDate());
  }

  private static SpacecraftState ballistic(SpacecraftState from, AbsoluteDate to, Mission mission) {
    return StageChainRunner.plain()
        .run(
            List.of(new CoastingStage("Ballistic reference", to.durationFrom(from.getDate()))),
            from,
            mission);
  }

  private static double radialVelocity(SpacecraftState state) {
    Vector3D position = state.getPosition();
    return Vector3D.dotProduct(position, state.getPVCoordinates().getVelocity())
        / position.getNorm();
  }
}
