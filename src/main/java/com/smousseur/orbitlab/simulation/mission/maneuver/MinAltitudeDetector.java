package com.smousseur.orbitlab.simulation.mission.maneuver;

import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.events.handlers.StopOnEvent;
import org.orekit.utils.Constants;

public class MinAltitudeDetector extends AbstractDetector<MinAltitudeDetector> {

  private final double minAltitude;
  private final double earthRadius;

  /**
   * @param minAltitude minimum altitude in meters (e.g. 100_000 for 100 km)
   */
  public MinAltitudeDetector(double minAltitude) {
    super(10.0, 1e-6, DEFAULT_MAX_ITER, new StopOnEvent());
    this.minAltitude = minAltitude;
    this.earthRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  }

  private MinAltitudeDetector(
      EventDetectionSettings settings,
      EventHandler handler,
      double minAltitude,
      double earthRadius) {
    super(settings, handler);
    this.minAltitude = minAltitude;
    this.earthRadius = earthRadius;
  }

  @Override
  protected MinAltitudeDetector create(
      EventDetectionSettings detectionSettings, EventHandler newHandler) {
    return new MinAltitudeDetector(detectionSettings, newHandler, minAltitude, earthRadius);
  }

  /** Positive above min altitude, negative below -> stops propagation. */
  @Override
  public double g(SpacecraftState state) {
    double alt = state.getPVCoordinates().getPosition().getNorm() - earthRadius;
    return alt - minAltitude;
  }
}
