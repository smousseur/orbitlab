package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferManeuver;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferResult;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TransferTwoManeuverProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * CMA-ES-optimized two-burn transfer to a circular target orbit (spec 06 I6). Burn 1 (4
 * variables: t1, dt1, α1, β1) is optimized with flame-out-aware bounds — dt1 may explore up to
 * the actual depletion of the active stage, infeasible candidates being truncated by the quiet
 * depletion guard — and the circularization burn at the next apoapsis is resolved
 * deterministically from the post-burn-1 state, seeded by the analytic Hohmann solution.
 * Profiles opt in to this stage in place of {@link AnalyticHohmannTransferStage}.
 */
public class TransfertTwoManeuverStage extends MissionStage
    implements OptimizableMissionStage<TransferTwoManeuverProblem> {

  private final double targetAltitude;
  private final double targetInclination;

  private OptimizationResult optimizationResult;

  /**
   * Creates an optimizable two-burn transfer stage.
   *
   * @param name the human-readable name of this stage
   * @param targetAltitude the circular target orbit altitude (m)
   * @param targetInclination the target orbital plane inclination (rad)
   */
  public TransfertTwoManeuverStage(String name, double targetAltitude, double targetInclination) {
    super(name);
    this.targetAltitude = targetAltitude;
    this.targetInclination = targetInclination;
  }

  @Override
  public String optimizationKey() {
    return getName();
  }

  @Override
  public void applyOptimization(OptimizationResult result) {
    this.optimizationResult = result;
  }

  @Override
  public TransferTwoManeuverProblem buildProblem(Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    ActiveStageInfo activeStage = mission.getVehicle().resolveActiveStage(state.getMass());
    return new TransferTwoManeuverProblem(
        createManeuver(mission),
        state,
        targetAltitude,
        activeStage.propulsion(),
        activeStage.depletionFloor(),
        targetInclination);
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (optimizationResult == null) {
      throw new OrbitlabException(
          "TransfertTwoManeuverStage '" + getName() + "' requires optimization before execution");
    }

    SpacecraftState state = mission.getCurrentState();
    TransfertTwoManeuver maneuver = createManeuver(mission);
    double[] variables = optimizationResult.bestVariables();
    TransferManeuver.Burn1Params params = maneuver.decode(variables);

    // Re-resolve the deterministic circularization burn against the current entry state — the
    // same resolution the optimizer evaluated (single source of truth).
    TransferResult transferResult = maneuver.propagateForOptimization(state, variables);
    TransfertTwoManeuver.ResolvedCircularizationBurn circularizationBurn =
        transferResult.circularizationBurn();
    if (circularizationBurn == null) {
      throw new OrbitlabException(
          "TransfertTwoManeuverStage '"
              + getName()
              + "': circularization burn could not be resolved from the optimized burn 1");
    }

    maneuver.configure(propagator, state, params, circularizationBurn);
    // Replay path: the optimized variables are supposed to fit the loaded propellant — fail loud.
    DepletionGuard.arm(
        propagator,
        mission.getVehicle().resolveActiveStage(state.getMass()).depletionFloor(),
        getName());

    AbsoluteDate endDate =
        state.getDate().shiftedBy(maneuver.totalDuration(params, circularizationBurn));
    this.configuredEndDate = endDate;
    propagator.addEventDetector(
        new DateDetector(endDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  private TransfertTwoManeuver createManeuver(Mission mission) {
    return new TransfertTwoManeuver(mission.getVehicle(), targetAltitude);
  }
}
