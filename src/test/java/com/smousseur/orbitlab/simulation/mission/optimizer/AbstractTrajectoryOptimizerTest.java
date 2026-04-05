package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;

/**
 * Base class for mission optimization tests. Provides helpers for extracting altitude data from
 * pre-computed ephemeris.
 */
public class AbstractTrajectoryOptimizerTest {

  protected static class MinMaxAltitudeResults {
    double minAltitude = Double.MAX_VALUE;
    double maxAltitude = Double.MIN_VALUE;
  }

  /**
   * Extracts min/max altitude for all ephemeris points in the specified stage.
   *
   * @param ephemeris the pre-computed mission ephemeris
   * @param phaseName the name of the coasting stage to measure
   * @return the min and max altitudes found in that stage
   */
  protected static MinMaxAltitudeResults extractMinMaxAltitudes(
      MissionEphemeris ephemeris, String phaseName) {
    MinMaxAltitudeResults results = new MinMaxAltitudeResults();
    for (MissionEphemerisPoint pt : ephemeris.allPoints()) {
      if (phaseName.equals(pt.stageName())) {
        if (pt.altitudeMeters() < results.minAltitude) {
          results.minAltitude = pt.altitudeMeters();
        }
        if (pt.altitudeMeters() > results.maxAltitude) {
          results.maxAltitude = pt.altitudeMeters();
        }
      }
    }
    return results;
  }
}
