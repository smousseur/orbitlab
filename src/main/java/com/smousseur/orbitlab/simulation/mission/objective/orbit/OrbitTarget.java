package com.smousseur.orbitlab.simulation.mission.objective.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveTarget;

public record OrbitTarget(SolarSystemBody body, double altitude, double eccentricity)
    implements ObjectiveTarget {
  @Override
  public double getAsScalar() {
    return PlanetRadius.radiusFor(body) + altitude;
  }
}
