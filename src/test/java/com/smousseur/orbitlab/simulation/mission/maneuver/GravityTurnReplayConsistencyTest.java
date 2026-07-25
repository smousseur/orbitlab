package com.smousseur.orbitlab.simulation.mission.maneuver;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.operation.GEOMission;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * Pins the proven root cause of the GEO optimize-vs-ephemeris divergence (bilan 11 §3.9): the
 * <b>vertical ascent flies under different gravity models in the two passes</b>, and the gravity
 * turn amplifies the resulting post-ascent difference into a downstream one that the GTO →
 * apogee-node → AKM chain blows up to ~5° of inclinaison.
 *
 * <p>{@code ConstantThrustStage.propagateStandalone} (which {@code VerticalAscentStage} extends)
 * builds a point-mass {@code createSimplePropagator}; the ephemeris generator flies every stage
 * under the 8×8 {@code createOptimizationPropagator}. Same 7 s burn, same mass consumed, but a
 * different post-ascent state — and the gravity turn's {@code getEntryState()} override never
 * reconciles it, because that default returns {@code null} and {@code GravityTurnStage} does not
 * override it. So the optimize GT starts from the point-mass post-ascent state, the ephemeris GT
 * from the 8×8 one.
 *
 * <p>The four fixtures separate the cause from the two dead ends chased earlier:
 *
 * <ul>
 *   <li>{@code verticalAscentGravityModelMismatch_*} — point-mass vs 8×8 vertical ascent differ
 *       (~0.4 m, 0.1 m/s), the seed;
 *   <li>{@code differentPostAscentStates_*} — the same maneuver + same config, fed those two
 *       entries, diverges (~25 m) — the cause is the entry, i.e. the VA model;
 *   <li>{@code samePostAscentState_configDifferenceIsHarmless} — the same entry with the two
 *       <em>propagator configs</em> (armQuiet+tracker vs arm+MECO+multiplexer), even <b>with
 *       staging</b>, agrees to the bit — the config is NOT the cause;
 *   <li>{@code gravityTurnStageConfigure_startsTheReplayFromTheKickedState} — the (correct but
 *       divergence-irrelevant) 2b pitch-kick fix.
 * </ul>
 *
 * <p>The reproduction needs the <em>real</em> regime (sub-orbital climbing entry, |v| ≈ 466 m/s,
 * with staging), so the fixtures fly the actual Falcon Heavy vertical ascent rather than a
 * synthetic state.
 */
class GravityTurnReplayConsistencyTest {
  private static final Logger logger = LogManager.getLogger(GravityTurnReplayConsistencyTest.class);

  // Falcon Heavy GEO profile, mirroring PropellantLoadOptimizerIntegrationTest.
  private static final double PARKING_ALT = 400_000.0;
  private static final double GEO_ALT = 35_786_000.0;
  private static final double LAT = 5.23;
  private static final double LON = -52.77;
  private static final double PITCH_KICK_DEG = 3.0; // FALCON_HEAVY AscentProfile
  private static final double INTERSTAGE_COAST = 2.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /** Real Falcon Heavy GEO mission, fully assembled from the catalogs. */
  private static GEOMission geoMission() {
    double payloadDryMass = Payloads.GEO_SAT.defaultDryMass();
    PropellantBudget.GeoLoads geoLoads =
        PropellantBudget.loadsForGeo(
            Launchers.FALCON_HEAVY, Payloads.GEO_SAT, payloadDryMass, PARKING_ALT, LAT);
    return new GEOMission(
        "GT replay test",
        new LaunchConfiguration(
            Launchers.FALCON_HEAVY,
            geoLoads.launcherLoads(),
            Payloads.GEO_SAT.toSpacecraft(payloadDryMass, geoLoads.akmLoad())),
        PARKING_ALT,
        GEO_ALT,
        LAT,
        LON,
        0.0,
        0.0);
  }

  /**
   * The state at gravity-turn entry: the real post-vertical-ascent state, obtained by flying the
   * mission's own first stage. This is the sub-orbital climbing regime (|v| ≈ 470 m/s) where the
   * config difference bites — not the orbital circular fixtures that hid it.
   */
  private record GtSetup(GravityTurnManeuver maneuver, SpacecraftState entry) {}

