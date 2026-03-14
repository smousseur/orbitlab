package com.smousseur.orbitlab.simulation.mission.detector;

import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Records the minimum and maximum altitude encountered during propagation. Does NOT stop
 * propagation — just tracks the extremes.
 */
public class MinAltitudeTracker extends AbstractDetector<MinAltitudeTracker> {

  private final double altitudeThreshold;
  private final double maxAltitudeThreshold;
  private final AbsoluteDate activeFrom;
  private double minAltitude = Double.MAX_VALUE;
  private double maxAltitude = Double.MIN_VALUE;

  /**
   * Creates a tracker that records altitude extremes, active from the start of propagation.
   *
   * @param altitudeThreshold minimum altitude threshold in meters
   * @param maxAltitudeThreshold maximum altitude threshold in meters
   */
  public MinAltitudeTracker(double altitudeThreshold, double maxAltitudeThreshold) {
    this(altitudeThreshold, maxAltitudeThreshold, null);
  }

  /**
   * Creates a tracker that records altitude extremes, optionally activating only after a given date.
   *
   * @param altitudeThreshold minimum altitude threshold (m)
   * @param maxAltitudeThreshold maximum altitude threshold (m)
   * @param activeFrom only track altitude after this date (null = track from start)
   */
  public MinAltitudeTracker(
      double altitudeThreshold, double maxAltitudeThreshold, AbsoluteDate activeFrom) {
    super(10.0, 1e-3, DEFAULT_MAX_ITER, new ContinueOnEvent());
    this.altitudeThreshold = altitudeThreshold;
    this.maxAltitudeThreshold = maxAltitudeThreshold;
    this.activeFrom = activeFrom;
  }

  private MinAltitudeTracker(
      EventDetectionSettings settings,
      EventHandler handler,
      double altitudeThreshold,
      double maxAltitudeThreshold,
      AbsoluteDate activeFrom,
      double minAltitude,
      double maxAltitude) {
    super(settings, handler);
    this.altitudeThreshold = altitudeThreshold;
    this.maxAltitudeThreshold = maxAltitudeThreshold;
    this.activeFrom = activeFrom;
    this.minAltitude = minAltitude;
    this.maxAltitude = maxAltitude;
  }

  @Override
  protected MinAltitudeTracker create(
      EventDetectionSettings detectionSettings, EventHandler newHandler) {
    return new MinAltitudeTracker(
        detectionSettings,
        newHandler,
        altitudeThreshold,
        maxAltitudeThreshold,
        activeFrom,
        minAltitude,
        maxAltitude);
  }

  @Override
  public double g(SpacecraftState state) {
    double altitude =
        state.getPVCoordinates().getPosition().getNorm() - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

    // Only track after the activation date
    if (activeFrom == null || state.getDate().compareTo(activeFrom) >= 0) {
      if (altitude < minAltitude) {
        minAltitude = altitude;
      }
      if (altitude > maxAltitude) {
        maxAltitude = altitude;
      }
    }

    return altitude - altitudeThreshold;
  }

  /**
   * Returns the minimum altitude recorded during propagation.
   *
   * @return the minimum altitude in meters
   */
  public double getMinAltitude() {
    return minAltitude;
  }

  /**
   * Returns the maximum altitude recorded during propagation.
   *
   * @return the maximum altitude in meters
   */
  public double getMaxAltitude() {
    return maxAltitude;
  }

  /**
   * Returns whether the recorded minimum altitude fell below the configured threshold.
   *
   * @return {@code true} if the minimum altitude threshold was violated
   */
  public boolean hasViolatedMin() {
    return minAltitude < altitudeThreshold;
  }

  /**
   * Returns whether the recorded maximum altitude exceeded the configured threshold.
   *
   * @return {@code true} if the maximum altitude threshold was violated
   */
  public boolean hasViolatedMax() {
    return maxAltitude > maxAltitudeThreshold;
  }
}
