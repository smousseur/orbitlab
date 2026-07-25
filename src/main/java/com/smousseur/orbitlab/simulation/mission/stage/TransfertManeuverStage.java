package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TransferProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * CMA-ES-optimized single-burn transfer to an elliptic {@code (perigee, apogee)} target orbit.
 *
 * <p>The single burn (4 variables: t1, dt1, α1, β1) shapes the post-gravity-turn sub-orbital state
 * directly onto the target ellipse, with flame-out-aware bounds — dt1 may explore up to the active
 * stage's actual depletion, infeasible candidates being truncated by the quiet depletion guard —
 * and is seeded by the analytic Hohmann solution. Unlike {@link TransfertTwoManeuverStage}, which
 * circularizes at apoapsis with a deterministic second burn and therefore only supports circular
 * targets, this stage grades the achieved apogee, perigee and eccentricity against the target shape
 * (see {@link TransferProblem}). A downstream {@link AnalyticTrimBurnStage} keyed on the target
 * <em>perigee</em> absorbs the residual shape/inclination error at the next apogee.
 *
 * <p>Profiles opt in to this stage in place of {@link AnalyticHohmannTransferStage} for an elliptic
 * insertion.
 */
public class TransfertManeuverStage extends MissionStage
    implements OptimizableMissionStage<TransferProblem> {

  private final double perigeeAltitude;
  private final double apogeeAltitude;
  private final double targetInclination;

  private OptimizationResult optimizationResult;

  /**
   * Creates an optimizable single-burn elliptic transfer stage.
   *
   * @param name the human-readable name of this stage
   * @param perigeeAltitude the target perigee altitude (m)
   * @param apogeeAltitude the target apogee altitude (m)
   * @param targetInclination the target orbital plane inclination (rad)
   */
  public TransfertManeuverStage(
      String name, double perigeeAltitude, double apogeeAltitude, double targetInclination) {
    super(name);
    this.perigeeAltitude = perigeeAltitude;
    this.apogeeAltitude = apogeeAltitude;
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
  public TransferProblem buildProblem(Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    ActiveStageInfo activeStage = mission.getVehicle().resolveActiveStage(state.getMass());
    return new TransferProblem(
        createManeuver(mission),
        state,
        perigeeAltitude,
        apogeeAltitude,
        activeStage.propulsion(),
        activeStage.depletionFloor(),
        targetInclination);
  }

  @Override
  public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
    // Replay uses the same late-ignition invariant the optimizer's own propagator uses.
    return createManeuver(mission).maxStepSeconds(entryState);
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (optimizationResult == null) {
      throw new OrbitlabException(
          "TransfertManeuverStage '" + getName() + "' requires optimization before execution");
    }

    SpacecraftState state = mission.getCurrentState();
    TransferManeuver maneuver = createManeuver(mission);
    TransferManeuver.Burn1Params params = maneuver.decode(optimizationResult.bestVariables());

    maneuver.configure(propagator, state, params);
    // Replay path: the optimized variables are supposed to fit the loaded propellant — fail loud.
    DepletionGuard.arm(
        propagator,
        mission.getVehicle().resolveActiveStage(state.getMass()).depletionFloor(),
        getName());

    AbsoluteDate endDate = state.getDate().shiftedBy(maneuver.totalDuration(params));
    this.configuredEndDate = endDate;
    propagator.addEventDetector(
        new DateDetector(endDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  private TransferManeuver createManeuver(Mission mission) {
    // The apogee is the reference altitude the maneuver's in-flight tracker uses to bound the
    // max-altitude excursion; for an elliptic target that is the apside actually reached.
    return new TransferManeuver(mission.getVehicle(), apogeeAltitude);
  }
}
