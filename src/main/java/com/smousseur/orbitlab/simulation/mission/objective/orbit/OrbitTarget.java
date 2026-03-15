package com.smousseur.orbitlab.simulation.mission.objective.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveTarget;

/**
 * Target parameters for an orbital insertion objective, specifying the desired orbit around a
 * celestial body.
 *
 * @param body the central body around which the target orbit is defined
 * @param altitude the desired orbital altitude above the body's surface in meters
 * @param eccentricity the desired orbital eccentricity (0.0 for circular)
 */
public record OrbitTarget(SolarSystemBody body, double altitude, double eccentricity)
    implements ObjectiveTarget {
  @Override
  public double getAsScalar() {
    return PlanetRadius.radiusFor(body) + altitude;
  }
}
