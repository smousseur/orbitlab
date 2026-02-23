package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.core.OrbitlabException;

public interface Vehicle {
  double getDryMass();

  double getPropellantMass();

  default double getMass() {
    return getDryMass() + getPropellantMass();
  }

  default double getFullDryMass() {
    return getDryMass();
  }

  /** Propellant mass of the current (first) stage only. */
  default double getCurrentStagePropellantMass() {
    return getPropellantMass();
  }

  PropulsionSystem getPropulsion();

  default Vehicle jettison(int index) {
    throw new OrbitlabException("Vehicle jettison unsupported for " + getClass().getSimpleName());
  }
}
