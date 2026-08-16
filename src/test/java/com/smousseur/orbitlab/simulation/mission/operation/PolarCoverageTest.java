package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticPlaneTrimAtNodeStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnFirstBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * <b>MIS-7 / P1, test T5</b> — a polar mission actually covers the poles (spec {@code
 * docs/earth-orbit/01-mission-terre-parametrable.md} §9.2).
 *
 * <p>Inclination is a number; coverage is what a polar orbit is <em>for</em>. This fixture reads the
 * ground track rather than the orbital element, so it fails on anything that would leave the plane
 * short of the poles — including the failure modes an inclination assertion cannot see.
 *
 * <p><b>Why the plane trim is in the chain, demonstrated.</b> The ascent alone lands the plane
 * within ~3.3° of a polar command (measured by {@code AscentPlaneControlTest}: the thrust stays in
 * the target plane, so it never cancels the out-of-plane entrainment). That is coverage to ~86.8°,
 * not to the poles. {@link AnalyticPlaneTrimAtNodeStage}, which {@code EarthOrbitMission} inserts
 * whenever a plane is commanded, is what closes the gap — and this fixture measures both sides of
 * it.
 */
class PolarCoverageTest {
  private static final Logger logger = LogManager.getLogger(PolarCoverageTest.class);

  private static final double LAT_DEG = 5.23;
  private static final double LAT_RAD = FastMath.toRadians(LAT_DEG);
  private static final double TARGET_ALT = 400_000.0;
  private static final double BURN2_SECONDS = 250.0;
  private static final double TURN_EXPONENT = 0.32;

  /** The coverage a polar mission is bought for (spec §9.2). */
  private static final double MIN_GROUND_TRACK_LATITUDE_DEG = 89.0;

  /** Ground-track samples over one revolution. */
  private static final int SAMPLES = 720;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void aPolarMission_sweepsTheGroundTrackToThePoles() {
    LaunchPlane polar = LaunchPlane.ofDegrees(90.0);
    SpacecraftState afterAscent = flyAscent(polar);
    SpacecraftState afterTrim = trimPlane(afterAscent, polar);

    double coverageAfterAscent = maxGroundTrackLatitudeDeg(afterAscent);
    double coverageAfterTrim = maxGroundTrackLatitudeDeg(afterTrim);

    // The orbit and mass lines carry no assertion: they exist so this profile can be recorded in a
    // reference table alongside the optimized ones (PHY-4 lot L0, docs/multi-corps/02-baseline-L0).
    // They go through OrbitElements.format() precisely so the line is character-for-character
    // comparable to the "Achieved orbit" line every other profile is read from.
    logger.info("T5 polar coverage from Kourou (φ = {}°):", fmt(LAT_DEG, 2));
    logger.info(
        "  after the ascent alone -> inclination {}°, ground track reaches {}°",
        fmt(Physics.inclinationDeg(afterAscent), 4),
        fmt(coverageAfterAscent, 3));
    logger.info(
        "      orbit {}, mass {} kg",
        OrbitElements.osculating(afterAscent.getOrbit()).format(),
        fmt(afterAscent.getMass(), 3));
    logger.info(
        "  after the plane trim   -> inclination {}°, ground track reaches {}°",
        fmt(Physics.inclinationDeg(afterTrim), 4),
        fmt(coverageAfterTrim, 3));
    logger.info(
        "      orbit {}, mass {} kg (propellant spent: {} kg)",
        OrbitElements.osculating(afterTrim.getOrbit()).format(),
        fmt(afterTrim.getMass(), 3),
        fmt(afterAscent.getMass() - afterTrim.getMass(), 1));

    assertTrue(
        coverageAfterTrim > MIN_GROUND_TRACK_LATITUDE_DEG,
        () ->
            "a polar mission must sweep past "
                + fmt(MIN_GROUND_TRACK_LATITUDE_DEG, 0)
                + "° of latitude; this one reaches "
                + fmt(coverageAfterTrim, 3)
                + "°");
    assertTrue(
        coverageAfterTrim > coverageAfterAscent,
        "the plane trim is in the chain to close the ascent residual — it must improve coverage");
  }

