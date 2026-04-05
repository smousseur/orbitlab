package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;

/**
 * Base class for mission optimization tests. Provides helpers for extracting altitude data from
 * pre-computed ephemeris.
 */
public class AbstractTrajectoryOptimizerTest {

  protected static class CoastAltitudeResults {
    double minCoastAltitude = Double.MAX_VALUE;
    double maxCoastAltitude = Double.MIN_VALUE;
  }

  /**
   * Extracts min/max altitude for all ephemeris points in the specified stage.
   *
   * @param ephemeris the pre-computed mission ephemeris
   * @param coastPhaseName the name of the coasting stage to measure
   * @return the min and max altitudes found in that stage
   */
  protected static CoastAltitudeResults extractCoastAltitudes(
      MissionEphemeris ephemeris, String coastPhaseName) {
    CoastAltitudeResults results = new CoastAltitudeResults();
    for (MissionEphemerisPoint pt : ephemeris.allPoints()) {
      if (coastPhaseName.equals(pt.stageName())) {
        if (pt.altitudeMeters() < results.minCoastAltitude) {
          results.minCoastAltitude = pt.altitudeMeters();
        }
        if (pt.altitudeMeters() > results.maxCoastAltitude) {
          results.maxCoastAltitude = pt.altitudeMeters();
        }
      }
    }
    return results;
  }
}
