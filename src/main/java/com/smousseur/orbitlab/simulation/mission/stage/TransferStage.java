package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferManeuver;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TransferProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

public class TransferStage extends MissionStage
    implements OptimizableMissionStage<TransferProblem> {
  private final double targetAltitude;

  private OptimizationResult optimizationResult;

  public TransferStage(String name, double targetAltitude) {
    super(name);
    this.targetAltitude = targetAltitude;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (optimizationResult == null) {
      throw new OrbitlabException(
          "TransfertStage '" + getName() + "' requires optimization before execution");
    }
    TransferManeuver maneuver = new TransferManeuver(mission.getVehicle(), targetAltitude);
    SpacecraftState state = mission.getCurrentState();

    TransferManeuver.Burn1Params params = maneuver.decode(optimizationResult.bestVariables());

    maneuver.configure(propagator, state, params);
    double maneuverTime = maneuver.totalDuration(params);
    AbsoluteDate mecoDate = state.getDate().shiftedBy(maneuverTime);
    this.configuredEndDate = mecoDate;
    propagator.addEventDetector(
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  @Override
  public TransferProblem buildProblem(Mission mission) {
    TransferManeuver maneuver = new TransferManeuver(mission.getVehicle(), targetAltitude);
    OrbitInsertionObjective insertion = (OrbitInsertionObjective) mission.getObjective();
    double dryMass =
        Spacecraft.getSpacecraft().dryMass() + LaunchVehicle.getLauncherStage2Vehicle().dryMass();
    return new TransferProblem(
        maneuver,
        mission.getCurrentState(),
        insertion.perigeeAltitude(),
        targetAltitude,
        mission.getVehicle().propulsion(),
        dryMass,
        insertion.inclination());
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
