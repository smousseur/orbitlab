package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * <b>MIS-7 / P1, test T0 — does the commanded launch azimuth control the orbital plane?</b> (spec
 * {@code docs/earth-orbit/01-mission-terre-parametrable.md} §2 and §9).
 *
 * <p><b>Why this test exists before any code.</b> The roadmap fiche assumes the three new orbit
 * types (polar, SSO, MEO) are a matter of passing {@code launchAzimuth} and {@code
 * targetInclination}, both of which the ascent already accepts —
 * {@link AscentSequence#gravityTurn(AscentProfile, GravityTurnConstraints, double, double)} takes
 * them, and {@link Physics#getLaunchAzimuth(double, double)} derives the azimuth. The spec claims
 * that plumbing is <em>inert</em>: the pitch kick rotates only the radial component of the velocity
 * (a few m/s against ~463 m/s of Earth-rotation entrainment), and {@code
 * GravityTurnAttitudeProvider} then thrusts along the current tangential velocity, which keeps the
 * thrust inside the {@code (r, v)} plane and therefore freezes whatever plane the kick left behind.
 *
 * <p>That claim is reasoning over the code plus back-of-the-envelope figures, not a measurement.
 * This test is the measurement, and it is the decision point of P1:
 *
 * <ul>
 *   <li><b>green</b> — the azimuth has no authority, the ascent needs a commanded-plane attitude
 *       (spec §4) and MIS-7 is not the parameter-only change the fiche assumes;
 *   <li><b>red</b> — the azimuth does steer the plane, spec §4 is unnecessary, and MIS-7 collapses
 *       back to wiring the dormant overloads.
 * </ul>
 *
 * <p><b>These fixtures are meant to die.</b> They characterise the ascent as it flies <em>today</em>
 * . Once P1.b lands the commanded-plane attitude, they turn red on purpose and are replaced by
 * {@code AscentPlaneControlTest} (T1), which asserts the opposite. A failure here after P1.b is the
 * signal that the migration worked, not a regression — check the git history before touching it.
 *
 * <p>Everything is measured at <b>fixed variables</b>: no CMA-ES, no seed, no optimizer. The only
 * quantity that varies between two runs of a fixture is the commanded azimuth.
 */
class AscentAzimuthAuthorityTest {
  private static final Logger logger = LogManager.getLogger(AscentAzimuthAuthorityTest.class);

  /** Kourou, the site every mission flies from today ({@code EarthMission.DEFAULT_LATITUDE}). */
  private static final double LAT_DEG = 5.23;

  private static final double TARGET_ALT = 400_000.0;

  /** Due east: the azimuth of every mission in the catalog today. */
  private static final double EAST_DEG = 90.0;

  /** Due north: the polar command, the largest plane change the site allows. */
  private static final double NORTH_DEG = 0.0;

  /** Due south: the descending-node branch of the same polar target. */
  private static final double SOUTH_DEG = 180.0;

  /**
   * The azimuth a 700 km sun-synchronous orbit needs from Kourou: {@code asin(cos(98.19°) /
   * cos(5.23°))}, i.e. slightly west of north — a retrograde launch (spec §5).
   */
  private static final double SSO_DEG = -8.22;

  /**
   * Duration of the second burn, fixed. Long enough for the ascent to be representative (the plane
   * question is not settled in the first two seconds), short enough not to deplete the Falcon Heavy
   * upper stage (250 s ≈ 72 t of its 107.5 t load).
   */
  private static final double BURN2_SECONDS = 250.0;

  /** Power-law exponent of the pitch-over, the second CMA-ES variable, held fixed. */
  private static final double TURN_EXPONENT = 0.32;

  /**
   * How much the achieved inclination may move across the whole azimuth range before the azimuth
   * counts as having authority. The reachable span from Kourou is 5.23° → 90°, so 1° is a little
   * over 1 % of the span — comfortably above numerical noise, far below anything usable.
   */
  private static final double AUTHORITY_THRESHOLD_DEG = 1.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  // ════════════════════════════════════════════════════════════════════════
  // T0.1 — the kick alone (kinematic, no propagation)
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Isolates spec §2.1: the pitch kick itself. Commanding due north instead of due east should, if
   * the azimuth meant anything, swing the horizontal velocity a quarter turn. It does not: the kick
   * rotates the radial component only, and the tangential component it leaves untouched is the
   * Earth-rotation entrainment, two orders of magnitude larger.
   */
  @Test
  void pitchKick_barelyMovesTheHorizontalHeading() {
    SpacecraftState entry = postVerticalAscentState();
    AscentProfile profile = Launchers.FALCON_HEAVY.ascentProfile();
    double kick = FastMath.toRadians(profile.pitchKickAngleDeg());

    SpacecraftState east = Physics.applyPitchKick(entry, kick, FastMath.toRadians(EAST_DEG));
    SpacecraftState north = Physics.applyPitchKick(entry, kick, FastMath.toRadians(NORTH_DEG));

    double headingEast = headingDeg(east);
    double headingNorth = headingDeg(north);
    double inclinationEast = inclinationDeg(east);
    double inclinationNorth = inclinationDeg(north);

    logger.info(
        "T0.1 pitch kick ({}° from vertical), |v| = {} m/s at entry:",
        fmt(profile.pitchKickAngleDeg(), 1),
        fmt(entry.getPVCoordinates().getVelocity().getNorm(), 1));
    logger.info(
        "  azimuth commanded 90° (east)  -> heading {}°, osculating inclination {}°",
        fmt(headingEast, 3),
        fmt(inclinationEast, 4));
    logger.info(
        "  azimuth commanded  0° (north) -> heading {}°, osculating inclination {}°",
        fmt(headingNorth, 3),
        fmt(inclinationNorth, 4));
    logger.info(
        "  swing obtained: {}° of heading, {}° of inclination, for 90° commanded",
        fmt(FastMath.abs(headingNorth - headingEast), 3),
        fmt(FastMath.abs(inclinationNorth - inclinationEast), 4));

    assertTrue(
        FastMath.abs(headingNorth - headingEast) < AUTHORITY_THRESHOLD_DEG,
        () ->
            "the pitch kick was expected to have no authority over the heading, but commanding "
                + "north instead of east swung it by "
                + fmt(FastMath.abs(headingNorth - headingEast), 3)
                + "° — spec §2.1 is wrong, revisit MIS-7 P1 before implementing §4");
    assertEquals(
        LAT_DEG,
        inclinationNorth,
        AUTHORITY_THRESHOLD_DEG,
        "a polar command still leaves the plane at the site latitude");
  }

  // ════════════════════════════════════════════════════════════════════════
  // T0.2 — the whole gravity turn (the headline measurement)
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Spec §2.2, the measurement MIS-7 hinges on: fly the entire gravity turn to MECO under four
   * commanded azimuths and read the achieved inclination. Same entry state, same variables, same
   * vehicle — the azimuth is the only difference.
   *
   * <p>Each case is compared against what the azimuth <em>would</em> reach if it had authority,
   * {@code cos i = cos φ · sin A}, and the ratio between the two is logged as an authority
   * percentage. The fixture fails if any command moves the plane by more than {@link
   * #AUTHORITY_THRESHOLD_DEG}.
   */
  @Test
  void gravityTurn_achievedInclinationIsInsensitiveToTheCommandedAzimuth() {
    SpacecraftState entry = postVerticalAscentState();
    Mission mission = leoMission();
    double reference = flyGravityTurn(mission, entry, EAST_DEG);

    logger.info(
        "T0.2 gravity turn to MECO (burn 2 = {} s, exponent {}), launch latitude {}°:",
        fmt(BURN2_SECONDS, 0),
        fmt(TURN_EXPONENT, 2),
        fmt(LAT_DEG, 2));
    logger.info(
        "  azimuth  90° (east, today's flight) -> achieved inclination {}°", fmt(reference, 4));

    for (double azimuthDeg : new double[] {NORTH_DEG, SOUTH_DEG, SSO_DEG}) {
      double achieved = flyGravityTurn(mission, entry, azimuthDeg);
      double obtainedSwing = achieved - reference;
      double reachableSwing = reachableInclinationDeg(azimuthDeg) - reachableInclinationDeg(EAST_DEG);
      double authorityPercent = 100.0 * obtainedSwing / reachableSwing;

      logger.info(
          "  azimuth {}° -> achieved {}°, reachable {}° if authoritative"
              + " => swing {}° of {}° = {} % authority",
          fmt(azimuthDeg, 2),
          fmt(achieved, 4),
          fmt(reachableInclinationDeg(azimuthDeg), 2),
          fmt(obtainedSwing, 4),
          fmt(reachableSwing, 2),
          fmt(authorityPercent, 2));

      assertTrue(
          FastMath.abs(obtainedSwing) < AUTHORITY_THRESHOLD_DEG,
          () ->
              "commanding azimuth "
                  + fmt(azimuthDeg, 2)
                  + "° moved the achieved inclination by "
                  + fmt(obtainedSwing, 4)
                  + "° (reachable "
                  + fmt(reachableSwing, 2)
                  + "°) — the azimuth DOES steer the plane, so spec §4 (commanded-plane"
                  + " attitude) is unnecessary and MIS-7 P1 must be rewritten around the"
                  + " dormant overloads instead");
    }
  }

  // ════════════════════════════════════════════════════════════════════════
  // T0.3 — the same question through the wiring a mission would use
  // ════════════════════════════════════════════════════════════════════════

  /**
   * The end-to-end version of T0.2: instead of driving {@link GravityTurnManeuver} directly, this
   * flies the three explicit ascent phases built by the <b>dormant four-argument overload</b> of
   * {@link AscentSequence#gravityTurn}, the one a parameterised mission would call. It answers the
   * objection that the authority might live in the wiring rather than in the maneuver.
   *
   * <p>The inclination is passed in <b>radians</b>, i.e. the unit {@link Physics#getLaunchAzimuth}
   * actually consumes, not the degrees the overload's javadoc announces (spec §1.1b). That is the
   * interpretation most favourable to the azimuth having authority: it yields the correct polar
   * azimuth of 0°.
   */
  @Test
  void wiredAscent_commandingAPolarInclinationChangesNothing() {
    SpacecraftState entry = postVerticalAscentState();

    double dormantPath = flyWiredAscent(entry, 0.0, 0.0);
    double polarCommand =
        flyWiredAscent(entry, FastMath.toRadians(LAT_DEG), FastMath.toRadians(90.0));

    logger.info("T0.3 three-phase ascent through AscentSequence.gravityTurn(...):");
    logger.info(
        "  (0, 0)          — the overload every mission uses today -> inclination {}°",
        fmt(dormantPath, 4));
    logger.info(
        "  (lat, i = 90°)  — the dormant parameterised overload    -> inclination {}°",
        fmt(polarCommand, 4));
    logger.info("  difference: {}° for a 90° polar command", fmt(polarCommand - dormantPath, 4));

    assertTrue(
        FastMath.abs(polarCommand - dormantPath) < AUTHORITY_THRESHOLD_DEG,
        () ->
            "wiring the parameterised overload moved the achieved inclination by "
                + fmt(polarCommand - dormantPath, 4)
                + "° — the plumbing is not inert after all, spec §1 and §2 must be corrected");
  }

  // ════════════════════════════════════════════════════════════════════════
  // T0.4 — the two defects of the azimuth formula (spec §1.1)
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Characterises {@link Physics#getLaunchAzimuth}, whose two defects are invisible today because
   * every caller passes {@code (0, 0)}:
   *
   * <ul>
   *   <li><b>the guard is wrong</b> — {@code launchLatitude != 0 && targetInclination != 0} sends a
   *       polar launch from the equator down the "due east" branch, where the answer is due north;
   *   <li><b>the unit is radians</b>, while {@link AscentSequence#gravityTurn(AscentProfile,
   *       GravityTurnConstraints, double, double)} and {@code GravityTurnFirstBurnStage} both
   *       document their arguments as degrees. One of the two is lying.
   * </ul>
   *
   * <p>This fixture asserts the <em>current</em> behaviour, defects included, so that fixing them in
   * P1.a turns it red — the intended reminder to delete it.
   */
  @Test
  void launchAzimuthFormula_readsRadiansAndMissesTheEquatorialPolarCase() {
    double polarFromEquator = Physics.getLaunchAzimuth(0.0, FastMath.toRadians(90.0));
    double polarFromKourou =
        Physics.getLaunchAzimuth(FastMath.toRadians(LAT_DEG), FastMath.toRadians(90.0));
    double degreesInterpretation = Physics.getLaunchAzimuth(LAT_DEG, 90.0);

    logger.info("T0.4 Physics.getLaunchAzimuth:");
    logger.info(
        "  (lat 0 rad, i = 90°) -> {}° (correct answer: 0°, due north — the guard mis-fires)",
        fmt(FastMath.toDegrees(polarFromEquator), 2));
    logger.info(
        "  (lat 5.23° in rad, i = 90° in rad) -> {}° (correct)",
        fmt(FastMath.toDegrees(polarFromKourou), 2));
    logger.info(
        "  (5.23, 90.0) passed as DEGREES, as the ascent javadoc announces -> {}°",
        fmt(FastMath.toDegrees(degreesInterpretation), 2));

    assertEquals(
        90.0,
        FastMath.toDegrees(polarFromEquator),
        1.0e-9,
        "current behaviour: a polar launch from the equator wrongly returns due east");
    assertEquals(
        0.0,
        FastMath.toDegrees(polarFromKourou),
        1.0e-9,
        "current behaviour: the formula is correct when fed radians");
    assertTrue(
        FastMath.abs(FastMath.toDegrees(degreesInterpretation)) > 1.0,
        "current behaviour: feeding degrees yields a meaningless azimuth");
  }

  // ════════════════════════════════════════════════════════════════════════
  // T0.5 — the local frame the kick is expressed in (spec §1.1c)
  // ════════════════════════════════════════════════════════════════════════

  /**
   * A third defect, found by T0.1 rather than by reading: {@link Physics#applyPitchKick} builds its
   * local horizontal basis as {@code east = zenith × north}, which is <b>west</b>. At the equator
   * {@code r̂ = x̂}, {@code n̂ = ẑ} and {@code x̂ × ẑ = −ŷ}, while geographic east is {@code +ŷ =
   * ẑ × r̂}.
   *
   * <p>Its azimuths are therefore counter-clockwise from north: 0° is due north and 180° due south
   * (both correct, the flip is on the east–west axis alone), but 90° points due <b>west</b>. The
   * standard, clockwise-from-north azimuth that {@link Physics#getLaunchAzimuth} returns is thus
   * mirrored the moment it is handed to the kick — {@code A → −A}.
   *
   * <p><b>Why it has stayed invisible.</b> Every mission commands 90°, where the kick has no
   * authority anyway (T0.1/T0.2): the ~2.5 m/s it misplaces are lost in 463 m/s of eastward
   * entrainment. It stops being harmless the day the azimuth gains authority (spec §4), where it
   * would fly the mirror image of any inclined prograde plane.
   *
   * <p>Characterisation fixture: it asserts the flip as it stands today, so fixing the basis in
   * P1.a turns it red.
   */
  @Test
  void pitchKick_horizontalBasisHasEastAndWestSwapped() {
    SpacecraftState entry = postVerticalAscentState();
    double kick = FastMath.toRadians(Launchers.FALCON_HEAVY.ascentProfile().pitchKickAngleDeg());
    SpacecraftState kicked = Physics.applyPitchKick(entry, kick, FastMath.toRadians(EAST_DEG));

    Vector3D deltaV =
        kicked
            .getPVCoordinates()
            .getVelocity()
            .subtract(entry.getPVCoordinates().getVelocity());
    Vector3D geographicEast =
        Vector3D.crossProduct(Vector3D.PLUS_K, entry.getPosition()).normalize();
    double eastward = Vector3D.dotProduct(deltaV, geographicEast);

    logger.info("T0.5 pitch kick commanded due east (90°), impulse in the local frame:");
    logger.info(
        "  |Δv| = {} m/s, of which {} m/s along geographic east (ẑ × r̂)",
        fmt(deltaV.getNorm(), 3),
        fmt(eastward, 3));
    logger.info(
        "  eastward entrainment carried at entry: {} m/s",
        fmt(
            Vector3D.dotProduct(entry.getPVCoordinates().getVelocity(), geographicEast),
            1));

    assertTrue(
        eastward < 0.0,
        () ->
            "current behaviour: an azimuth commanded due east pushes west; measured "
                + fmt(eastward, 3)
                + " m/s along geographic east. A positive value means the basis has been fixed —"
                + " delete this fixture and update spec §1.1c");
  }

  // ════════════════════════════════════════════════════════════════════════
  // T0.6 — what fixing the flipped basis would cost the calibrated baselines
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Prices the correction of §1.1c <b>before</b> writing it, using the mirror itself: since the
   * basis has east and west swapped, commanding {@code −90°} today produces exactly the kick a
   * corrected basis would produce for a due-east command. Flying both and differencing them gives
   * the displacement the fix would inflict on the recorded ascent baselines, with no production code
   * touched.
   *
   * <p>The reference tolerances it is read against are the N2 ones pinned by {@code
   * AscentBaselineN2Test} and {@code GravityTurnReplayConsistencyTest}: 10 m of position and 0.05
   * m/s of velocity at MECO. A displacement above them means correcting the basis is a
   * <b>re-calibration</b>, not a silent bug fix — spec §1.1.1.
   *
   * <p>No threshold is asserted beyond the propagation having actually flown: this fixture exists to
   * produce a number for the spec, and that number is expected to change the day the fix lands.
   */
  @Test
  void flippedBasis_pricesTheBaselineDisplacementOfItsOwnCorrection() {
    SpacecraftState entry = postVerticalAscentState();
    Mission mission = leoMission();

    SpacecraftState asFlown = flyGravityTurnState(mission, entry, EAST_DEG);
    // Same geographic direction, expressed in the corrected basis: the mirror cancels the flip.
    SpacecraftState asCorrected = flyGravityTurnState(mission, entry, -EAST_DEG);

    double deltaPosition =
        Vector3D.distance(asFlown.getPosition(), asCorrected.getPosition());
    double deltaVelocity =
        Vector3D.distance(
            asFlown.getPVCoordinates().getVelocity(),
            asCorrected.getPVCoordinates().getVelocity());

    logger.info("T0.6 cost of correcting the flipped basis (§1.1c), measured at MECO:");
    logger.info(
        "  Δposition = {} m (N2 baseline tolerance: 10 m)", fmt(deltaPosition, 1));
    logger.info(
        "  Δvelocity = {} m/s (N2 baseline tolerance: 0.05 m/s)", fmt(deltaVelocity, 4));
    logger.info(
        "  Δinclination = {}°",
        fmt(FastMath.abs(inclinationDeg(asCorrected) - inclinationDeg(asFlown)), 4));
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  /** Falcon Heavy, fully loaded, LEO 400 km from Kourou — the historical default profile. */
  private static LEOMission leoMission() {
    return new LEOMission("T0 azimuth authority", TARGET_ALT);
  }

  /**
   * The state at gravity-turn entry: the mission's own vertical ascent, actually flown. This is the
   * sub-orbital climbing regime the kick is applied in, where the Earth-rotation entrainment
   * dominates the velocity — a synthetic orbital state would hide exactly what is being measured.
   */
  private static SpacecraftState postVerticalAscentState() {
    LEOMission mission = leoMission();
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    mission.setCurrentState(mission.getInitialState(epoch));
    MissionStage verticalAscent = mission.getStages().getFirst();
    return verticalAscent.propagateStandalone(mission.getCurrentState(), mission);
  }

  /**
   * Flies the gravity turn to MECO under one commanded azimuth and returns the achieved
   * inclination, in degrees.
   *
   * @param mission the mission supplying the vehicle
   * @param entry the state at gravity-turn entry, shared by every case
   * @param azimuthDeg the commanded launch azimuth (degrees from north, clockwise)
   * @return the osculating inclination at MECO, in degrees
   */
  private static double flyGravityTurn(Mission mission, SpacecraftState entry, double azimuthDeg) {
    return inclinationDeg(flyGravityTurnState(mission, entry, azimuthDeg));
  }

  /**
   * Flies the gravity turn to MECO under one commanded azimuth and returns the MECO state itself,
   * for the fixtures that compare more than the inclination.
   *
   * @param mission the mission supplying the vehicle
   * @param entry the state at gravity-turn entry, shared by every case
   * @param azimuthDeg the commanded launch azimuth (degrees, in the kick's own convention)
   * @return the state at MECO
   */
  private static SpacecraftState flyGravityTurnState(
      Mission mission, SpacecraftState entry, double azimuthDeg) {
    AscentProfile profile = Launchers.FALCON_HEAVY.ascentProfile();
    GravityTurnManeuver maneuver =
        new GravityTurnManeuver(
            mission.getVehicle(),
            entry.getMass(),
            FastMath.toRadians(profile.pitchKickAngleDeg()),
            FastMath.toRadians(azimuthDeg),
            profile.interstageCoastDuration());
    double[] variables = {maneuver.getStagingCompleteTime() + BURN2_SECONDS, TURN_EXPONENT};

    SpacecraftState meco = maneuver.propagateForOptimization(entry, variables);
    // propagateForOptimization returns the (unflown) kicked entry state as a penalty when the
    // propagation throws. Without this check a failed case would be read as "the azimuth changed
    // nothing", which is the very conclusion under test.
    assertTrue(
        meco.getMass() < entry.getMass() - 1.0,
        () -> "the gravity turn must actually fly at azimuth " + fmt(azimuthDeg, 2) + "°");
    return meco;
  }

  /**
   * Flies the three explicit ascent phases exactly as {@code StageChainRunner} drives them on the
   * replay path, with the ascent built by the parameterised overload.
   *
   * @param entry the state at gravity-turn entry
   * @param launchLatitude the latitude handed to the overload (radians, see the caller)
   * @param targetInclination the inclination handed to the overload (radians, see the caller)
   * @return the osculating inclination at MECO, in degrees
   */
  private static double flyWiredAscent(
      SpacecraftState entry, double launchLatitude, double targetInclination) {
    LEOMission mission = leoMission();
    AscentProfile profile = Launchers.FALCON_HEAVY.ascentProfile();
    List<MissionStage> ascent =
        AscentSequence.gravityTurn(
            profile,
            GravityTurnConstraints.forTarget(TARGET_ALT),
            launchLatitude,
            targetInclination);

    // The schedule is injected rather than optimized: same fixed variables as T0.2, so the two
    // fixtures measure the same ascent and differ only in how it is driven.
    GravityTurnManeuver reference =
        new GravityTurnManeuver(
            mission.getVehicle(),
            entry.getMass(),
            FastMath.toRadians(profile.pitchKickAngleDeg()),
            Physics.getLaunchAzimuth(launchLatitude, targetInclination),
            profile.interstageCoastDuration());
    double[] variables = {reference.getStagingCompleteTime() + BURN2_SECONDS, TURN_EXPONENT};
    ((GravityTurnFirstBurnStage) ascent.getFirst())
        .applyOptimization(new OptimizationResult(variables, 0.0, entry, 1, entry));

    mission.setCurrentState(entry);
    SpacecraftState meco = StageChainRunner.plain().run(ascent, entry, mission);
    assertTrue(meco.getMass() < entry.getMass() - 1.0, "the wired ascent must actually fly");
    return inclinationDeg(meco);
  }

  // ════════════════════════════════════════════════════════════════════════
  // Measurements
  // ════════════════════════════════════════════════════════════════════════

  /** Osculating inclination of a state's orbit, in degrees. */
  private static double inclinationDeg(SpacecraftState state) {
    KeplerianOrbit orbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(state.getOrbit());
    return FastMath.toDegrees(orbit.getI());
  }

  /**
   * Azimuth of the horizontal component of a state's velocity, in degrees from north, clockwise —
   * the same convention {@link Physics#applyPitchKick} builds its kick direction in, so a commanded
   * azimuth and this measurement are directly comparable.
   */
  private static double headingDeg(SpacecraftState state) {
    Vector3D position = state.getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    Vector3D zenith = position.normalize();
    Vector3D north =
        Vector3D.PLUS_K
            .subtract(new Vector3D(Vector3D.dotProduct(Vector3D.PLUS_K, zenith), zenith))
            .normalize();
    Vector3D east = Vector3D.crossProduct(zenith, north).normalize();
    Vector3D horizontal =
        velocity.subtract(new Vector3D(Vector3D.dotProduct(velocity, zenith), zenith));
    return FastMath.toDegrees(
        FastMath.atan2(
            Vector3D.dotProduct(horizontal, east), Vector3D.dotProduct(horizontal, north)));
  }

  /**
   * The inclination a launch at this azimuth would reach if the azimuth controlled the plane:
   * {@code cos i = cos φ · sin A}, the spherical-trigonometry relation of a non-rotating Earth.
   *
   * @param azimuthDeg the commanded azimuth (degrees from north)
   * @return the reachable inclination in degrees
   */
  private static double reachableInclinationDeg(double azimuthDeg) {
    double cosInclination =
        FastMath.cos(FastMath.toRadians(LAT_DEG)) * FastMath.sin(FastMath.toRadians(azimuthDeg));
    return FastMath.toDegrees(FastMath.acos(cosInclination));
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }
}
