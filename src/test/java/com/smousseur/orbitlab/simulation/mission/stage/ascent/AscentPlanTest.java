package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * Pins the ascent date arithmetic (spec {@code docs/mission-stages/01-separations-implicites.md}
 * §4.3, étape 1).
 *
 * <p>{@link AscentPlan} exists so the jettison and second-ignition dates are computed <b>once</b>
 * and consumed identically by however many phases the ascent is split into. The dates it returns
 * must therefore reproduce, bit for bit, the chain of {@code shiftedBy} calls the single-propagator
 * configuration used before the split — hence the zero-tolerance comparisons below against the
 * literal pre-split expressions. {@code AbsoluteDate} keeps its offset in two parts, so {@code
 * a.shiftedBy(x).shiftedBy(y)} is not {@code a.shiftedBy(x + y)}: collapsing a chain here would
 * silently move a burn boundary.
 */
class AscentPlanTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  // Falcon Heavy-like two-stage stack plus payload, as Launchers.FALCON_HEAVY resolves it.
  private static final LaunchVehicle S1 =
      new LaunchVehicle(66_000, 1_233_000, 1_233_000, new PropulsionSystem(296, 22_800_000));
  private static final LaunchVehicle S2 =
      new LaunchVehicle(4_000, 107_500, 107_500, new PropulsionSystem(348, 981_000));
  private static final Spacecraft PAYLOAD =
      new Spacecraft(2_000, 2_000, 1_500, new PropulsionSystem(320, 400));

  private static final double BURN1_DURATION = 149.979660;
  private static final double INTERSTAGE_COAST = 2.0;
  private static final double TRANSITION_TIME = 153.979660;
  private static final double SETTLING_EPSILON = 1.0e-3;

  private static AscentPlan plan() {
    VehicleStack stack = new VehicleStack(List.of(S1, S2, PAYLOAD));
    ActiveStageInfo first = stack.resolveActiveStage(stack.getMass());
    ActiveStageInfo second = stack.resolveActiveStage(first.massAfterJettison());
    return new AscentPlan(
        AbsoluteDate.J2000_EPOCH,
        TRANSITION_TIME,
        0.32,
        BURN1_DURATION,
        INTERSTAGE_COAST,
        TRANSITION_TIME - BURN1_DURATION - INTERSTAGE_COAST,
        30.0,
        first,
        second,
        null);
  }

  /** The literal expressions {@code GravityTurnManeuver.configure} used before the split. */
  @Test
  void dates_reproduceThePreSplitArithmeticExactly() {
    AscentPlan plan = plan();
    AbsoluteDate kick = plan.kickDate();

    AbsoluteDate expectedIgnition1 = kick.shiftedBy(SETTLING_EPSILON);
    AbsoluteDate expectedJettison =
        kick.shiftedBy(SETTLING_EPSILON).shiftedBy(BURN1_DURATION).shiftedBy(SETTLING_EPSILON);
    AbsoluteDate expectedIgnition2 =
        expectedJettison.shiftedBy(INTERSTAGE_COAST).shiftedBy(SETTLING_EPSILON);
    AbsoluteDate expectedMeco = kick.shiftedBy(TRANSITION_TIME);

    assertEquals(
        0.0,
        plan.firstIgnitionDate().durationFrom(expectedIgnition1),
        0.0,
        "burn 1 ignition must sit exactly one settling epsilon after the kick");
    assertEquals(
        0.0,
        plan.jettisonDate().durationFrom(expectedJettison),
        0.0,
        "the jettison date must be the pre-split shiftedBy chain, not a collapsed sum");
    assertEquals(
        0.0,
        plan.secondIgnitionDate().durationFrom(expectedIgnition2),
        0.0,
        "burn 2 ignition must be the pre-split shiftedBy chain, not a collapsed sum");
    assertEquals(0.0, plan.mecoDate().durationFrom(expectedMeco), 0.0, "MECO date");
  }

  /** The same dates, read as the durations a reader of the spec's §4.3 table would expect. */
  @Test
  void dates_layOutTheAscentInOrder() {
    AscentPlan plan = plan();
    AbsoluteDate kick = plan.kickDate();

    assertEquals(SETTLING_EPSILON, plan.firstIgnitionDate().durationFrom(kick), 1.0e-12);
    assertEquals(
        BURN1_DURATION + 2 * SETTLING_EPSILON, plan.jettisonDate().durationFrom(kick), 1.0e-9);
    assertEquals(
        INTERSTAGE_COAST + SETTLING_EPSILON,
        plan.secondIgnitionDate().durationFrom(plan.jettisonDate()),
        1.0e-12,
        "the interstage coast separates the jettison from the second ignition");
    assertEquals(TRANSITION_TIME, plan.mecoDate().durationFrom(kick), 1.0e-12);
    assertEquals(BURN1_DURATION + INTERSTAGE_COAST, plan.stagingCompleteTime(), 1.0e-12);
  }

  /** The plan carries the two vehicle stages the split phases will each need on their own. */
  @Test
  void stages_carryTheJettisonMassAndTheGuardingFloor() {
    AscentPlan plan = plan();

    assertEquals(
        S2.getMass() + PAYLOAD.getMass(),
        plan.massAfterJettison(),
        1.0e-9,
        "jettisoning S1 leaves exactly the reference mass of the stack above");
    assertEquals(
        S2.dryMass() + PAYLOAD.getMass(),
        plan.depletionFloor(),
        1.0e-9,
        "the guarding floor is the post-jettison stack floor, not S1's own");
  }
}