  /**
   * The other half of the statement: a due-east launch from Kourou covers a 5° band and nothing
   * more, which is exactly why a polar mission could not be flown before MIS-7.
   */
  @Test
  void aDueEastMission_neverLeavesTheEquatorialBand() {
    double coverage = maxGroundTrackLatitudeDeg(flyAscent(LaunchPlane.dueEast(LAT_DEG)));

    logger.info("T5 due-east coverage from Kourou: ground track reaches {}°", fmt(coverage, 3));

    assertTrue(
        coverage < LAT_DEG + 1.0,
        () -> "a due-east launch stays within its site latitude, got " + fmt(coverage, 3) + "°");
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Highest geodetic latitude the ground track reaches over one revolution, in degrees. Read from
   * the trace rather than from the inclination, so the assertion is about coverage and not about
   * the element that is supposed to produce it.
   */
  private static double maxGroundTrackLatitudeDeg(SpacecraftState state) {
    KeplerianPropagator propagator = new KeplerianPropagator(state.getOrbit());
    double period = state.getOrbit().getKeplerianPeriod();
    double maximum = 0.0;
    for (int sample = 0; sample <= SAMPLES; sample++) {
      SpacecraftState sampled = propagator.propagate(state.getDate().shiftedBy(period * sample / SAMPLES));
      GeodeticPoint subSatellite =
          OrekitService.get()
              .getEarthEllipsoid()
              .transform(sampled.getPosition(), sampled.getFrame(), sampled.getDate());
      maximum = FastMath.max(maximum, FastMath.abs(FastMath.toDegrees(subSatellite.getLatitude())));
    }
    return maximum;
  }

  private static EarthOrbitMission mission(LaunchPlane plane) {
    return new EarthOrbitMission(
        "T5 polar coverage",
        LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY),
        TARGET_ALT,
        TARGET_ALT,
        plane,
        LAT_DEG,
        -52.77,
        0.0);
  }

  /** Flies the vertical ascent and the three gravity-turn phases at fixed variables. */
  private static SpacecraftState flyAscent(LaunchPlane plane) {
    EarthOrbitMission mission = mission(plane);
    mission.setCurrentState(mission.getInitialState(epoch()));
    MissionStage verticalAscent = mission.getStages().getFirst();
    SpacecraftState entry = verticalAscent.propagateStandalone(mission.getCurrentState(), mission);

    AscentProfile profile = Launchers.FALCON_HEAVY.ascentProfile();
    List<MissionStage> ascent =
        AscentSequence.gravityTurn(
            profile, GravityTurnConstraints.forTarget(TARGET_ALT), plane, LAT_DEG);
    GravityTurnManeuver reference =
        new GravityTurnManeuver(
            mission.getVehicle(),
            entry.getMass(),
            FastMath.toRadians(profile.pitchKickAngleDeg()),
            plane.launchAzimuth(LAT_RAD),
            profile.interstageCoastDuration(),
            plane.commands(LAT_RAD));
    double[] variables = {reference.getStagingCompleteTime() + BURN2_SECONDS, TURN_EXPONENT};
    ((GravityTurnFirstBurnStage) ascent.getFirst())
        .applyOptimization(new OptimizationResult(variables, 0.0, entry, 1, entry));

    mission.setCurrentState(entry);
    return StageChainRunner.plain().run(ascent, entry, mission);
  }

  /** Runs the very stage {@code EarthOrbitMission} inserts for a commanded plane. */
  private static SpacecraftState trimPlane(SpacecraftState state, LaunchPlane plane) {
    Mission mission = mission(plane);
    mission.setCurrentState(state);
    return new AnalyticPlaneTrimAtNodeStage("Plane trim", plane.targetInclination())
        .propagateStandalone(state, mission);
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }
}
