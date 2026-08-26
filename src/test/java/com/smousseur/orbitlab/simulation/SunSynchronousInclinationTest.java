package com.smousseur.orbitlab.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;
import org.orekit.utils.Constants;

/**
 * <b>MIS-7 / P1, test T3</b> — {@link Physics#sunSynchronousInclination} against the three
 * reference values of spec {@code docs/earth-orbit/01-mission-terre-parametrable.md} §5.
 *
 * <p><b>What this fixture does and does not claim.</b> It checks the <em>arithmetic</em>, at
 * ±0.02°, and nothing else. Its tolerance must not be read as a precision on a flown inclination:
 * GCRF's equator is J2000's rather than the date's, worth up to 0.145° of frame offset on an
 * inclination read from a propagated state — seven times this tolerance, and measured at 0.008° of
 * divergence over three days by {@code SunSynchronousPrecessionTest}. What a sun-synchronous orbit
 * actually has to do is precess its node at 0.9856°/day, and that is what T4 measures. This one
 * only guarantees the formula is the right formula.
 */
class SunSynchronousInclinationTest {
  private static final Logger logger = LogManager.getLogger(SunSynchronousInclinationTest.class);

  /** Spec §5's reference table: circular orbits, altitude in meters → inclination in degrees. */
  private static final double[][] REFERENCE = {
    {600_000.0, 97.79}, {700_000.0, 98.19}, {800_000.0, 98.61}
  };

  private static final double TOLERANCE_DEG = 0.02;

  @Test
  void theFormula_matchesTheThreeReferenceAltitudes() {
    logger.info("T3 sun-synchronous inclination (circular orbits):");
    for (double[] entry : REFERENCE) {
      double altitude = entry[0];
      double expectedDeg = entry[1];
      double computedDeg =
          FastMath.toDegrees(Physics.sunSynchronousInclinationForAltitude(altitude));

      logger.info(
          "  {} km -> {}° (reference {}°, Δ {}°)",
          fmt(altitude / 1000.0, 0),
          fmt(computedDeg, 4),
          fmt(expectedDeg, 2),
          fmt(computedDeg - expectedDeg, 4));

      assertEquals(
          expectedDeg,
          computedDeg,
          TOLERANCE_DEG,
          () -> "sun-synchronous inclination at " + fmt(altitude / 1000.0, 0) + " km");
    }
  }

  /** Every sun-synchronous orbit is retrograde: the required {@code cos i} is negative. */
  @Test
  void everySunSynchronousOrbit_isRetrograde() {
    for (double[] entry : REFERENCE) {
      double inclination = Physics.sunSynchronousInclinationForAltitude(entry[0]);

      assertTrue(
          FastMath.toDegrees(inclination) > 90.0,
          () -> "an SSO at " + entry[0] + " m must be retrograde");
    }
  }

  /**
   * The inclination rises with altitude: a higher orbit feels less oblateness torque, so it needs
   * to be tilted further from polar to precess at the same rate.
   */
  @Test
  void theInclination_risesWithAltitude() {
    double low = Physics.sunSynchronousInclinationForAltitude(600_000.0);
    double high = Physics.sunSynchronousInclinationForAltitude(800_000.0);

    assertTrue(high > low, "a higher sun-synchronous orbit sits further from polar");
  }

  /** An eccentric orbit needs a different inclination at the same semi-major axis. */
  @Test
  void eccentricityShiftsTheAnswer() {
    double semiMajorAxis = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 700_000.0;
    double circular = Physics.sunSynchronousInclination(semiMajorAxis, 0.0);
    double eccentric = Physics.sunSynchronousInclination(semiMajorAxis, 0.05);

    logger.info(
        "T3 e = 0.05 at the same semi-major axis: {}° instead of {}°",
        fmt(FastMath.toDegrees(eccentric), 4),
        fmt(FastMath.toDegrees(circular), 4));

    // (1 − e²)² < 1 shrinks the numerator, so |cos i| shrinks and the inclination moves toward 90°.
    assertTrue(
        eccentric < circular,
        "an eccentric sun-synchronous orbit sits closer to polar at the same semi-major axis");
  }

  /**
   * Above roughly 12 000 km there is not enough oblateness torque left to keep up with the Sun at
   * any inclination. Refused, rather than returned as a {@code NaN} that would surface as an
   * unreachable plane much later.
   */
  @Test
  void tooHighAnOrbit_hasNoSunSynchronousInclination() {
    OrbitlabException failure =
        assertThrows(
            OrbitlabException.class,
            () -> Physics.sunSynchronousInclinationForAltitude(20_200_000.0));

    assertTrue(
        failure.getMessage().contains("cos i"),
        () -> "the message must say why: " + failure.getMessage());
  }

  /**
   * The point of §5: an SSO is an ordinary {@code EarthOrbit} whose inclination comes from the
   * formula. This checks the two halves fit — the derived inclination is reachable from Kourou, and
   * the azimuth it yields is the retrograde one spec §9.1 records.
   */
  @Test
  void theDerivedInclination_feedsALaunchPlaneDirectly() {
    LaunchPlane plane = LaunchPlane.sunSynchronous(700_000.0);
    plane.requireReachableFrom(5.23);

    double azimuthDeg = FastMath.toDegrees(plane.launchAzimuth(FastMath.toRadians(5.23)));

    logger.info("T3 SSO 700 km from Kourou -> launch azimuth {}°", fmt(azimuthDeg, 2));
    assertEquals(
        FastMath.toDegrees(Physics.sunSynchronousInclinationForAltitude(700_000.0)),
        plane.targetInclinationDeg(),
        1.0e-12,
        "the factory must derive the inclination, not approximate it");
    assertEquals(-8.22, azimuthDeg, 0.05, "spec §9.1's SSO azimuth");
    assertEquals(NodeBranch.ASCENDING, plane.nodeBranch());
  }

  /**
   * The whole of §5 in one fixture: a sun-synchronous mission is an ordinary circular Earth-orbit
   * spec whose inclination came from the formula. No new mission type, no new objective — and the
   * plane is a commanded one, so the composition picks up the plane trim of §4.3 on its own.
   */
  @Test
  void aSunSynchronousMission_isAnOrdinaryCircularEarthOrbitSpec() {
    LaunchPlane plane = LaunchPlane.sunSynchronous(700_000.0);
    double kourou = FastMath.toRadians(5.23);

    assertTrue(plane.commands(kourou), "an SSO plane must be flown to, not fallen into");
    assertTrue(
        plane.launchAzimuth(kourou) < 0.0, "and reached west of north, since it is retrograde");
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }
}
