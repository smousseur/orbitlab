package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;
import org.orekit.utils.Constants;

/**
 * PHY-2 / L4: the analytic half of the measured sizing (spec {@code
 * docs/atmosphere/11-conception-L4-PHY-2.md} §3.2). {@code loadsForMeasuredTopStage} converts a ΔV
 * that was measured in flight back into kilograms; the flight itself is {@code
 * MeasuredLoadPlannerFlightTest}'s business.
 *
 * <p>The assertions are on the <em>ΔV the sized load can deliver</em> rather than on the load in
 * kilograms, which is what makes them checks and not a second copy of the formula.
 */
class PropellantBudgetMeasuredTopStageTest {

  /** Kourou, the latitude the Falcon Heavy LEO-400 profile of {@code DT-19} was measured at. */
  private static final double KOUROU_LATITUDE_DEG = 5.23;

  /**
   * What {@code DT-19} measured a Falcon Heavy upper stage actually spending on the LEO-400
   * profile: 430 m/s of transfer and 6 of trim, against the 1 300 the universal reserve provisions.
   */
  private static final double MEASURED_INSERTION_DV = 436.0;

  private static final double PAYLOAD_MASS = 10_000.0;

  @Test
  void lowerStagesFlyFull_andOnlyTheTopLoadMoves() {
    double[] measured = measuredLoads(MEASURED_INSERTION_DV);
    double[] capacities =
        Launchers.FALCON_HEAVY.stages().stream()
            .mapToDouble(StageModel::propellantCapacity)
            .toArray();

    assertEquals(capacities.length, measured.length);
    for (int stage = 0; stage < measured.length - 1; stage++) {
      assertEquals(
          capacities[stage], measured[stage], 1e-6, "lower stage " + stage + " must fly full");
    }
    assertTrue(measured[top()] > 0, "the top stage needs propellant for the measured burn");
    assertTrue(measured[top()] < capacities[top()], "and well under its capacity at 436 m/s");
  }

  @Test
  void theSizedLoadCoversTheMeasuredDeltaV_plusTheMarginAndNoMore() {
    double deliverable = deliverableDeltaV(measuredLoads(MEASURED_INSERTION_DV)[top()]);

    assertTrue(
        deliverable >= MEASURED_INSERTION_DV,
        () -> "sized load must deliver at least the measured ΔV, got " + deliverable);
    // The margin is applied to the load, and on a logarithmic law that buys rather less than 10 %
    // of ΔV, so this ceiling is loose on purpose: it only catches a margin applied twice.
    assertTrue(
        deliverable <= MEASURED_INSERTION_DV * 1.15,
        () -> "sized load must not over-provision beyond the margin, got " + deliverable);
  }

  @Test
  void measuringTheRealInsertionRemovesTheReserveOverProvisioning() {
    Spacecraft payload = payload();
    double[] budgeted =
        PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, payload, 400_000, KOUROU_LATITUDE_DEG);
    double[] measured = measuredLoads(MEASURED_INSERTION_DV);

    double budgetedDv = deliverableDeltaV(budgeted[top()]);
    double measuredDv = deliverableDeltaV(measured[top()]);

    assertTrue(
        measured[top()] < budgeted[top()],
        () ->
            "measured sizing must be lighter than the reserve-laden budget, got "
                + measured[top()]
                + " vs "
                + budgeted[top()]);
    assertTrue(
        budgetedDv > 1.5 * measuredDv,
        () ->
            "the reserve provisions the stage for far more than it spends: budgeted "
                + budgetedDv
                + " m/s vs measured "
                + measuredDv);
  }

  @Test
  void theLoadGrowsWithTheMeasuredDeltaV() {
    assertTrue(measuredLoads(800.0)[top()] > measuredLoads(400.0)[top()]);
  }

  @Test
  void anUnreachableDeltaVClampsToCapacity() {
    double[] loads = measuredLoads(50_000.0);
    assertEquals(
        Launchers.FALCON_HEAVY.stages().get(top()).propellantCapacity(),
        loads[top()],
        1e-6,
        "a ΔV the stage cannot hold clamps rather than overflowing the tank");
  }

  @Test
  void aTopStageThatNeverBurntCarriesNoSizingInformation() {
    double[] loads = measuredLoads(0.0);
    assertEquals(
        Launchers.FALCON_HEAVY.stages().get(top()).propellantCapacity(),
        loads[top()],
        1e-6,
        "a non-positive measurement yields capacity, as a stage with no sizing freedom does");
  }

  private static double[] measuredLoads(double deltaV) {
    return PropellantBudget.loadsForMeasuredTopStage(Launchers.FALCON_HEAVY, PAYLOAD_MASS, deltaV);
  }

  /** Tsiolkovsky on the top stage: the ΔV a given load can deliver above the payload (m/s). */
  private static double deliverableDeltaV(double topLoad) {
    StageModel topStage = Launchers.FALCON_HEAVY.stages().getLast();
    double exhaustVelocity = topStage.propulsion().isp() * Constants.G0_STANDARD_GRAVITY;
    double finalMass = topStage.dryMass() + PAYLOAD_MASS;
    return exhaustVelocity * FastMath.log((finalMass + topLoad) / finalMass);
  }

  private static int top() {
    return Launchers.FALCON_HEAVY.stages().size() - 1;
  }

  private static Spacecraft payload() {
    return Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(PAYLOAD_MASS, 0.0);
  }
}