  private static GtSetup gravityTurnAtEntry() {
    GEOMission mission = geoMission();
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    mission.setCurrentState(mission.getInitialState(epoch));

    MissionStage verticalAscent = mission.getStages().getFirst();
    SpacecraftState postVa = verticalAscent.propagateStandalone(mission.getCurrentState(), mission);

    // The maneuver the GEO gravity-turn stage builds internally (launch latitude / target
    // inclination are 0 on that stage, as in GEOMission.buildStages).
    double azimuth = Physics.getLaunchAzimuth(0.0, 0.0);
    GravityTurnManeuver maneuver =
        new GravityTurnManeuver(
            mission.getVehicle(),
            postVa.getMass(),
            Math.toRadians(PITCH_KICK_DEG),
            azimuth,
            INTERSTAGE_COAST);
    return new GtSetup(maneuver, postVa);
  }

  /**
   * Replays the GT exactly as {@code MissionEphemerisGenerator} drives it, from the given start.
   */
  private static SpacecraftState replayLikeGenerator(
      GravityTurnManeuver maneuver, SpacecraftState start, double[] variables) {
    GravityTurnManeuver.GravityTurnParams params = maneuver.decode(variables);
    NumericalPropagator prop =
        OrekitService.get().createOptimizationPropagator(maneuver.maxStepSeconds());
    prop.setInitialState(start);
    maneuver.configure(
        prop, start, params); // single source of truth, shared with the optimize path
    DepletionGuard.arm(prop, maneuver.getDepletionFloor(), "GT"); // loud, as GravityTurnStage does
    AbsoluteDate meco = start.getDate().shiftedBy(params.transitionTime());
    prop.addEventDetector(new DateDetector(meco).withHandler((s, d, inc) -> Action.STOP));
    prop.getMultiplexer().add(1.0, state -> {}); // fixed-step sampler, as the generator adds
    return prop.propagate(meco);
  }

  /** Post-vertical-ascent state, flown under the two gravity models the two passes actually use. */
  private record PostVa(
      SpacecraftState pointMass, SpacecraftState eightByEight, double burn1Mass) {}

  private static PostVa postVerticalAscentBothModels() {
    GEOMission mission = geoMission();
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    SpacecraftState initial = mission.getInitialState(epoch);
    MissionStage va = mission.getStages().getFirst();

    // Optimize pass: propagateStandalone → createSimplePropagator (point-mass).
    mission.setCurrentState(initial);
    SpacecraftState pointMass = va.propagateStandalone(initial, mission);

    // Ephemeris pass: the generator flies every stage under createOptimizationPropagator (8×8).
    mission.setCurrentState(initial);
    NumericalPropagator p8 =
        OrekitService.get().createOptimizationPropagator(va.maxStepSeconds(initial, mission));
    p8.setInitialState(initial);
    va.configure(p8, mission);
    AbsoluteDate end =
        va.getConfiguredEndDate() != null
            ? va.getConfiguredEndDate()
            : initial.getDate().shiftedBy(7.0);
    SpacecraftState eightByEight = p8.propagate(end);
    return new PostVa(pointMass, eightByEight, pointMass.getMass());
  }

  @Test
  void verticalAscentGravityModelMismatch_movesThePostAscentState() {
    PostVa va = postVerticalAscentBothModels();
    double dPos = Vector3D.distance(va.pointMass().getPosition(), va.eightByEight().getPosition());
    double dVel =
        Vector3D.distance(
            va.pointMass().getPVCoordinates().getVelocity(),
            va.eightByEight().getPVCoordinates().getVelocity());
    logger.info(
        "Vertical Ascent point-mass vs 8×8 post-state: Δpos={} m, Δvel={} m/s, |v|≈{} m/s",
        String.format(java.util.Locale.ROOT, "%.3f", dPos),
        String.format(java.util.Locale.ROOT, "%.5f", dVel),
        String.format(
            java.util.Locale.ROOT,
            "%.1f",
            va.pointMass().getPVCoordinates().getVelocity().getNorm()));
    // The root cause (bilan 11 §3.9): the optimize pass flies the vertical ascent point-mass, the
    // ephemeris generator 8×8. Same 7 s burn, same mass consumed — but the state differs, and the
    // gravity-turn override never reconciles it (GravityTurnStage.getEntryState() is null).
    assertEquals(va.pointMass().getMass(), va.eightByEight().getMass(), 1.0, "same mass consumed");
    assertTrue(
        dPos > 1.0e-3 || dVel > 1.0e-6,
        () -> "the two VA models must produce different post-ascent states; Δpos=" + dPos);
  }

