package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticPlaneTrimAtNodeStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnFirstBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
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
import org.orekit.utils.Constants;

/**
 * <b>MIS-7 / P1, test T5</b> — a polar mission actually covers the poles (spec {@code
 * docs/earth-orbit/01-mission-terre-parametrable.md} §9.2).
 *
 * <p>Inclination is a number; coverage is what a polar orbit is <em>for</em>. This fixture reads
 * the ground track rather than the orbital element, so it fails on anything that would leave the
 * plane short of the poles — including the failure modes an inclination assertion cannot see.
 *
 * <p><b>It flies the whole chain {@code EarthOrbitMission} composes</b> — ascent, transfer, trim,
 * plane trim — and not the ascent alone. That is the correction of {@code docs/bugs.md} BUG-6:
 * {@link AnalyticPlaneTrimAtNodeStage} aims its target velocity purely transverse, so on an
 * eccentric orbit it flattens the flight path angle as well as rotating the plane, and pays for
 * both. Fired on the MECO arc, as this fixture used to fire it, it spent 1 028 m/s and 10 349 kg —
 * a figure read as the cost of a polar mission, which it never was. Fired where the mission
 * actually fires it, on the circularized orbit, it costs <b>10 m/s and 141 kg</b>.
 *
 * <p><b>Where the plane is actually closed, measured.</b> The ascent alone lands 3.41° short of the
 * polar command — the thrust stays in the target plane, so it never cancels the out-of-plane
 * entrainment ({@code AscentPlaneControlTest}) — which is coverage to 86.7°, not to the poles. The
 * transfer's apogee burn closes that to 0.064°, and the plane trim closes the 0.064°. So the stage
 * that makes this mission polar is the transfer, and the plane trim is the residual cleanup its own
 * javadoc describes, in the regime it is written for.
 *
 * <p><b>The last step is asserted on the plane and not on the ground track, and that is not a
 * weakening.</b> Inclination is commanded in GCRF; coverage is an Earth-fixed property. The two
 * poles are <b>0.145376° apart</b> at this epoch — precession accumulated since J2000, measured by
 * transforming the ITRF pole into GCRF — so an orbit whose inertial inclination is exactly 90°
 * misses the true pole by up to that much, and its ground track tops out at 89.89°, <em>below</em>
 * the 89.96° of the 89.9364° state the trim starts from. Driving the inertial inclination to 90°
 * therefore cannot be justified by a ground-track improvement; it is justified by the residual it
 * removes, which is what the third assertion measures. The ground track keeps the two claims it can
 * carry: that the mission reaches the poles at all, and that the ascent alone does not.
 *
 * <p><b>The ascent variables are frozen optimizer output, not a hand-picked pair.</b> They are what
 * CMA-ES returns for this mission at seed 42, inlined so the fixture stays deterministic and free
 * of an optimization run. The hand-picked pair used before — a second burn of 250 s at exponent
 * 0.32 — could not be kept: it leaves MECO on a suborbital arc whose perigee is −131 km, and {@code
 * AnalyticHohmannTransferStage} refuses to plan from it ("No apogee found within one transfer
 * half-period"). Putting the plane trim back in envelope therefore required moving the ascent too.
 */
class PolarCoverageTest {
  private static final Logger logger = LogManager.getLogger(PolarCoverageTest.class);

  private static final double LAT_DEG = 5.23;
  private static final double TARGET_ALT = 400_000.0;

  /** CMA-ES output for this mission at seed 42, frozen — see the class javadoc. */
  private static final double TRANSITION_TIME = 349.7121685971332;

  private static final double TURN_EXPONENT = 0.18590543817939678;

  /** Stages 1 to 3: the three gravity-turn phases, the vertical ascent being flown on its own. */
  private static final int END_OF_ASCENT = 4;

  /** The coverage a polar mission is bought for (spec §9.2). */
  private static final double MIN_GROUND_TRACK_LATITUDE_DEG = 89.0;

