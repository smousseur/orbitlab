package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticPlaneTrimAtNodeStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnFirstBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * <b>PHY-4 / L1 — the central-body refactor gate</b> (spec {@code
 * docs/multi-corps/03-conception-L1.md} §5).
 *
 * <p><b>What it guards.</b> L1 turns the central body from a constant read at the bottom of a
 * factory into a datum carried by the stage, across twenty propagator construction sites. It is a
 * pure refactor: not one number may move. This fixture pins the state at <em>every</em> stage
 * boundary of four profiles, so that a drift is localised to a stage rather than merely detected.
 *
 * <p><b>Why pinned literals and not an A/B comparison.</b> {@code EarthOrbitNonRegressionTest}
 * compares two code paths inside one run. That form does not transpose here: after the refactor the
 * Earth-hardcoded path no longer exists, so there is no B for the A. This gate must compare across
 * a commit, which means pinning values.
 *
 * <p><b>The pass this flies.</b> {@link StageChainRunner#sampling} with a null sampler and a zero
 * trailing coast — {@link StageChainRunner#plain()} takes no listener and therefore cannot record
 * stage boundaries. The two differ only in {@code abortOnFailure}, so on a nominal profile they fly
 * the very same trajectory. The mission is built fresh and flown once: these numbers are comparable
 * to themselves across commits, and to neither the optimize pass nor the ephemeris pass of the
 * baseline (see {@code 02-baseline-L0.md} §5.2, where those two provably disagree on MEO).
 *
 * <p><b>Fixed variables, never an optimizer output.</b> No CMA-ES runs here. Only the gravity turn
 * takes variables; the analytic stages plan themselves, deterministically within one pass.
 *
 * <p><b>The fixtures are copied, not shared.</b> {@code MeoMissionTest} and {@code PolarCoverageTest}
 * keep theirs {@code private}, and more importantly a gate must not rest on another test's fixture:
 * a change over there would move the reference over here with nobody seeing it.
 */
class CentralBodyBaselineTest {
  private static final Logger logger = LogManager.getLogger(CentralBodyBaselineTest.class);

  private static final double LAT = 5.23;
  private static final double LON = -52.77;
  private static final double ALT = 0.0;

  /** Second-burn length the polar fixture freezes, in seconds. */
  private static final double POLAR_BURN2_SECONDS = 250.0;

  /** Pitch exponent the polar fixture freezes. */
  private static final double POLAR_TURN_EXPONENT = 0.32;

  /** Galileo / GPS altitude, as the MEO fixture targets it. */
  private static final double MEO_ALTITUDE = 20_200_000.0;

  /** The constellation inclination the MEO fixture targets, in degrees. */
  private static final double MEO_INCLINATION_DEG = 55.0;

  private static final double MEO_PARKING_ALTITUDE = 400_000.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /** One stage boundary: what the gate pins. */
  record Boundary(
      String stage, double x, double y, double z, double vx, double vy, double vz, double mass) {}

  // ════════════════════════════════════════════════════════════════════════
  // The four profiles
  // ════════════════════════════════════════════════════════════════════════

  @Test
  void measureLeo400() {
    Mission mission = new EarthOrbitMission("Falcon Heavy", falconHeavyBaselineLoads(), 400_000.0);
    print("LEO-400", fly(mission, 307.193166, 0.127161));
  }

  @Test
  void measureGeo() {
    Mission mission = new GEOMission("GTO mission", 400_000.0, 35_786_000.0);
    print("GEO", fly(mission, 329.124209, 0.177424));
  }

  @Test
  void measureMeo() {
    Mission mission = MissionComposer.compose(meoSpec(), OptimizationType.FAST);
    print("MEO", fly(mission, 378.663107, 0.131995));
  }

  /**
   * The polar profile freezes a <em>second-burn duration</em>, not an absolute transition time: its
   * fixture writes {@code transitionTime = stagingCompleteTime + 250}. The staging time is read off
   * a reference maneuver rebuilt from the same inputs the first burn phase reads, at the mass the
   * vertical ascent actually leaves behind.
   *
   * <p><b>It flies the ascent and the plane trim, and nothing between them</b> — which is exactly
   * what {@code PolarCoverageTest} flies, and what {@code 02-baseline-L0.md} §4 records. This is not
   * a shortcut: the fixture is knowingly out of envelope (baseline §5.6, {@code bugs.md} BUG-6). Its
   * ascent ends on a suborbital arc whose perigee is −131 km, and {@code
   * AnalyticHohmannTransferStage} refuses to plan from it — "No apogee found within one transfer
   * half-period", thrown from {@code configure()}, which the runner does not catch. Putting the
   * fixture back in envelope would move the polar figures of a baseline five lots are about to rest
   * on, which §5.6 forbids for the duration of PHY-4.
   */
  @Test
  void measurePolar() {
    LaunchPlane polar = LaunchPlane.ofDegrees(90.0);
    Mission mission = MissionComposer.compose(polarSpec(polar), OptimizationType.FAST);
    print(
        "POLAR",
        fly(
            mission,
            entry ->
                maneuverOf(mission, entry, polar).getStagingCompleteTime() + POLAR_BURN2_SECONDS,
            POLAR_TURN_EXPONENT,
            CentralBodyBaselineTest::ascentThenPlaneTrim));
  }

  // ════════════════════════════════════════════════════════════════════════
  // The flight
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Flies the whole chain at fixed ascent variables and records every stage boundary.
   *
   * @param mission the mission to fly, freshly built
   * @param transitionTime the gravity turn's transition time (s), a fixed literal
   * @param exponent the pitch exponent, a fixed literal
   * @return the boundary state of every stage after the vertical ascent, in flight order
   */
  private static List<Boundary> fly(Mission mission, double transitionTime, double exponent) {
    return fly(
        mission,
        entry -> transitionTime,
        exponent,
        CentralBodyBaselineTest::everythingAfterTheVerticalAscent);
  }

  /**
   * Flies a chain at fixed ascent variables and records every stage boundary.
   *
   * <p>The transition time is a function of the gravity-turn entry state rather than a literal,
   * because the polar fixture expresses it as a second-burn duration added to a staging time the
   * entry mass determines. The three other profiles pass a constant function.
   *
   * <p>The chain is a function of the mission's stages for the same kind of reason: three profiles
   * fly everything past the vertical ascent, and the polar one flies what its own fixture flies —
   * see {@link #measurePolar()}.
   *
   * @param mission the mission to fly, freshly built
   * @param transitionTime the gravity turn's transition time (s), derived from the turn entry state
   * @param exponent the pitch exponent, a fixed literal
   * @param chain picks the stages to fly out of the mission's own list
   * @return the boundary state of every stage flown, in flight order
   */
  private static List<Boundary> fly(
      Mission mission,
      ToDoubleFunction<SpacecraftState> transitionTime,
      double exponent,
      UnaryOperator<List<MissionStage>> chain) {
    SpacecraftState initial = mission.getInitialState(epoch());
    mission.setCurrentState(initial);

    List<MissionStage> stages = mission.getStages();
    SpacecraftState entry = stages.getFirst().propagateStandalone(initial, mission);

    // Only the gravity turn takes variables. AnalyticHohmannTransferStage and
    // AnalyticParkingInsertionStage are optimizable too, but they store a plan they compute
    // themselves — injecting anything into them would be injecting an optimizer output.
    GravityTurnFirstBurnStage firstBurn = firstBurnOf(mission);
    double[] variables = {transitionTime.applyAsDouble(entry), exponent};
    firstBurn.applyOptimization(new OptimizationResult(variables, 0.0, entry, 1, entry));

    List<Boundary> recorded = new ArrayList<>();
    StageChainRunner runner =
        StageChainRunner.sampling(
            null,
            0.0,
            run ->
                recorded.add(
                    new Boundary(
                        run.stage().getName(),
                        run.finalState().getPosition().getX(),
                        run.finalState().getPosition().getY(),
                        run.finalState().getPosition().getZ(),
                        run.finalState().getPVCoordinates().getVelocity().getX(),
                        run.finalState().getPVCoordinates().getVelocity().getY(),
                        run.finalState().getPVCoordinates().getVelocity().getZ(),
                        run.finalState().getMass())));

    mission.setCurrentState(entry);
    runner.run(chain.apply(stages), entry, mission);
    return recorded;
  }

  /** The whole mission past the vertical ascent, which is what a nominal profile flies. */
  private static List<MissionStage> everythingAfterTheVerticalAscent(List<MissionStage> stages) {
    return stages.subList(1, stages.size());
  }

  /** The three gravity-turn phases and the plane trim, skipping the orbital phases between them. */
  private static List<MissionStage> ascentThenPlaneTrim(List<MissionStage> stages) {
    List<MissionStage> chain = new ArrayList<>(stages.subList(1, 4));
    for (MissionStage stage : stages) {
      if (stage instanceof AnalyticPlaneTrimAtNodeStage) {
        chain.add(stage);
        return List.copyOf(chain);
      }
    }
    throw new AssertionError("no plane trim in this mission");
  }

  private static GravityTurnFirstBurnStage firstBurnOf(Mission mission) {
    for (MissionStage stage : mission.getStages()) {
      if (stage instanceof GravityTurnFirstBurnStage firstBurn) {
        return firstBurn;
      }
    }
    throw new AssertionError("no gravity-turn first burn in this mission");
  }

  /** Rebuilds the maneuver the first burn phase would build, from the same inputs it reads. */
  private static GravityTurnManeuver maneuverOf(
      Mission mission, SpacecraftState entry, LaunchPlane plane) {
    AscentProfile profile = Launchers.FALCON_HEAVY.ascentProfile();
    double latitude = FastMath.toRadians(LAT);
    return new GravityTurnManeuver(
        mission.getVehicle(),
        entry.getMass(),
        FastMath.toRadians(profile.pitchKickAngleDeg()),
        plane.launchAzimuth(latitude),
        profile.interstageCoastDuration(),
        plane.commands(latitude));
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /**
   * Prints boundaries as ready-to-paste Java literals. Phase 1 only — deleted in Task 2.
   *
   * <p>The numbers go through {@link Double#toString}, <b>not</b> through a fixed number of decimals:
   * that is the shortest decimal which reads back as the very same {@code double}. A {@code %.9e}
   * literal is ten significant digits of a seventeen-digit value, so pinning it and then demanding
   * the zero tolerance of §5.5 would fail on the very run that produced it.
   */
  private static void print(String profile, List<Boundary> boundaries) {
    for (Boundary b : boundaries) {
      logger.info(
          String.format(
              Locale.ROOT,
              "[%s] new Boundary(\"%s\", %s, %s, %s, %s, %s, %s, %s),",
              profile,
              b.stage(),
              b.x(),
              b.y(),
              b.z(),
              b.vx(),
              b.vy(),
              b.vz(),
              b.mass()));
    }
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures — copied, never borrowed
  // ════════════════════════════════════════════════════════════════════════

  /**
   * The Falcon Heavy of the <b>LEO-400 baseline</b>: 600 t and 100 t of propellant, <em>not</em> the
   * tank capacities.
   *
   * <p>These are the loads {@code AscentBaselineN2Test.leo400Baseline} flies and the ones {@code
   * 02-baseline-L0.md} §3 records against. A fully loaded stack is a different vehicle: flown at the
   * baseline's fixed variables it re-enters during the second gravity-turn phase, which is how the
   * mismatch was caught. The polar profile below <em>does</em> want the fully loaded stack, because
   * that is what its own fixture flies — the two are deliberately different.
   */
  private static LaunchConfiguration falconHeavyBaselineLoads() {
    return new LaunchConfiguration(
        Launchers.FALCON_HEAVY, new double[] {600_000, 100_000}, Spacecraft.LEGACY);
  }

  /** The fully loaded Falcon Heavy the polar fixture flies. */
  private static LaunchConfiguration falconHeavyFullyLoaded() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  /** A copy of {@code MeoMissionTest}'s hand-built spec: Ariane 62 to 20 200 km at 55°. */
  private static MissionSpec.EarthOrbit meoSpec() {
    PayloadModel model = Payloads.GEO_SAT;
    double payloadDryMass = model.defaultDryMass();
    LaunchPlane plane = LaunchPlane.ofDegrees(MEO_INCLINATION_DEG, NodeBranch.ASCENDING);
    double azimuth = plane.launchAzimuth(FastMath.toRadians(LAT));

    PropellantBudget.GeoLoads loads =
        PropellantBudget.loadsForHighOrbit(
            Launchers.ARIANE_62,
            model,
            payloadDryMass,
            MEO_PARKING_ALTITUDE,
            MEO_ALTITUDE,
            LAT,
            0.0,
            azimuth);
    Spacecraft payload = model.toSpacecraft(payloadDryMass, loads.akmLoad());

    return new MissionSpec.EarthOrbit(
        "MEO Galileo",
        new LaunchConfiguration(Launchers.ARIANE_62, loads.launcherLoads(), payload, model.id()),
        MEO_ALTITUDE,
        MEO_ALTITUDE,
        plane.targetInclination(),
        plane.nodeBranch(),
        "Kourou",
        LAT,
        LON,
        0.0,
        null);
  }

  /** A copy of {@code PolarCoverageTest}'s mission: a fully loaded Falcon Heavy to 400 km at 90°. */
  private static MissionSpec.EarthOrbit polarSpec(LaunchPlane plane) {
    return new MissionSpec.EarthOrbit(
        "T5 polar coverage",
        falconHeavyFullyLoaded(),
        400_000.0,
        400_000.0,
        plane.targetInclination(),
        plane.nodeBranch(),
        "Kourou",
        LAT,
        LON,
        ALT,
        null);
  }
}
