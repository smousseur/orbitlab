package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Base class for mission optimization tests. Provides helpers for extracting altitude data from
 * pre-computed ephemeris.
 */
public class AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(AbstractTrajectoryOptimizerTest.class);

  public static final double ORBIT_MARGIN_RATIO = 0.07;

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

  void testMission(Mission mission, double perigeeAltitude, double apogeeAltitude) {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    SpacecraftState initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);

    MissionOptimizer optimizer = new MissionOptimizer(mission, 40_000);
    MissionComputeResult computeResult = optimizer.optimize();
    MissionEphemeris ephemeris = computeResult.ephemeris();

    MinMaxAltitudeResults results = extractMinMaxAltitudes(ephemeris, "Coasting");

    logger.info(
        "[{}km] Max coast altitude: {} m", (int) (apogeeAltitude / 1000), results.maxAltitude);
    logger.info(
        "[{}km] Min coast altitude: {} m", (int) (perigeeAltitude / 1000), results.minAltitude);

    MissionEphemerisPoint last = ephemeris.lastPoint();
    KeplerianOrbit finalOrbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            OrekitService.get().gcrf(),
            last.time(),
            Constants.WGS84_EARTH_MU);
    logger.info(
        "[{}/{} km] Final inclination: {} rad ({}°)",
        (int) (perigeeAltitude / 1000),
        (int) (apogeeAltitude / 1000),
        finalOrbit.getI(),
        FastMath.toDegrees(finalOrbit.getI()));

    double errorApogeeMargin = ORBIT_MARGIN_RATIO * apogeeAltitude;
    Assertions.assertTrue(
        Math.abs(results.maxAltitude - apogeeAltitude) <= errorApogeeMargin,
        () ->
            String.format(
                "Max coast altitude %.0f m not within %.0f m of target %.0f m",
                results.maxAltitude, errorApogeeMargin, apogeeAltitude));
    double errorPerigeeMargin = ORBIT_MARGIN_RATIO * perigeeAltitude;
    Assertions.assertTrue(
        Math.abs(results.minAltitude - perigeeAltitude) <= errorPerigeeMargin,
        () ->
            String.format(
                "Min coast altitude %.0f m not within %.0f m of target %.0f m",
                results.minAltitude, errorPerigeeMargin, perigeeAltitude));
  }
}
