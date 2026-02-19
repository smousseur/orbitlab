package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;

public class GravityTurnProblem implements TrajectoryProblem {
  private final KeplerianOrbit initialOrbit;
  private final double initialMass;
  private final double thrust;
  private final double isp;

  public GravityTurnProblem(
      KeplerianOrbit initialOrbit, double initialMass, PropulsionSystem propulsionSystem) {
    this.initialOrbit = initialOrbit;
    this.initialMass = initialMass;
    this.thrust = propulsionSystem.thrust();
    this.isp = propulsionSystem.isp();
  }

  @Override
  public int getNumVariables() {
    return 1;
  }

  @Override
  public double[] buildInitialGuess() {
    return new double[0];
  }

  @Override
  public double[] getLowerBounds() {
    return new double[0];
  }

  @Override
  public double[] getUpperBounds() {
    return new double[0];
  }

  @Override
  public double[] getInitialSigma() {
    return new double[0];
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    return null;
  }

  @Override
  public double computeCost(SpacecraftState finalState) {
    return 0;
  }
}
