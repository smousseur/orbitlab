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
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

/**
 * Guards the optimize-vs-ephemeris consistency of the ascent, and holds the étape 0 numeric
 * reference of the explicit-staging migration.
 *
 * <p><b>History (bilan 11 §3.9), now closed.</b> The GEO optimize-vs-ephemeris divergence — ~5° of
 * inclinaison once the GTO → apogee-node → AKM chain had amplified it — was traced to the
 * <b>vertical ascent flying under two different gravity models</b>: {@code
 * ConstantThrustStage.propagateStandalone} (which {@code VerticalAscentStage} extends) built a
 * point-mass {@code createSimplePropagator}, while the ephemeris generator flew every stage under
 * the 8×8 {@code createOptimizationPropagator}. Same 7 s burn, same mass consumed, but a
 * post-ascent state differing by ~0.4 m / 0.1 m/s — which the gravity turn's {@code
 * getEntryState()} never reconciled. {@code propagateStandalone} now builds the 8×8 too, so the two
 * passes coincide; the fixtures below hold that closure rather than the former mismatch.
 *
 * <ul>
 *   <li>{@code verticalAscent_fliesTheSameGravityModelInBothPasses} — the fix: the two passes
 *       produce the same post-ascent state, to the bit. Red again if a point-mass propagator
 *       reappears in the optimize path;
 *   <li>{@code postAscentEntryDifference_isAmplifiedByTheGravityTurn} — why that matters: an entry
 *       difference of the historical magnitude, injected deliberately, still moves the gravity-turn
 *       exit by a real margin. The gravity turn amplifies its entry, so the entry must be shared;
 *   <li>{@code samePostAscentState_configDifferenceIsHarmless} — the dead end that was ruled out:
 *       the same entry under the two <em>propagator configs</em> (armQuiet+tracker vs
 *       arm+MECO+multiplexer), even <b>with staging</b>, agrees to the bit;
 *   <li>{@code gravityTurnStageConfigure_startsTheReplayFromTheKickedState} — the (correct but
 *       divergence-irrelevant) 2b pitch-kick fix;
 *   <li>{@code gravityTurnExit_matchesTheRecordedPreSplitBaseline} — étape 0 of the
 *       explicit-staging migration: the pre-split reference the three-phase ascent must reproduce.
 * </ul>
 *
 * <p>The regime matters (sub-orbital climbing entry, |v| ≈ 466 m/s, with staging), so the fixtures
 * fly the actual Falcon Heavy vertical ascent rather than a synthetic state.
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

  // ── Étape 0 baseline of the explicit-staging migration (spec 01 §7.1) ─────
  // Recorded on the pre-split code (commit 1d53e83), fixed variables, no optimizer involved.
  private static final double REF_BURN1_DURATION_S = 149.979660;
  private static final double REF_STAGING_COMPLETE_S = 151.979660;
  private static final double REF_EXIT_DT_S = 153.979660;
  private static final double REF_EXIT_MASS_KG = 17360.267;
  private static final Vector3D REF_EXIT_POSITION =
      new Vector3D(-3949439.705160, -5013991.353815, 589230.976026);
  private static final Vector3D REF_EXIT_VELOCITY =
      new Vector3D(6221.798395, -5155.808070, -48.249486);

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

  /**
   * Post-vertical-ascent state as each pass computes it. Both fly 8×8 today; the field names say
   * which pass, not which gravity model, precisely because the model is no longer what differs.
   */
  private record PostVa(
      SpacecraftState optimizePass, SpacecraftState ephemerisPass, double burn1Mass) {}

  private static PostVa postVerticalAscentBothPasses() {
    GEOMission mission = geoMission();
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    SpacecraftState initial = mission.getInitialState(epoch);
    MissionStage va = mission.getStages().getFirst();

    // Optimize pass: propagateStandalone, which builds its own propagator (8×8 since the fix).
    mission.setCurrentState(initial);
    SpacecraftState optimizePass = va.propagateStandalone(initial, mission);

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
    SpacecraftState ephemerisPass = p8.propagate(end);
    return new PostVa(optimizePass, ephemerisPass, optimizePass.getMass());
  }

  /** The given state, displaced by {@code dPos} along the radius and {@code dVel} along v. */
  private static SpacecraftState displaced(SpacecraftState state, double dPos, double dVel) {
    Vector3D position = state.getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    PVCoordinates pv =
        new PVCoordinates(
            position.add(new Vector3D(dPos, position.normalize())),
            velocity.add(new Vector3D(dVel, velocity.normalize())));
    CartesianOrbit orbit =
        new CartesianOrbit(pv, state.getFrame(), state.getDate(), state.getOrbit().getMu());
    return new SpacecraftState(orbit).withMass(state.getMass());
  }

  /**
   * The closure of bilan 11 §3.9: the vertical ascent is flown under the <b>same</b> gravity model
   * by both passes, so the gravity turn starts from the same state either way.
   *
   * <p>This fixture used to assert the opposite — that the optimize pass (point-mass {@code
   * createSimplePropagator}) and the ephemeris pass (8×8) produced post-ascent states ~0.4 m /
   * 0.1 m/s apart, the seed of the ~5° GEO inclinaison divergence. {@code
   * ConstantThrustStage.propagateStandalone} now builds the 8×8 propagator, the difference is
   * exactly zero, and this fixture holds it there: it turns red the day a point-mass propagator
   * reappears on the optimize path — which is how the divergence would come back.
   */
  @Test
  void verticalAscent_fliesTheSameGravityModelInBothPasses() {
    PostVa va = postVerticalAscentBothPasses();
    double dPos =
        Vector3D.distance(va.optimizePass().getPosition(), va.ephemerisPass().getPosition());
    double dVel =
        Vector3D.distance(
            va.optimizePass().getPVCoordinates().getVelocity(),
            va.ephemerisPass().getPVCoordinates().getVelocity());
    logger.info(
        "Vertical Ascent optimize vs ephemeris post-state: Δpos={} m, Δvel={} m/s, |v|≈{} m/s",
        String.format(java.util.Locale.ROOT, "%.2e", dPos),
        String.format(java.util.Locale.ROOT, "%.2e", dVel),
        String.format(
            java.util.Locale.ROOT,
            "%.1f",
            va.optimizePass().getPVCoordinates().getVelocity().getNorm()));
    assertEquals(
        va.optimizePass().getMass(), va.ephemerisPass().getMass(), 1.0, "same mass consumed");
    assertEquals(0.0, dPos, 1.0e-6, "the two passes must fly the same vertical ascent (position)");
    assertEquals(0.0, dVel, 1.0e-9, "the two passes must fly the same vertical ascent (velocity)");
  }

  /**
   * Why the shared entry matters: the gravity turn <b>amplifies</b> whatever difference it is
   * handed. Feeding it two entries a mere 0.4 m / 0.1 m/s apart — the magnitude the VA
   * gravity-model mismatch used to produce — moves its exit by tens of meters, which the GTO →
   * apogee-node → AKM chain then blew up to ~5° of inclinaison (bilan 11 §3.9).
   *
   * <p>The difference is injected deliberately here, since the ascent no longer produces one on its
   * own. That keeps the sensitivity documented — and it is what makes the N2 tolerances of the
   * explicit-staging migration (spec 01 §7.1) meaningful: 10 m at MECO is not slack, it is the
   * budget for an entry difference three orders of magnitude smaller.
   */
  @Test
  void postAscentEntryDifference_isAmplifiedByTheGravityTurn() {
    PostVa va = postVerticalAscentBothPasses();
    SpacecraftState entry = va.optimizePass();
    // The historical VA mismatch magnitude (bilan 11 §3.9), reproduced synthetically.
    SpacecraftState nudgedEntry = displaced(entry, 0.4, 0.1);

    double azimuth = Physics.getLaunchAzimuth(0.0, 0.0);
    GravityTurnManeuver maneuver =
        new GravityTurnManeuver(
            geoMission().getVehicle(),
            va.burn1Mass(),
            Math.toRadians(PITCH_KICK_DEG),
            azimuth,
            INTERSTAGE_COAST);
    double[] variables = {maneuver.getStagingCompleteTime() + 2.0, 0.32};

    // Same maneuver, same config, same variables: the ONLY difference is the entry state.
    SpacecraftState fromEntry = maneuver.propagateForOptimization(entry, variables);
    SpacecraftState fromNudgedEntry = maneuver.propagateForOptimization(nudgedEntry, variables);
    assertTrue(fromEntry.getMass() < va.burn1Mass() - 1.0, "GT must fly (mass consumed)");

    double dPos = Vector3D.distance(fromEntry.getPosition(), fromNudgedEntry.getPosition());
    double dVel =
        Vector3D.distance(
            fromEntry.getPVCoordinates().getVelocity(),
            fromNudgedEntry.getPVCoordinates().getVelocity());
    logger.info(
        "GT exit from a 0.4 m / 0.1 m/s entry difference: Δpos={} m, Δvel={} m/s",
        String.format(java.util.Locale.ROOT, "%.1f", dPos),
        String.format(java.util.Locale.ROOT, "%.4f", dVel));
    assertTrue(
        dPos > 1.0,
        () -> "the gravity turn must amplify an entry difference of that size; Δpos=" + dPos);
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
   * Étape 0 of the explicit-staging migration (spec {@code
   * specs/mission-stages/01-separations-implicites.md} §7.1/§8): the numeric reference of the
   * gravity turn at <b>fixed variables</b>, so the split into {@code Gravity turn (S1) → S1
   * separation → Gravity turn (S2)} can be checked without running CMA-ES.
   *
   * <p>Three quantities are pinned, and they are exactly the ones the split must reproduce:
   *
   * <ul>
   *   <li>{@code burn1Duration} and {@code stagingCompleteTime} — the date arithmetic the split
   *       phases inherit (spec §4.3: jettison at {@code kick + 1e-3 + burn1 + 1e-3}, second
   *       ignition at {@code jettison + interstageCoast + 1e-3});
   *   <li>the state at gravity-turn exit (MECO) — date, mass, position, velocity, at the N2
   *       tolerances of §7.1 (1 ms / 1 kg / 10 m / 0.05 m/s).
   * </ul>
   *
   * <p>Recorded on the pre-split code, seed-free (no optimizer involved), Falcon Heavy GEO profile.
   * After the split this test must fly the three-phase chain from the same entry and land here.
   */
  @Test
  void gravityTurnExit_matchesTheRecordedPreSplitBaseline() {
    GtSetup gt = gravityTurnAtEntry();
    double transitionTime = gt.maneuver().getStagingCompleteTime() + 2.0;
    double[] variables = {transitionTime, 0.32};

    SpacecraftState exit = gt.maneuver().propagateForOptimization(gt.entry(), variables);
    double dt = exit.getDate().durationFrom(gt.entry().getDate());
    Vector3D position = exit.getPosition();
    Vector3D velocity = exit.getPVCoordinates().getVelocity();

    logger.info(
        "Ascent baseline (fixed variables): burn1={} s, stagingComplete={} s, transition={} s",
        String.format(java.util.Locale.ROOT, "%.6f", gt.maneuver().getBurn1Duration()),
        String.format(java.util.Locale.ROOT, "%.6f", gt.maneuver().getStagingCompleteTime()),
        String.format(java.util.Locale.ROOT, "%.6f", transitionTime));
    logger.info(
        "Ascent baseline GT exit: t+{} s, mass={} kg, pos=({}), vel=({})",
        String.format(java.util.Locale.ROOT, "%.6f", dt),
        String.format(java.util.Locale.ROOT, "%.3f", exit.getMass()),
        String.format(
            java.util.Locale.ROOT,
            "%.6f, %.6f, %.6f",
            position.getX(),
            position.getY(),
            position.getZ()),
        String.format(
            java.util.Locale.ROOT,
            "%.6f, %.6f, %.6f",
            velocity.getX(),
            velocity.getY(),
            velocity.getZ()));

    assertEquals(REF_BURN1_DURATION_S, gt.maneuver().getBurn1Duration(), 1.0e-3, "burn 1 duration");
    assertEquals(
        REF_STAGING_COMPLETE_S, gt.maneuver().getStagingCompleteTime(), 1.0e-3, "staging complete");
    assertEquals(REF_EXIT_DT_S, dt, 1.0e-3, "GT exit date");
    assertEquals(REF_EXIT_MASS_KG, exit.getMass(), 1.0, "GT exit mass");
    assertEquals(
        0.0, Vector3D.distance(REF_EXIT_POSITION, position), 10.0, "GT exit position (10 m)");
    assertEquals(
        0.0, Vector3D.distance(REF_EXIT_VELOCITY, velocity), 0.05, "GT exit velocity (0.05 m/s)");
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
