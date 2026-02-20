package com.smousseur.orbitlab.simulation.mission.objective.orbit;

import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveStatus;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveTarget;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.PVCoordinates;

public class OrbitalObjective implements MissionObjective {
  private final OrbitTarget target;

  public OrbitalObjective(OrbitTarget target) {
    this.target = target;
  }

  @Override
  public void evaluate(ObjectiveStatus status, SpacecraftState state) {
    double mu = state.getOrbit().getMu();
    PVCoordinates pv = state.getPVCoordinates();
    Vector3D r = pv.getPosition();
    Vector3D v = pv.getVelocity();

    double rNorm = r.getNorm();
    double vNorm = v.getNorm();
    double targetR = PlanetRadius.radiusFor(target.body()) + target.altitude();

    // Specific energy → semi-major axis (vis-viva)
    double energy = 0.5 * vNorm * vNorm - mu / rNorm;
    double currentA = -mu / (2.0 * energy);

    // Vecteur excentricité
    Vector3D eVec =
        r.scalarMultiply(vNorm * vNorm / mu - 1.0 / rNorm)
            .subtract(v.scalarMultiply(Vector3D.dotProduct(r, v) / mu));
    double ecc = eVec.getNorm();

    // Normalized residuals (~1)
    // For non circular orbit, aError = (currentA - targetA) / targetR
    double aError = (currentA - targetR) / targetR;
    double eError = ecc - target.eccentricity();

    // Radial velocity fraction — critical for suborbital trajectories
    double vRadial = Vector3D.dotProduct(r.normalize(), v);
    double vrRatio = vRadial / vNorm; // → 0 for circular orbits

    status.setError(new double[] {aError, eError, vrRatio});
  }

  @Override
  public ObjectiveTarget getTarget() {
    return target;
  }
}
