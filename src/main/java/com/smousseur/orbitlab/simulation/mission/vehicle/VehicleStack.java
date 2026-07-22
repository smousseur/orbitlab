package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * A composite vehicle made up of multiple stacked stages. The first vehicle in the list represents
 * the currently active (bottom) vehicle. Mass and propulsion queries delegate to the constituent
 * vehicles.
 *
 * @param vehicles the ordered list of vehicle stages (index 0 = bottom vehicle)
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
  public double propellantLoad() {
    return vehicles.stream().mapToDouble(Vehicle::propellantLoad).sum();
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
   * Resolves the active vehicle based on the current spacecraft mass. The active vehicle is the
   * lowest (bottom-most) vehicle whose cumulative mass-above is strictly less than the current
   * mass.
   *
   * @param currentMass the current spacecraft mass from SpacecraftState
   * @return the active vehicle information
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
    // Fallback: topmost vehicle
    int last = n - 1;
    return new ActiveStageInfo(last, vehicles.get(last), 0, 0);
  }

  /**
   * Splits the propellant still aboard across the physical stages, instead of reporting a single
   * stack-wide total (bilan 10 §6). Given the current mass, the active stage holds {@link
   * ActiveStageInfo#remainingFuel(double)}, every stage below it has reached its depletion floor
   * (residual 0 — the mass model switches stages exactly there), and every stage above it is still
   * untouched at its full load.
   *
   * <p>A stage jettisoned early with propellant aboard is the one case this cannot see after the
   * fact: once the mass has dropped, the discarded propellant is indistinguishable from burnt
   * propellant. {@code MissionOptimizer} captures that residual as the separation happens.
   */
  @Override
  public List<StagePropellant> resolveStagePropellant(double currentMass) {
    ActiveStageInfo active = resolveActiveStage(currentMass);
    int activeIndex = active.stageIndex();
    List<StagePropellant> perStage = new ArrayList<>(vehicles.size());
    for (int i = 0; i < vehicles.size(); i++) {
      double loaded = vehicles.get(i).propellantLoad();
      double residual;
      if (i < activeIndex) {
        residual = 0.0;
      } else if (i == activeIndex) {
        residual = Math.max(0.0, Math.min(loaded, active.remainingFuel(currentMass)));
      } else {
        residual = loaded;
      }
      perStage.add(new StagePropellant(i, loaded, residual));
    }
    return List.copyOf(perStage);
  }
}
