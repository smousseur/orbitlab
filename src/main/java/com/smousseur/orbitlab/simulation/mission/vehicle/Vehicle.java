package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.core.OrbitlabException;

public interface Vehicle {
  double getMass();

  PropulsionSystem getPropulsion();

  default Vehicle jettison(int index) {
    throw new OrbitlabException("Vehicle jettison unsupported for " + getClass().getSimpleName());
  }
}
