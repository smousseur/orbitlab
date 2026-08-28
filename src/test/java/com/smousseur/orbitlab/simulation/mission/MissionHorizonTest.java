package com.smousseur.orbitlab.simulation.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.AbsolutePVCoordinates;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * The restitution horizon's resolution rules (spec {@code
 * docs/mission-horizon/01-horizon-explicite.md} §3). Pure: nothing here propagates.
 */
class MissionHorizonTest {

  private static final AbsoluteDate LAUNCH = AbsoluteDate.J2000_EPOCH;

  /** The Moon's equatorial radius, as {@code GravitationalContext.moon()} carries it. */
  private static final double MOON_RADIUS = 1_737_400.0;

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

  /** A circular selenocentric state, declared with the µ a lunar propagator sets on it. */
  private static SpacecraftState selenocentricState(double a, double lunarMu) {
    double v = FastMath.sqrt(lunarMu / a);
    return new SpacecraftState(
        new CartesianOrbit(
            new PVCoordinates(new Vector3D(a, 0, 0), new Vector3D(0, v, 0)),
            OrekitService.get().bodyCentredIcrfFrame(SolarSystemBody.MOON),
            LAUNCH.shiftedBy(600.0),
            lunarMu));
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

  /**
   * MIS-5 / L2 §7.2 — the µ comes off the state, so twelve turns of a lunar orbit last twelve lunar
   * periods.
   *
   * <p>The pre-L2 reading is <b>derived here</b> and not recorded, so the bar is visible. It is not
   * the {@code √(µE/µM) = 9.017} the arithmetic at fixed {@code a} suggests: the method rebuilds
   * the orbit from the PV, so the wrong µ collapses {@code a} towards {@code r/2} as well and the
   * two errors compound. Measured before the change: 3 355.88 s for the 84 809.52 s below, a factor
   * 25.3.
   */
  @Test
  void revolutions_readTheMuOffTheState() {
    double a = MOON_RADIUS + 100_000.0;
    double lunarMu = OrekitService.get().body(SolarSystemBody.MOON).getGM();
    double lunarPeriod = 2.0 * FastMath.PI * FastMath.sqrt(a * a * a / lunarMu);

    double coast =
        new MissionHorizon.Revolutions(12)
            .finalCoastSeconds(LAUNCH, selenocentricState(a, lunarMu));

    assertEquals(12 * lunarPeriod, coast, 1.0);

    // What the Earth constant returned on this very state, derived rather than pinned: vis-viva
    // gives a' = 1/(2/r − v²/µE), and with v² = µM/r that is a' ≈ r/(2 − µM/µE), i.e. about r/2.
    double collapsed = 1.0 / (2.0 / a - (lunarMu / a) / Constants.WGS84_EARTH_MU);
    double asReadWithTheEarthMu =
        12.0
            * 2.0
            * FastMath.PI
            * FastMath.sqrt(collapsed * collapsed * collapsed / Constants.WGS84_EARTH_MU);
    assertTrue(
        coast / asReadWithTheEarthMu > 20.0,
        () ->
            "the pre-L2 reading must be visibly wrong; measured factor 25.3, derived here as "
                + coast / asReadWithTheEarthMu);
  }

  /**
   * MIS-5 / L2 §4.1 — a state carrying no orbit carries no µ, and an unreadable µ is an
   * unresolvable horizon: it takes the fallback that already exists rather than a second one on an
   * Earth constant, which would silently return the wrong period on a lunar arc.
   */
  @Test
  void revolutions_fallsBackWhenTheStateCarriesNoOrbit() {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 550_000.0;
    double v = FastMath.sqrt(Constants.WGS84_EARTH_MU / r);
    SpacecraftState absolutePva =
        new SpacecraftState(
            new AbsolutePVCoordinates(
                OrekitService.get().gcrf(),
                LAUNCH.shiftedBy(600.0),
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0))));
    assertFalse(absolutePva.isOrbitDefined(), "the fixture must carry no orbit");

    double coast = new MissionHorizon.Revolutions(48).finalCoastSeconds(LAUNCH, absolutePva);

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
    // MIS-5 / L3 §6.3 — twelve turns, i.e. 23.6 h of lunar orbit past insertion. A count of
    // revolutions and not a duration, unlike the flyby: this profile ends bound around the Moon.
    assertEquals(
        new MissionHorizon.Revolutions(MissionHorizon.DEFAULT_LUNAR_ORBIT_REVOLUTIONS),
        MissionHorizon.defaultFor(MissionType.LUNAR_ORBIT));
    assertEquals(12, MissionHorizon.DEFAULT_LUNAR_ORBIT_REVOLUTIONS);
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
