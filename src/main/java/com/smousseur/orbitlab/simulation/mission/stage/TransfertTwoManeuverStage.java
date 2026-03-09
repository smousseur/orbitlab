package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver.Burn1Params;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver.ResolvedBurns;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TransferTwoManeuverProblem;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

public class TransfertTwoManeuverStage extends MissionStage
    implements OptimizableMissionStage<TransferTwoManeuverProblem> {
  private final double targetAltitude;

  private OptimizationResult optimizationResult;

  public TransfertTwoManeuverStage(String name, double targetAltitude) {
    super(name);
    this.targetAltitude = targetAltitude;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (optimizationResult == null) {
      throw new OrbitlabException(
          "TransfertStage '" + getName() + "' requires optimization before execution");
    }
    TransfertTwoManeuver maneuver = new TransfertTwoManeuver(mission.getVehicle(), targetAltitude);
    SpacecraftState state = mission.getCurrentState();

    Burn1Params params = maneuver.decode(optimizationResult.bestVariables());

    ResolvedBurns burns = maneuver.resolveBurnsFromInitial(state, params);
    if (burns == null) {
      throw new OrbitlabException(
          "TransfertStage '" + getName() + "': failed to resolve burns at runtime");
    }

    maneuver.configure(propagator, state, params, burns);

    double maneuverTime = maneuver.totalDuration(params, burns);
    AbsoluteDate mecoDate = state.getDate().shiftedBy(maneuverTime);
    propagator.addEventDetector(
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  @Override
  public TransferTwoManeuverProblem buildProblem(Mission mission) {
    TransfertTwoManeuver maneuver = new TransfertTwoManeuver(mission.getVehicle(), targetAltitude);
    return new TransferTwoManeuverProblem(
        maneuver,
        mission.getCurrentState(),
        targetAltitude,
        mission.getVehicle().getSecondStage().propulsion());
  }

  @Override
  public SpacecraftState getEntryState() {
    return optimizationResult != null ? optimizationResult.stageEntryState() : null;
  }

  @Override
  public String optimizationKey() {
    return getName();
  }

  @Override
  public void applyOptimization(OptimizationResult result) {
    this.optimizationResult = result;
  }
}