  @Test
  void differentPostAscentStates_makeTheGravityTurnExitDiverge_sameConfig() {
    PostVa va = postVerticalAscentBothModels();
    double azimuth = Physics.getLaunchAzimuth(0.0, 0.0);
    GravityTurnManeuver maneuver =
        new GravityTurnManeuver(
            geoMission().getVehicle(),
            va.burn1Mass(),
            Math.toRadians(PITCH_KICK_DEG),
            azimuth,
            INTERSTAGE_COAST);
    double[] variables = {maneuver.getStagingCompleteTime() + 2.0, 0.32};

    // Same config (both optimize) — so the ONLY difference is the entry state, which the VA model
    // mismatch produced. This isolates the cause to the entry, not the propagator config.
    SpacecraftState fromPointMass = maneuver.propagateForOptimization(va.pointMass(), variables);
    SpacecraftState fromEightByEight =
        maneuver.propagateForOptimization(va.eightByEight(), variables);
    assertTrue(fromPointMass.getMass() < va.burn1Mass() - 1.0, "GT must fly (mass consumed)");

    double dPos = Vector3D.distance(fromPointMass.getPosition(), fromEightByEight.getPosition());
    double dVel =
        Vector3D.distance(
            fromPointMass.getPVCoordinates().getVelocity(),
            fromEightByEight.getPVCoordinates().getVelocity());
    logger.info(
        "GT exit from point-mass-VA vs 8×8-VA entry (same config): Δpos={} m, Δvel={} m/s",
        String.format(java.util.Locale.ROOT, "%.1f", dPos),
        String.format(java.util.Locale.ROOT, "%.4f", dVel));
    assertTrue(
        dPos > 1.0,
        () -> "the VA-model entry difference must move the GT exit by a real margin; Δpos=" + dPos);
  }

  @Test
  void samePostAscentState_configDifferenceIsHarmless() {
    GtSetup gt = gravityTurnAtEntry();
    double[] variables = {gt.maneuver().getStagingCompleteTime() + 2.0, 0.32};

    // Same entry, staging happens — vary only the propagator config (optimize vs generator). It
    // agrees to the bit, ruling out the config (and detectors, and multiplexer) as the cause.
    SpacecraftState optimize = gt.maneuver().propagateForOptimization(gt.entry(), variables);
    SpacecraftState generator =
        replayLikeGenerator(gt.maneuver(), gt.maneuver().applyKick(gt.entry()), variables);

    double dPos = Vector3D.distance(optimize.getPosition(), generator.getPosition());
    double dVel =
        Vector3D.distance(
            optimize.getPVCoordinates().getVelocity(), generator.getPVCoordinates().getVelocity());
    logger.info(
        "GT config residual, same entry WITH staging: Δpos={} m, Δvel={} m/s",
        String.format(java.util.Locale.ROOT, "%.2e", dPos),
        String.format(java.util.Locale.ROOT, "%.2e", dVel));
    assertEquals(0.0, dPos, 1.0e-3, "same entry: the two configs agree (config is not the cause)");
    assertEquals(0.0, dVel, 1.0e-6, "same entry: the two configs agree on velocity");
  }

  /**
   * The 2b fix (bilan 11 §3.9): {@code GravityTurnStage.configure} — the ephemeris replay path —
   * applies the pitch kick and resets the propagator's initial state to the kicked one (the
   * generator sets it to the pre-kick saved entry before calling configure). Correct in itself,
   * though it does not resolve the divergence (the kick washes out; the config difference above is
   * the real cause).
   */
  @Test
  void gravityTurnStageConfigure_startsTheReplayFromTheKickedState() {
    GtSetup gt = gravityTurnAtEntry();
    GravityTurnStage stage =
        new GravityTurnStage(
            "Gravity turn",
            PITCH_KICK_DEG,
            INTERSTAGE_COAST,
            GravityTurnConstraints.forTarget(PARKING_ALT));
    double[] variables = {gt.maneuver().getStagingCompleteTime() + 2.0, 0.32};
    SpacecraftState preKick = gt.entry();
    stage.applyOptimization(new OptimizationResult(variables, 0.0, preKick, 1, preKick));

    Mission mission = geoMission();
    mission.setCurrentState(preKick); // the generator sets this to the pre-kick opt.getEntryState()

    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(stage.maxStepSeconds(preKick, mission));
    propagator.setInitialState(preKick); // exactly what the generator does before configure
    stage.configure(propagator, mission);

    Vector3D flownVel = propagator.getInitialState().getPVCoordinates().getVelocity();
    Vector3D kickedVel = gt.maneuver().applyKick(preKick).getPVCoordinates().getVelocity();
    Vector3D preKickVel = preKick.getPVCoordinates().getVelocity();
    assertEquals(
        0.0,
        Vector3D.distance(flownVel, kickedVel),
        1.0e-9,
        "configure must reset the propagator to the kicked entry velocity");
    assertTrue(
        Vector3D.distance(flownVel, preKickVel) > 1.0e-3,
        "configure must NOT leave the replay on the pre-kick velocity the generator supplied");
  }
}
