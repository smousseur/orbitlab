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

  /** Name of the final coast, the phase the achieved orbit is read from. */
  private static final String COAST_STAGE = "Coasting";

  /**
   * How far the flown trajectory may dip below the target perigee before the orbit counts as
   * unsafe, rather than merely perturbed.
   *
   * <p><b>Why the achieved orbit is read at insertion and not from the coast minimum.</b> The
   * mission's analytic stages target an <em>osculating</em> perigee and hit it: measured 2026-08-05
   * on the elliptic 200/1000 profile, the orbit at the end of the trim burn is 200 000 × 1 000 077
   * m — the target to the metre. The coast then flies a full sidereal day under the 8×8 field, and
   * the osculating eccentricity oscillates with a long period: ~5 h 30 after insertion the same
   * orbit reads 182 936 × 1 021 677 m. Sampling the minimum geodetic altitude over that day and
   * comparing it against the target perigee therefore measures a J2 oscillation the mission neither
   * controls nor sees, not an insertion error.
   *
   * <p>The band is close to altitude-independent in absolute terms (δr_p ≈ a·δe with δe ≈ 2.8e-3),
   * which is why a <em>relative</em> tolerance mis-sorts the profiles: the deficit measured 16 906
   * m on the elliptic 200/1000 case that failed at ±14 000 m, and 19 225 m — larger — on the
   * Falcon Heavy 400 km case that passed at ±28 000 m. The profile that passed was the one further
   * off. This floor keeps a genuinely decaying or re-entering trajectory failing while leaving the
   * measured ~19 km band alone.
   */
  private static final double FLOWN_PERIGEE_FLOOR_MARGIN_M = 40_000.0;

  /** Fixed seed for reproducible CMA-ES runs across test executions. */
  protected static final long TEST_SEED = 42L;

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

  MissionComputeResult testMission(Mission mission, double perigeeAltitude, double apogeeAltitude) {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    SpacecraftState initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);

    MissionOptimizer optimizer = new MissionOptimizer(mission, 40_000, TEST_SEED);
    MissionComputeResult computeResult = optimizer.optimize();
    MissionEphemeris ephemeris = computeResult.ephemeris();

    MinMaxAltitudeResults results = extractMinMaxAltitudes(ephemeris, COAST_STAGE);

    logger.info(
        "[{}km] Max coast altitude: {} km",
        (int) (apogeeAltitude / 1000),
        results.maxAltitude / 1000);
    logger.info(
        "[{}km] Min coast altitude: {} km",
        (int) (perigeeAltitude / 1000),
        results.minAltitude / 1000);

    // The achieved orbit, read where the mission delivers it: the first coast sample, i.e. the
    // state the last burn hands over. See FLOWN_PERIGEE_FLOOR_MARGIN_M for why the coast extrema
    // cannot play that role.
    MissionEphemerisPoint insertion = firstPointOfStage(ephemeris, COAST_STAGE);
    KeplerianOrbit insertionOrbit =
        new KeplerianOrbit(
            new PVCoordinates(insertion.position(), insertion.velocity()),
            OrekitService.get().gcrf(),
            insertion.time(),
            Constants.WGS84_EARTH_MU);
    double insertionPerigee =
        insertionOrbit.getA() * (1.0 - insertionOrbit.getE())
            - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
    double insertionApogee =
        insertionOrbit.getA() * (1.0 + insertionOrbit.getE())
            - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
    logger.info(
        "[{}/{} km] Insertion orbit: {} x {} m (e={})",
        (int) (perigeeAltitude / 1000),
        (int) (apogeeAltitude / 1000),
        (long) insertionPerigee,
        (long) insertionApogee,
        insertionOrbit.getE());

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

    logger.info("Final eccentricity: {}", finalOrbit.getE());
    double errorApogeeMargin = ORBIT_MARGIN_RATIO * apogeeAltitude;
    Assertions.assertTrue(
        Math.abs(insertionApogee - apogeeAltitude) <= errorApogeeMargin,
        () ->
            String.format(
                "Insertion apogee %.0f m not within %.0f m of target %.0f m",
                insertionApogee, errorApogeeMargin, apogeeAltitude));
    double errorPerigeeMargin = ORBIT_MARGIN_RATIO * perigeeAltitude;
    Assertions.assertTrue(
        Math.abs(insertionPerigee - perigeeAltitude) <= errorPerigeeMargin,
        () ->
            String.format(
                "Insertion perigee %.0f m not within %.0f m of target %.0f m",
                insertionPerigee, errorPerigeeMargin, perigeeAltitude));

    // The flown trajectory is still checked, but for safety rather than for accuracy: it may drift
    // within the J2 band, it may not head for the ground.
    double flownFloor = perigeeAltitude - FLOWN_PERIGEE_FLOOR_MARGIN_M;
    Assertions.assertTrue(
        results.minAltitude >= flownFloor,
        () ->
            String.format(
                "Min coast altitude %.0f m fell below the safety floor %.0f m"
                    + " (target perigee %.0f m minus the %.0f m J2 band)",
                results.minAltitude, flownFloor, perigeeAltitude, FLOWN_PERIGEE_FLOOR_MARGIN_M));
    return computeResult;
  }

  /**
   * The first ephemeris sample tagged with the given stage — for the final coast, the state the
   * last burn handed over.
   */
  private static MissionEphemerisPoint firstPointOfStage(
      MissionEphemeris ephemeris, String stageName) {
    for (MissionEphemerisPoint pt : ephemeris.allPoints()) {
      if (stageName.equals(pt.stageName())) {
        return pt;
      }
    }
    throw new AssertionError("no '" + stageName + "' samples in the mission ephemeris");
  }
}
