package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.core.OrbitlabException;

public interface Vehicle {
  double dryMass();

  double propellantCapacity();

  default Vehicle getFirstStage() {
    return getStage(0);
  }

  default Vehicle getSecondStage() {
    return getStage(1);
  }

  default Vehicle getStage(int index) {
    return this;
  }

  default double getMass() {
    return dryMass() + propellantCapacity();
  }

  default double getFullDryMass() {
    return dryMass();
  }

  /** Propellant mass of the current (first) stage only. */
  default double getCurrentStagePropellantMass() {
    return propellantCapacity();
  }

  PropulsionSystem propulsion();

  default void jettison() {
    jettison(0);
  }

  default void jettison(int index) {
    throw new OrbitlabException("Vehicle jettison unsupported for " + getClass().getSimpleName());
  }

  default double getFirstStageDryMass() {
    return dryMass();
  }
}
