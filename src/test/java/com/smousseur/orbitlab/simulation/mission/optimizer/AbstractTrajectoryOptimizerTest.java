package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrbitElements;
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
   * analytic trim targets the <em>mean</em> perigee (spec orbit-reporting/02), which centres the
   * flown excursion on the request instead of perching it at the top of the J2 short-period
   * oscillation. The excursion itself remains — no orbit is flat under J2 — so sampling the minimum
   * geodetic altitude over a sidereal day and comparing it against the target still measures that
   * oscillation, not an insertion error. Only its amplitude has been halved.
   *
   * <p><b>The value is twice the worst case measured after the retargeting, not a round number.</b>
   * Measured 2026-08-05 by {@code GravityTurnFloorProbeTest#flownBandCentringAndCost}: the minimum
   * flown altitude sits 9 835 m under target on the Falcon Heavy 400 km profile and 9 536 m under
   * on the elliptic 200/1000 one — down from 19 225 and 16 906 m before the change. Twice the
   * larger, rounded up, is 20 000 m.
   *
   * <p>A <em>relative</em> tolerance would mis-sort the two profiles, which is why this floor is
   * absolute: the deficits are within 300 m of each other while the targets differ by a factor of
   * two. The floor keeps a genuinely decaying or re-entering trajectory failing while leaving the
   * measured band alone.
   */
  private static final double FLOWN_PERIGEE_FLOOR_MARGIN_M = 20_000.0;

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
    // Both element conventions are LOGGED, and neither is asserted. Read them as diagnostics of a
    // clean insertion, not as the achieved orbit:
    //
    //  - the OSCULATING insertion is no longer circular on a circular target, by construction: the
    //    trim aims an eccentricity of about f so the mean orbit comes out circular;
    //  - the MEAN insertion sits ~9.8 km above the request for the same reason — measured
    //    2026-08-05, a 400 km request inserts at 409 692 x 409 915 m mean.
    //
    // Asserting either against the request would measure the offset between a convention and the
    // flown trajectory, not a targeting error. This class asserted the osculating orbit until
    // 2026-08-05 and the mean one briefly after; both were wrong for the same reason, and the
    // third change is the one that stops chasing conventions (spec orbit-reporting/02 section 5.6).
    logger.info(
        "[{}/{} km] Insertion orbit (osculating): {}",
        (int) (perigeeAltitude / 1000),
        (int) (apogeeAltitude / 1000),
        OrbitElements.osculating(insertionOrbit, Constants.WGS84_EARTH_EQUATORIAL_RADIUS).format());
    logger.info(
        "[{}/{} km] Insertion orbit (mean):       {}",
        (int) (perigeeAltitude / 1000),
        (int) (apogeeAltitude / 1000),
        OrbitElements.mean(insertionOrbit, Constants.WGS84_EARTH_EQUATORIAL_RADIUS)
            .map(OrbitElements::format)
            .orElse("unavailable"));

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

    // The assertions read the FLOWN altitude band over the terminal coast: its minimum against the
    // requested perigee, its maximum against the requested apogee. That is the quantity the trim
    // now targets (spec orbit-reporting/02), it needs no mean theory to be computed, and it is
    // exactly what MissionLoadEvaluator.objectiveMet already reads — so the accuracy bar of these
    // tests and the feasibility gate of the λ campaigns finally measure the same thing.
    double errorApogeeMargin = ORBIT_MARGIN_RATIO * apogeeAltitude;
    Assertions.assertTrue(
        Math.abs(results.maxAltitude - apogeeAltitude) <= errorApogeeMargin,
        () ->
            String.format(
                "Max flown altitude %.0f m not within %.0f m of target apogee %.0f m",
                results.maxAltitude, errorApogeeMargin, apogeeAltitude));
    double errorPerigeeMargin = ORBIT_MARGIN_RATIO * perigeeAltitude;
    Assertions.assertTrue(
        Math.abs(results.minAltitude - perigeeAltitude) <= errorPerigeeMargin,
        () ->
            String.format(
                "Min flown altitude %.0f m not within %.0f m of target perigee %.0f m",
                results.minAltitude, errorPerigeeMargin, perigeeAltitude));

    // Coarse backstop, now largely redundant with the perigee assertion above: it survives because
    // it fails with a message about hitting the ground rather than about a tolerance, which is the
    // right thing to read when a trajectory is genuinely decaying.
    double flownFloor = perigeeAltitude - FLOWN_PERIGEE_FLOOR_MARGIN_M;
    Assertions.assertTrue(
        results.minAltitude >= flownFloor,
        () ->
            String.format(
                "Min coast altitude %.0f m fell below the safety floor %.0f m"
                    + " (target perigee %.0f m minus the %.0f m margin)",
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
