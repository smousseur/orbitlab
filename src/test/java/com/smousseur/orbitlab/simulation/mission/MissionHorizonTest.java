package com.smousseur.orbitlab.simulation.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * The restitution horizon's resolution rules (spec {@code
 * docs/mission-horizon/01-horizon-explicite.md} §3). Pure: nothing here propagates.
 */
class MissionHorizonTest {

  private static final AbsoluteDate LAUNCH = AbsoluteDate.J2000_EPOCH;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /** A circular state at the given altitude, {@code ascentSeconds} after launch. */
  private static SpacecraftState circularState(double altitude, double ascentSeconds) {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    double v = FastMath.sqrt(Constants.WGS84_EARTH_MU / r);
    return new SpacecraftState(
        new CartesianOrbit(
            new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
            OrekitService.get().gcrf(),
            LAUNCH.shiftedBy(ascentSeconds),
            Constants.WGS84_EARTH_MU));
  }

  /** The Keplerian period of a circular orbit at that altitude, computed independently. */
  private static double periodAt(double altitude) {
    double a = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    return 2.0 * FastMath.PI * FastMath.sqrt(a * a * a / Constants.WGS84_EARTH_MU);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Revolutions
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void revolutions_countsPeriodsOfTheAchievedOrbit() {
    SpacecraftState insertion = circularState(550_000.0, 600.0);

    double coast = new MissionHorizon.Revolutions(48).finalCoastSeconds(LAUNCH, insertion);

    assertEquals(48 * periodAt(550_000.0), coast, 1.0);
  }

  /**
   * The derived LEO default must land where the spec says it does — around three days. This is the
   * number a user sees prefilled in the wizard, so it is worth pinning rather than merely deriving.
   */
  @Test
  void revolutions_leoDefaultIsAboutThreeDays() {
    SpacecraftState insertion = circularState(550_000.0, 600.0);

    double days =
        MissionHorizon.defaultFor(MissionType.LEO).finalCoastSeconds(LAUNCH, insertion)
            / MissionHorizon.SECONDS_PER_DAY;

    assertTrue(days > 3.0 && days < 3.4, () -> "expected ~3.2 days, got " + days);
  }

  /**
   * An orbit with no Keplerian period cannot be counted in revolutions. It must degrade to the
   * documented fallback rather than produce a NaN-length coast — this method is called from inside
   * {@code MissionOptimizer.optimize()}, where anything thrown is read as "load infeasible".
   */
  @Test
  void revolutions_fallsBackOnAnUnboundOrbit() {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 550_000.0;
    double escape = FastMath.sqrt(2.0 * Constants.WGS84_EARTH_MU / r);
    SpacecraftState hyperbolic =
        new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, escape * 1.2, 0)),
                OrekitService.get().gcrf(),
                LAUNCH.shiftedBy(600.0),
                Constants.WGS84_EARTH_MU));

    double coast = new MissionHorizon.Revolutions(48).finalCoastSeconds(LAUNCH, hyperbolic);

    assertEquals(MissionHorizon.UNRESOLVED_FALLBACK_SECONDS, coast, 0.0);
  }

  @Test
  void revolutions_rejectsANonPositiveCount() {
    assertThrows(IllegalArgumentException.class, () -> new MissionHorizon.Revolutions(0));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // FixedDuration
  // ─────────────────────────────────────────────────────────────────────────

  /** A total duration counted from launch: the ascent comes out of it, it is not added to it. */
  @Test
  void fixedDuration_subtractsTheAscent() {
    SpacecraftState insertion = circularState(550_000.0, 600.0);

    double coast = new MissionHorizon.FixedDuration(86_400.0).finalCoastSeconds(LAUNCH, insertion);

    assertEquals(86_400.0 - 600.0, coast, 0.0);
  }

  /**
   * Asking for less than the ascent takes yields the ascent, not an error: this resolves on a
   * background thread, where throwing would surface as a failed mission rather than as a short one.
   */
  @Test
  void fixedDuration_clampsToZeroBelowTheAscent() {
    SpacecraftState insertion = circularState(550_000.0, 600.0);

    double coast = new MissionHorizon.FixedDuration(120.0).finalCoastSeconds(LAUNCH, insertion);

    assertEquals(0.0, coast, 0.0);
  }

  @Test
  void fixedDuration_rejectsANonPositiveDuration() {
    assertThrows(IllegalArgumentException.class, () -> new MissionHorizon.FixedDuration(0.0));
    assertThrows(
        IllegalArgumentException.class, () -> new MissionHorizon.FixedDuration(Double.NaN));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // TrailingCoast, cap, defaults
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * The legacy case, and the reason it exists: independent of the insertion date, so {@code
   * Mission}'s default reproduces the old constant exactly whatever the mission's state.
   */
  @Test
  void trailingCoast_ignoresTheAscent() {
    double coast =
        new MissionHorizon.TrailingCoast(86_164.0)
            .finalCoastSeconds(LAUNCH, circularState(550_000.0, 600.0));

    assertEquals(86_164.0, coast, 0.0);
  }

  @Test
  void everyCase_isCappedAtThirtyDays() {
    SpacecraftState insertion = circularState(550_000.0, 600.0);

    assertEquals(
        MissionHorizon.MAX_COAST_SECONDS,
        new MissionHorizon.FixedDuration(365 * MissionHorizon.SECONDS_PER_DAY)
            .finalCoastSeconds(LAUNCH, insertion),
        0.0);
    assertEquals(
        MissionHorizon.MAX_COAST_SECONDS,
        new MissionHorizon.Revolutions(10_000).finalCoastSeconds(LAUNCH, insertion),
        0.0);
    assertEquals(
        MissionHorizon.MAX_COAST_SECONDS,
        new MissionHorizon.TrailingCoast(365 * MissionHorizon.SECONDS_PER_DAY)
            .finalCoastSeconds(LAUNCH, insertion),
        0.0);
  }

  @Test
  void defaults_areRevolutionsPerMissionType() {
    assertEquals(
        new MissionHorizon.Revolutions(MissionHorizon.DEFAULT_LEO_REVOLUTIONS),
        MissionHorizon.defaultFor(MissionType.LEO));
    assertEquals(
        new MissionHorizon.Revolutions(MissionHorizon.DEFAULT_GEO_REVOLUTIONS),
        MissionHorizon.defaultFor(MissionType.GEO));
  }

  /** A fresh mission must behave exactly as it did before the horizon existed. */
  @Test
  void missionDefault_isTheLegacyTrailingCoast() {
    Mission mission =
        new Mission("horizon default", null, null, null) {
          @Override
          public SpacecraftState getInitialState(AbsoluteDate initialDate) {
            return null;
          }
        };

    assertEquals(new MissionHorizon.TrailingCoast(86_164.0), mission.getHorizon());
  }
}
