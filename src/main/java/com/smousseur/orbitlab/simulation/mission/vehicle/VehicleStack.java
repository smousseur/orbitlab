package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.List;

/**
 * A composite vehicle made up of multiple stacked stages. The first vehicle in the list represents
 * the currently active (bottom) stage. Mass and propulsion queries delegate to the constituent
 * vehicles.
 *
 * @param vehicles the ordered list of vehicle stages (index 0 = bottom stage)
 */
public record VehicleStack(List<Vehicle> vehicles) implements Vehicle {

  @Override
  public double dryMass() {
    return vehicles.stream().mapToDouble(Vehicle::dryMass).sum();
  }

  @Override
  public double propellantCapacity() {
    return vehicles.stream().mapToDouble(Vehicle::propellantCapacity).sum();
  }

  @Override
  public double getMass() {
    return vehicles.stream().mapToDouble(Vehicle::getMass).sum();
  }

  @Override
  public PropulsionSystem propulsion() {
    return vehicles.getFirst().propulsion();
  }

  /**
   * Resolves the active stage based on the current spacecraft mass. The active stage is the lowest
   * (bottom-most) stage whose cumulative mass-above is strictly less than the current mass.
   *
   * @param currentMass the current spacecraft mass from SpacecraftState
   * @return the active stage information
   */
  @Override
  public ActiveStageInfo resolveActiveStage(double currentMass) {
    int n = vehicles.size();
    double[] massAbove = new double[n];
    double[] dryMassAbove = new double[n];
    double cumulativeMass = 0;
    double cumulativeDryMass = 0;

    for (int i = n - 1; i >= 0; i--) {
      massAbove[i] = cumulativeMass;
      dryMassAbove[i] = cumulativeDryMass;
      cumulativeMass += vehicles.get(i).getMass();
      cumulativeDryMass += vehicles.get(i).dryMass();
    }

    for (int i = 0; i < n; i++) {
      if (currentMass > massAbove[i]) {
        return new ActiveStageInfo(i, vehicles.get(i), massAbove[i], dryMassAbove[i]);
      }
    }
    // Fallback: topmost stage
    int last = n - 1;
    return new ActiveStageInfo(last, vehicles.get(last), 0, 0);
  }
}
