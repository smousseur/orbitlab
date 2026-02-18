package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.events.handlers.StopOnEvent;

public class RadialVelocityDetector extends AbstractDetector<RadialVelocityDetector> {

  private final double vRadialThreshold;

  public RadialVelocityDetector(
      EventDetectionSettings detectionSettings, EventHandler handler, double vRadialThreshold) {
    super(detectionSettings, handler);
    this.vRadialThreshold = vRadialThreshold;
  }

  /** Constructeur simplifié */
  public RadialVelocityDetector(double vRadialThreshold) {
    super(60.0, 1.0e-6, 100, new StopOnEvent());
    this.vRadialThreshold = vRadialThreshold;
  }

  /**
   * g(s) = vRadial(s) - vRadialThreshold L'événement est détecté quand g passe par zéro, i.e. quand
   * la vitesse radiale croise le seuil.
   */
  @Override
  public double g(SpacecraftState state) {
    Vector3D position = state.getPVCoordinates().getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();

    // Vitesse radiale = projection de V sur la direction radiale (position unitaire)
    double vRadial = Vector3D.dotProduct(velocity, position.normalize());

    return vRadial - vRadialThreshold;
  }

  @Override
  protected RadialVelocityDetector create(
      EventDetectionSettings detectionSettings, EventHandler newHandler) {
    return new RadialVelocityDetector(detectionSettings, newHandler, vRadialThreshold);
  }
}
