package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;

/**
 * <b>MIS-7 / P1, test T7</b> — the Earth-rotation assist is signed and projected on the launch
 * azimuth (spec {@code docs/earth-orbit/01-mission-terre-parametrable.md} §7).
 *
 * <p><b>The largest error on the list.</b> {@code ascentDeltaV} used to credit the ascent with the
 * full eastward entrainment, {@code 465 · cos φ}, whatever the heading. That is right due east and
 * wrong everywhere else: a polar launch flies perpendicular to the entrainment and gets none of it,
 * and a retrograde sun-synchronous one flies against it and <em>pays</em>. On a budget sized by
 * inverse Tsiolkovsky, the 529 m/s error of an SSO from Kourou is not a margin detail — the same
 * arithmetic already documents 140 m/s moving a Falcon Heavy upper-stage load from 2 844 to 1 963
 * kg.
 */
class PropellantBudgetAzimuthTest {
  private static final Logger logger = LogManager.getLogger(PropellantBudgetAzimuthTest.class);

  private static final double KOUROU_DEG = 5.23;
  private static final double KOUROU_RAD = FastMath.toRadians(KOUROU_DEG);
  private static final double LEO_ALT = 700_000.0;

  /** {@code 465 · cos(5.23°)}, the full eastward assist the old formula always credited. */
  private static final double FULL_ASSIST = 465.0 * FastMath.cos(KOUROU_RAD);

  private static double assistFor(LaunchPlane plane) {
    // The assist is what the azimuth removes from the ΔV, so read it as a difference against a
    // heading that gets none — this keeps the fixture independent of the losses and of the target.
    double withAzimuth =
        PropellantBudget.ascentDeltaV(LEO_ALT, KOUROU_DEG, plane.launchAzimuth(KOUROU_RAD));
    double withNone = PropellantBudget.ascentDeltaV(LEO_ALT, KOUROU_DEG, 0.0);
    return withNone - withAzimuth;
  }

  // ── The three cases of the §7 table ──────────────────────────────────────

  @Test
  void dueEast_keepsTheFullAssist() {
    double assist = assistFor(LaunchPlane.dueEast(KOUROU_DEG));

    logger.info("T7 due east (90°): assist {} m/s", fmt(assist));
    assertEquals(FULL_ASSIST, assist, 0.5, "a due-east launch collects the whole entrainment");
    assertEquals(463.0, assist, 1.0);
  }

  @Test
  void polar_collectsNoAssistAtAll() {
    double assist = assistFor(LaunchPlane.ofDegrees(90.0));

    logger.info("T7 polar (azimuth 0°): assist {} m/s", fmt(assist));
    assertEquals(0.0, assist, 1.0e-9, "flying due north uses none of the eastward entrainment");
  }

  @Test
  void sunSynchronous_paysForTheEntrainmentInsteadOfCollectingIt() {
    double assist = assistFor(LaunchPlane.ofDegrees(98.19));

    logger.info("T7 SSO 700 km (azimuth −8.22°): assist {} m/s", fmt(assist));
    assertTrue(assist < 0.0, () -> "a retrograde launch pays the entrainment, got " + fmt(assist));
    assertEquals(-66.0, assist, 2.0, "spec §7: −66 m/s from Kourou");
    assertEquals(
        529.0,
        FULL_ASSIST - assist,
        3.0,
        "the error the old unsigned formula made on this mission");
  }

  // ── What it costs, where it is actually spent ────────────────────────────

  /**
   * The consequence the spec cares about: at the same altitude and the same payload, a
   * sun-synchronous mission must be sized <em>heavier</em> than a due-east one. Before the fix both
   * got the same loads, which is how a mission could be planned with propellant it does not have.
   */
  @Test
  void aSunSynchronousMission_isSizedHeavierThanADueEastOne() {
    Spacecraft payload = Spacecraft.LEGACY;
    double[] dueEast =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            payload,
            LEO_ALT,
            KOUROU_DEG,
            LaunchPlane.dueEast(KOUROU_DEG).launchAzimuth(KOUROU_RAD));
    double[] sunSynchronous =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            payload,
            LEO_ALT,
            KOUROU_DEG,
            LaunchPlane.ofDegrees(98.19).launchAzimuth(KOUROU_RAD));

    double topDueEast = dueEast[dueEast.length - 1];
    double topSso = sunSynchronous[sunSynchronous.length - 1];

    logger.info(
        "T7 Falcon Heavy upper stage at 700 km: due east {} kg, SSO {} kg (+{} kg)",
        fmt(topDueEast),
        fmt(topSso),
        fmt(topSso - topDueEast));

    assertTrue(
        topSso > topDueEast,
        () ->
            "an SSO must be sized heavier than a due-east launch to the same altitude: "
                + fmt(topSso)
                + " kg vs "
                + fmt(topDueEast)
                + " kg");
  }

  /**
   * The historical entry point must be untouched: the four-argument overload means due east, and a
   * due-east azimuth must reproduce the pre-MIS-7 number exactly. This is what keeps the calibrated
   * Falcon Heavy and Ariane 62 budgets where they were.
   */
  @Test
  void theDueEastOverload_reproducesThePreMis7Budget() {
    assertEquals(
        PropellantBudget.ascentDeltaV(LEO_ALT, KOUROU_DEG),
        PropellantBudget.ascentDeltaV(LEO_ALT, KOUROU_DEG, Physics.getLaunchAzimuth()),
        0.0,
        "the unqualified overload is the due-east one, to the bit");

    double[] historical =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY, Spacecraft.LEGACY, LEO_ALT, KOUROU_DEG);
    double[] explicit =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            Spacecraft.LEGACY,
            LEO_ALT,
            KOUROU_DEG,
            LaunchPlane.dueEast(KOUROU_DEG).launchAzimuth(KOUROU_RAD));

    assertEquals(historical.length, explicit.length);
    for (int stage = 0; stage < historical.length; stage++) {
      assertEquals(historical[stage], explicit[stage], 0.0, "stage " + stage + " load moved");
    }
  }

  private static String fmt(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }
}