  /**
   * Ground-track samples over one revolution. Ten times the step a coverage assertion at 89° would
   * need: near the pole the sub-satellite latitude varies linearly rather than turning, so a coarse
   * step reads several hundredths of a degree low — the same order as the 0.145° pole offset the
   * class javadoc explains, and the two must not be confused in the log.
   */
  private static final int SAMPLES = 7200;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void aPolarMission_sweepsTheGroundTrackToThePoles() {
    EarthOrbitMission mission = mission(LaunchPlane.ofDegrees(90.0));
    List<MissionStage> stages = mission.getStages();
    int planeTrim = planeTrimIndex(stages);

    SpacecraftState afterAscent = flyAscent(mission);
    SpacecraftState afterTransfer =
        fly(mission, stages.subList(END_OF_ASCENT, planeTrim), afterAscent);
    SpacecraftState afterTrim =
        fly(mission, stages.subList(planeTrim, planeTrim + 1), afterTransfer);

    double coverageAfterAscent = maxGroundTrackLatitudeDeg(afterAscent);
    double coverageAfterTransfer = maxGroundTrackLatitudeDeg(afterTransfer);
    double coverageAfterTrim = maxGroundTrackLatitudeDeg(afterTrim);

    // The orbit and mass lines carry no assertion: they exist so this profile can be read as a
    // reference table. They go through OrbitElements.format() precisely so the line is
    // character-for-character comparable to the "Achieved orbit" line every other profile is read
    // from.
    logger.info("T5 polar coverage from Kourou (φ = {}°):", fmt(LAT_DEG, 2));
    logStep("after the ascent alone", afterAscent, coverageAfterAscent);
    logStep("after the transfer and trim", afterTransfer, coverageAfterTransfer);
    logStep("after the plane trim", afterTrim, coverageAfterTrim);
    double residualAfterTransfer = planeResidualDeg(afterTransfer);
    double residualAfterTrim = planeResidualDeg(afterTrim);
    logger.info(
        "  plane trim: {} kg of propellant, fired on an orbit of e = {}; residual {}° -> {}°",
        fmt(afterTransfer.getMass() - afterTrim.getMass(), 3),
        fmt(afterTransfer.getOrbit().getE(), 4),
        fmt(residualAfterTransfer, 4),
        fmt(residualAfterTrim, 4));

    assertTrue(
        coverageAfterTrim > MIN_GROUND_TRACK_LATITUDE_DEG,
        () ->
            "a polar mission must sweep past "
                + fmt(MIN_GROUND_TRACK_LATITUDE_DEG, 0)
                + "° of latitude; this one reaches "
                + fmt(coverageAfterTrim, 3)
                + "°");
    assertTrue(
        coverageAfterTransfer > coverageAfterAscent,
        "the ascent alone cannot reach the poles — the transfer's apogee burn is what closes it");
    assertTrue(
        residualAfterTrim < residualAfterTransfer,
        "the plane trim is in the chain to close the residual the transfer leaves — it must reduce"
            + " it");
  }

  /** Distance of the flown plane from the commanded one, in degrees of inclination. */
  private static double planeResidualDeg(SpacecraftState state) {
    return FastMath.abs(Physics.inclinationDeg(state) - 90.0);
  }

  /**
   * The other half of the statement: a due-east launch from Kourou covers a 5° band and nothing
   * more, which is exactly why a polar mission could not be flown before MIS-7. The ascent is
   * enough to say so — no orbital phase widens a band the plane does not have.
   */
  @Test
  void aDueEastMission_neverLeavesTheEquatorialBand() {
    double coverage = maxGroundTrackLatitudeDeg(flyAscent(mission(LaunchPlane.dueEast(LAT_DEG))));

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
      SpacecraftState sampled =
          propagator.propagate(state.getDate().shiftedBy(period * sample / SAMPLES));
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

  /** Flies the vertical ascent and the three gravity-turn phases at the frozen variables. */
  private static SpacecraftState flyAscent(EarthOrbitMission mission) {
    List<MissionStage> stages = mission.getStages();
    mission.setCurrentState(mission.getInitialState(epoch()));
    SpacecraftState entry =
        stages.getFirst().propagateStandalone(mission.getCurrentState(), mission);

    ((GravityTurnFirstBurnStage) stages.get(1))
        .applyOptimization(
            new OptimizationResult(
                new double[] {TRANSITION_TIME, TURN_EXPONENT}, 0.0, entry, 1, entry));

    return fly(mission, stages.subList(1, END_OF_ASCENT), entry);
  }

  /**
   * Flies a slice of the mission's own stage list. {@link StageChainRunner#plain()} returns the
   * entry state when a stage throws, so a caller would otherwise measure the same orbit twice and
   * read it as "the stage changed nothing" — hence the explicit check.
   */
  private static SpacecraftState fly(
      Mission mission, List<MissionStage> chain, SpacecraftState entry) {
    mission.setCurrentState(entry);
    SpacecraftState exit = StageChainRunner.plain().run(chain, entry, mission);
    assertTrue(
        exit.getDate().durationFrom(entry.getDate()) > 0.0,
        () -> "stage '" + chain.getFirst().getName() + "' did not fly — see the WARN above");
    return exit;
  }

  private static int planeTrimIndex(List<MissionStage> stages) {
    for (int index = 0; index < stages.size(); index++) {
      if (stages.get(index) instanceof AnalyticPlaneTrimAtNodeStage) {
        return index;
      }
    }
    throw new IllegalStateException("a commanded plane must compose a plane trim");
  }

  private static void logStep(String label, SpacecraftState state, double coverage) {
    logger.info(
        "  {} -> inclination {}°, ground track reaches {}°",
        label,
        fmt(Physics.inclinationDeg(state), 4),
        fmt(coverage, 3));
    logger.info(
        "      orbit {}, mass {} kg",
        OrbitElements.osculating(state.getOrbit(), Constants.WGS84_EARTH_EQUATORIAL_RADIUS)
            .format(),
        fmt(state.getMass(), 3));
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }
}
