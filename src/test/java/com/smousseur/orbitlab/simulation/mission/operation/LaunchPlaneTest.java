package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.Physics;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;

/**
 * The azimuth derivation and the reachability rules of {@link LaunchPlane} (spec {@code
 * docs/earth-orbit/01-mission-terre-parametrable.md} §3.1 and §8).
 *
 * <p>This is the arithmetic half of what {@code AscentAzimuthAuthorityTest} T0.4 used to
 * <em>characterise as broken</em> in {@code Physics.getLaunchAzimuth}: a guard that sent an
 * equatorial polar launch down the due-east branch, and radians consumed where the ascent
 * documented degrees. Both are gone with that overload, and the fixtures below assert the answers
 * rather than the defects.
 */
class LaunchPlaneTest {

  /** Kourou, the site every profile in the catalog flies from. */
  private static final double KOUROU_DEG = 5.23;

  private static final double KOUROU_RAD = FastMath.toRadians(KOUROU_DEG);

  // ── The derivation ───────────────────────────────────────────────────────

  /**
   * The free plane must yield <b>exactly</b> the double the pre-MIS-7 code returned as a literal.
   * One ulp of difference here is one ulp on the pitch kick of every calibrated trajectory in the
   * catalog, which is why {@code launchAzimuth} writes the {@code sin A = ±1} extremes out instead
   * of trusting {@code asin} to land on the same bits.
   */
  @Test
  void dueEastPlane_reproducesThePreMis7AzimuthBitForBit() {
    double azimuth = LaunchPlane.dueEast(KOUROU_DEG).launchAzimuth(KOUROU_RAD);

    assertEquals(
        Physics.getLaunchAzimuth(),
        azimuth,
        0.0,
        "the site's free plane must give the historical due-east azimuth to the last bit");
  }

  /** The case the old guard got wrong: a polar launch from the equator heads due north, not east. */
  @Test
  void polarFromTheEquator_headsDueNorth() {
    double azimuth = LaunchPlane.ofDegrees(90.0).launchAzimuth(0.0);

    assertEquals(0.0, FastMath.toDegrees(azimuth), 1.0e-9);
  }

  /** A polar target from any reachable latitude is still due north: {@code cos 90° = 0}. */
  @Test
  void polarFromKourou_headsDueNorth() {
    double azimuth = LaunchPlane.ofDegrees(90.0).launchAzimuth(KOUROU_RAD);

    assertEquals(0.0, FastMath.toDegrees(azimuth), 1.0e-9);
  }

  /**
   * A sun-synchronous target is retrograde, so its azimuth is west of north — negative in the
   * clockwise-from-north convention. The reference value is spec §9.1's SSO case: 700 km, {@code i =
   * 98.19°} from Kourou.
   */
  @Test
  void retrogradeTarget_headsWestOfNorth() {
    double azimuth = LaunchPlane.ofDegrees(98.19).launchAzimuth(KOUROU_RAD);

    assertEquals(-8.22, FastMath.toDegrees(azimuth), 0.01);
    assertTrue(azimuth < 0.0, "a retrograde plane is reached west of north");
  }

  /** The two branches are mirrored about the east–west axis and reach the same plane. */
  @Test
  void descendingBranch_isTheMirrorOfTheAscendingOne() {
    double ascending =
        LaunchPlane.ofDegrees(51.6, NodeBranch.ASCENDING).launchAzimuth(KOUROU_RAD);
    double descending =
        LaunchPlane.ofDegrees(51.6, NodeBranch.DESCENDING).launchAzimuth(KOUROU_RAD);

    assertEquals(
        180.0,
        FastMath.toDegrees(ascending) + FastMath.toDegrees(descending),
        1.0e-9,
        "the two azimuths reaching one inclination are A and 180° − A");
  }

  /** A polar target on the descending branch is the due-south launch of spec §2.4. */
  @Test
  void polarDescendingBranch_headsDueSouth() {
    double azimuth =
        LaunchPlane.ofDegrees(90.0, NodeBranch.DESCENDING).launchAzimuth(KOUROU_RAD);

    assertEquals(180.0, FastMath.toDegrees(azimuth), 1.0e-9);
  }

  // ── Which planes must be flown to (spec §4.2) ────────────────────────────

  @Test
  void theSitesFreePlane_isNotCommanded() {
    assertFalse(
        LaunchPlane.dueEast(KOUROU_DEG).commands(KOUROU_RAD),
        "a due-east target must keep the historical, calibrated attitude");
  }

  @Test
  void anInclinedPlane_isCommanded() {
    assertTrue(LaunchPlane.ofDegrees(90.0).commands(KOUROU_RAD));
    assertTrue(LaunchPlane.ofDegrees(51.6).commands(KOUROU_RAD));
    assertTrue(LaunchPlane.ofDegrees(98.19).commands(KOUROU_RAD));
  }

  // ── The refusals (spec §8) ───────────────────────────────────────────────

  /** Refusals name the reachable bound, so the caller learns what to ask for instead. */
  @Test
  void inclinationBelowTheLatitude_isRefusedNamingTheMinimum() {
    OrbitlabException failure =
        assertThrows(
            OrbitlabException.class,
            () -> LaunchPlane.ofDegrees(2.0).requireReachableFrom(KOUROU_DEG));

    assertTrue(
        failure.getMessage().contains("5.230"),
        () -> "the message must name the reachable minimum: " + failure.getMessage());
  }

  @Test
  void inclinationAboveTheRetrogradeBound_isRefused() {
    assertThrows(
        OrbitlabException.class,
        () -> LaunchPlane.ofDegrees(176.0).requireReachableFrom(KOUROU_DEG));
  }

  @Test
  void theReachableBoundsThemselves_areAccepted() {
    LaunchPlane.ofDegrees(KOUROU_DEG).requireReachableFrom(KOUROU_DEG);
    LaunchPlane.ofDegrees(180.0 - KOUROU_DEG).requireReachableFrom(KOUROU_DEG);
  }

  /** A southern site reaches the same band: the bound is {@code |φ|}, not {@code φ}. */
  @Test
  void aSouthernSite_reachesTheSameInclinationBand() {
    LaunchPlane.ofDegrees(90.0).requireReachableFrom(-28.5);

    assertThrows(
        OrbitlabException.class, () -> LaunchPlane.ofDegrees(10.0).requireReachableFrom(-28.5));
  }

  @Test
  void inclinationOutsideZeroToPi_isRefusedAtConstruction() {
    assertThrows(OrbitlabException.class, () -> LaunchPlane.ofDegrees(-1.0));
    assertThrows(OrbitlabException.class, () -> LaunchPlane.ofDegrees(181.0));
    assertThrows(OrbitlabException.class, () -> LaunchPlane.ofDegrees(Double.NaN));
  }

  /** The pole guard the pre-MIS-7 formula already carried, kept and turned into a domain error. */
  @Test
  void aSiteAtThePole_hasNoDefinedAzimuth() {
    assertThrows(
        OrbitlabException.class, () -> LaunchPlane.ofDegrees(90.0).launchAzimuth(FastMath.PI / 2));
  }
}
