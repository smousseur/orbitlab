package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

public class VerticalAscentStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(VerticalAscentStage.class);
  private final double duration;

  public VerticalAscentStage(String name, double duration) {
    super(name);
    this.duration = duration;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState currentState = mission.getCurrentState();
    Vector3D thrustDir = currentState.getPosition().normalize();

    PropulsionSystem propulsion = mission.getVehicle().getPropulsion();
    ConstantThrustManeuver burn =
        new ConstantThrustManeuver(
            currentState.getDate().shiftedBy(1.0e-3),
            this.duration,
            propulsion.thrust(),
            propulsion.isp(),
            thrustDir);

    propagator.addForceModel(burn);

    AbsoluteDate mecoDate = currentState.getDate().shiftedBy(this.duration);
    DateDetector mecoDetector =
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  logger.info("MECO reached");
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                });

    propagator.addEventDetector(mecoDetector);
  }
}
