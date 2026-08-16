package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;
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
  // The pinned boundaries — measured on be7a320 + this gate, 2026-08-16
  // ════════════════════════════════════════════════════════════════════════
  //
  // Cross-checked against 02-baseline-L0.md §3, which is the control §5.3 of the spec asks for:
  // the LEO-400 MECO sits 0.098 m and the GEO MECO 1.06 m from the figures recorded there, which
  // is the truncation of the six-decimal transitionTime being re-flown and nothing else. The polar
  // masses land on §4 exactly — 39 787.241 kg after the ascent, 29 438.109 kg after the trim.

  private static final List<Boundary> LEO_400 =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              -4161034.9606876844,
              -4836957.645658843,
              590773.6731263066,
              2829.2607217724676,
              -3458.967438127062,
              54.124323290628595,
              170150.00000000003),
          new Boundary(
              "S1 separation",
              -4155363.820830963,
              -4843860.898629096,
              590880.1233619973,
              2841.8747467619964,
              -3444.283813047835,
              52.32607603022072,
              104150.0),
          new Boundary(
              "Gravity turn (S2)",
              -3065580.756105344,
              -5677159.277736631,
              577919.1129824509,
              6963.0515855951,
              -3780.275434104066,
              -187.4060930376476,
              36368.08203548459),
          new Boundary(
              "Transfert",
              3168936.400792205,
              5961285.082526461,
              -603008.6377632977,
              -6761.441907162198,
              3613.1489887745934,
              186.66828509878627,
              35369.63057846537),
          new Boundary(
              "Trim",
              3129196.3682721797,
              5982644.292413696,
              -600467.5597814192,
              -6789.696463594801,
              3570.901488014221,
              195.0811207201827,
              35306.788175214075),
          new Boundary(
              "Coasting",
              -6650440.181998898,
              1262824.364510979,
              346567.7896302069,
              -1397.544179285111,
              -7522.756152872497,
              589.336816348514,
              35306.788175214075));

  private static final List<Boundary> GEO =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              -3990331.7912686206,
              -4958119.857950621,
              588076.4351483264,
              4504.610783911383,
              -4008.6745458054506,
              -16.167527453699485,
              181500.00000000006),
          new Boundary(
              "S1 separation",
              -3981310.378170853,
              -4966122.0390679445,
              588042.2960715811,
              4516.797160451363,
              -3993.503187457715,
              -17.971448853860903,
              115500.0),
          new Boundary(
              "Gravity turn (S2)",
              -2971528.72158108,
              -5650536.517216826,
              569924.046261095,
              7056.0501252158965,
              -3730.6083302299016,
              -197.7687123269583,
              64579.867269962386),
          new Boundary(
              "Parking",
              3049387.2626123563,
              6000586.895582041,
              -597754.1923737081,
              -6847.031726449716,
              3498.656712279812,
              203.32624112120058,
              62362.56256610243),
          new Boundary(
              "Coasting parking",
              -4888593.466937612,
              4674822.352430858,
              9.094947017729282E-12,
              -5296.1521302287,
              -5522.254603714591,
              710.3410199952696,
              62362.56256610243),
          new Boundary(
              "GTO injection",
              5363171.335655637,
              -4159043.9513475136,
              -67915.37855673787,
              6496.609094744228,
              7625.961523348982,
              -928.2804138707179,
              30818.80579511038),
          new Boundary(
              "S2 separation",
              5376150.850214461,
              -4143781.4230961213,
              -69771.7639833136,
              6482.900904128504,
              7636.5594898672025,
              -928.1042330659104,
              4000.0),
          new Boundary(
              "Circularization",
              -3.828866276610513E7,
              1.6334616275741128E7,
              52816.01938498162,
              -1005.8825168545008,
              -2765.5324237421733,
              -1.7609573410251111,
              2614.5271678302447),
          new Boundary(
              "Trim",
              -2.8587485781660516E7,
              3.0999543080187935E7,
              57789.760286321194,
              -2260.033980619004,
              -2084.2841847009554,
              -0.16972274969871384,
              2473.698472986933),
          new Boundary(
              "Plane trim",
              -3.2093155824133564E7,
              -2.7347087468971446E7,
              -0.012361423339541489,
              1994.2257198823486,
              -2340.3223268548413,
              -2.021527985007765E-7,
              2470.3698765048334),
          new Boundary(
              "Coasting",
              -1.4062589177404426E7,
              -3.9750315103151195E7,
              0.6907753783797899,
              2898.6763664681303,
              -1025.5079966092255,
              1.691760385748791E-4,
              2470.3698765048334));

  private static final List<Boundary> MEO =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              -4098925.1609306443,
              -4850403.573956941,
              778109.0943139206,
              2934.219598059122,
              -2350.3131155661185,
              4666.557620526424,
              63579.63568392503),
          new Boundary(
              "S1 separation",
              -4084176.0862850533,
              -4862062.682046478,
              801426.8647898303,
              2965.3859049671037,
              -2313.322008299819,
              4660.521967437738,
              27579.63568392501),
          new Boundary(
              "Gravity turn (S2)",
              -3036572.823018373,
              -5272633.81560809,
              2106204.153357493,
              5467.410169764329,
              -858.7645657697162,
              5732.4922537815855,
              17479.876833959104),
          new Boundary(
              "Parking",
              3162581.3910491304,
              5541087.882508149,
              -2262386.3036700296,
              -5303.850686703881,
              777.6089488638333,
              -5497.250197444033,
              17096.508695948487),
          new Boundary(
              "Coasting parking",
              -4861256.266797463,
              -4722125.557930212,
              0.002282458241097629,
              3271.355811467949,
              -3373.849936247002,
              6062.418740240285,
              17096.508695948487),
          new Boundary(
              "GTO injection",
              4387268.762007029,
              5126276.66957439,
              -824025.1829344148,
              -4764.39385061451,
              3592.871796769579,
              -7603.625718716541,
              10888.634298302268),
          new Boundary(
              "S2 separation",
              4363377.257129568,
              5144159.541947063,
              -862029.9894382439,
              -4792.176956486476,
              3560.265102120205,
              -7598.257679743834,
              3241.135805750434),
          new Boundary(
              "Circularization",
              -8077774.002682127,
              -2.085747850548936E7,
              1.3378327843698116E7,
              2993.81433779354,
              535.9553915025411,
              2424.0079247864933,
              2025.3465169413505),
          new Boundary(
              "Trim",
              -2.184029848144173E7,
              -1.121468609463065E7,
              -1.0176420215644725E7,
              64.4243434532862,
              -2655.4105966441653,
              2789.647240115543,
              1999.9999999999893),
          // The plane trim is a no-op here, to the last bit: the ascent already flew into the
          // target plane and the residual falls under the stage's own threshold, so it skips.
          // Baseline §5.2 records the same skip. It is pinned as it is — the day it stops
          // skipping is a fact this gate must state, not hide.
          new Boundary(
              "Plane trim",
              -2.184029848144173E7,
              -1.121468609463065E7,
              -1.0176420215644725E7,
              64.4243434532862,
              -2655.4105966441653,
              2789.647240115543,
              1999.9999999999893),
          new Boundary(
              "Coasting",
              -1.0471457254281698E7,
              -2.1355984620962016E7,
              1.1522959599503215E7,
              2803.9831547831855,
              111.19972997934396,
              2670.225624598103,
              1999.9999999999893));

  private static final List<Boundary> POLAR =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              -4237604.577084436,
              -4825708.974082522,
              813316.9491830231,
              -379.4219889459279,
              -1132.5178453548897,
              5028.679324490893,
              177650.00719250954),
          new Boundary(
              "S1 separation",
              -4238350.95635028,
              -4827959.813731344,
              823371.8980372399,
              -366.960227704707,
              -1118.3244783127097,
              5026.265230553439,
              111650.0),
          new Boundary(
              "Gravity turn (S2)",
              -4138653.747594576,
              -4888695.687681091,
              2386294.83898584,
              1385.9811073767362,
              888.8786253415311,
              7953.379096638929,
              39787.2413195339),
          new Boundary(
              "Plane trim",
              5920681.068147236,
              6699823.596242506,
              -111577.28891540543,
              -59.44375121101879,
              -67.24883572063003,
              -6202.174007220991,
              29438.108788232836));

  // ════════════════════════════════════════════════════════════════════════
  // The four profiles
  // ════════════════════════════════════════════════════════════════════════

  @Test
  void leo400_hasNotMoved() {
    Mission mission = new EarthOrbitMission("Falcon Heavy", falconHeavyBaselineLoads(), 400_000.0);
    assertPinned("LEO-400", fly(mission, 307.193166, 0.127161), LEO_400);
  }

  @Test
  void geo_hasNotMoved() {
    Mission mission = new GEOMission("GTO mission", 400_000.0, 35_786_000.0);
    assertPinned("GEO", fly(mission, 329.124209, 0.177424), GEO);
  }

  @Test
  void meo_hasNotMoved() {
    Mission mission = MissionComposer.compose(meoSpec(), OptimizationType.FAST);
    assertPinned("MEO", fly(mission, 378.663107, 0.131995), MEO);
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
  void polar_hasNotMoved() {
    LaunchPlane polar = LaunchPlane.ofDegrees(90.0);
    Mission mission = MissionComposer.compose(polarSpec(polar), OptimizationType.FAST);
    assertPinned(
        "POLAR",
        fly(
            mission,
            entry ->
                maneuverOf(mission, entry, polar).getStagingCompleteTime() + POLAR_BURN2_SECONDS,
            POLAR_TURN_EXPONENT,
            CentralBodyBaselineTest::ascentThenPlaneTrim),
        POLAR);
  }

  /**
   * Asserts every boundary against its pinned value at <b>zero tolerance</b>.
   *
   * <p>Strict equality is achievable and therefore required: the refactor keeps the same constant,
   * the same cached frame instances and the same shared 8×8 gravity model, so the floating-point
   * operations happen in the same order. Spec §5.5 — a site that cannot reach it gets a javadoc
   * naming the cause, never a delta.
   *
   * <p>Whole {@link Boundary} records are compared rather than field by field: the generated {@code
   * equals} is exact {@code double} equality, which is precisely what is wanted, and the message
   * names the offending stage.
   */
  private static void assertPinned(String profile, List<Boundary> actual, List<Boundary> expected) {
    assertEquals(
        expected.size(), actual.size(), () -> profile + ": the chain gained or lost a stage");
    for (int i = 0; i < expected.size(); i++) {
      int index = i;
      assertEquals(
          expected.get(i),
          actual.get(i),
          () -> profile + ": stage " + index + " '" + expected.get(index).stage() + "' moved");
    }
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
