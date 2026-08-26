package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
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
 * pure refactor: not one number may move. This fixture pins the state at each boundary of the
 * stages it flies, so that a drift is localised to a stage rather than merely detected.
 *
 * <p><b>What it pins, exactly.</b> Every stage of four profiles <em>except</em> the vertical
 * ascent, which is flown to produce the gravity-turn entry state and is therefore upstream of the
 * recording. The polar profile pins four of its eight stages, for the reason {@link
 * #polar_hasNotMoved()} gives. Nothing else is excluded.
 *
 * <p><b>Why pinned literals and not an A/B comparison.</b> {@code EarthOrbitNonRegressionTest}
 * compares two code paths inside one run. That form does not transpose here: after the refactor the
 * Earth-hardcoded path no longer exists, so there is no B for the A. This gate must compare across
 * a commit, which means pinning values.
 *
 * <h2>The two passes, and why both are needed</h2>
 *
 * <p>A stage builds propagators in <b>two different places</b>, and flying one does not exercise
 * the other. Pinning a single pass would leave half the lot unguarded, so each profile is flown
 * twice and pinned twice, in constants that never mix.
 *
 * <ul>
 *   <li><b>REPLAY</b> — {@link StageChainRunner#sampling} with a null sampler and a zero trailing
 *       coast. The runner builds one propagator per stage and calls {@code configure()} on it; a
 *       stage's own {@code propagateStandalone} is never reached. This is the ephemeris/replay
 *       pass. {@link StageChainRunner#plain()} would fly the same trajectory but takes no listener,
 *       so it cannot record boundaries at all.
 *   <li><b>STANDALONE</b> — the walk {@code MissionOptimizer} performs: inject the gravity turn's
 *       variables, then advance stage by stage through {@code MissionStage#propagateStandalone},
 *       which is where six of the twelve analytic construction sites live. {@code
 *       AnalyticParkingInsertionStage} is the clearest case — it has exactly one site, and it is in
 *       {@code propagateStandalone}, so the replay pass alone would let L1 rewrite it unguarded.
 *       This is the optimization pass, minus CMA-ES.
 * </ul>
 *
 * <p><b>The two passes do not agree, and must not be conflated</b> — which is precisely why spec
 * §5.4 demands that each profile declare its pass. Baseline §5.2 records the divergence on MEO;
 * this gate measures it on GEO as well, and shows the mechanism. {@code CoastingStage} and {@code
 * StageSeparationStage} do not override {@code propagateStandalone}, so it falls back to {@code
 * enter()} and advances no time: in the STANDALONE pass a coast is not flown, and the analytic
 * stage downstream re-plans its own node targeting from the pre-coast state. On GEO the GTO
 * injection lands 2 770 s earlier and half an orbit away. That is a property of the passes, it
 * predates PHY-4, and this gate exists to keep it from moving — not to fix it.
 *
 * <p><b>Fixed variables, never an optimizer output.</b> No CMA-ES runs here. Only the gravity turn
 * takes variables; the analytic stages plan themselves, deterministically within one pass. {@code
 * AnalyticHohmannTransferStage} and {@code AnalyticParkingInsertionStage} are named as optimizable
 * in their own javadoc but implement no optimizable interface — in the chains flown here the
 * gravity-turn first burn is the only {@code OptimizableMissionStage}, which is why the STANDALONE
 * walk reduces to the non-optimizable branch of {@code MissionOptimizer}.
 *
 * <p><b>One boundary past what the spec describes.</b> Spec §5.6 says the gate stops at the last
 * propulsive stage. The REPLAY pass in fact records the trailing {@code Coasting} too: it
 * configures no end date, so the runner bounds it by its 7 200 s safety net and logs a WARN saying
 * so. That WARN is expected output of this fixture, not a defect. The boundary is deterministic and
 * pinning it is strictly stronger, so it is kept — but it is a coast horizon, not physics, and a
 * future lot that changes the safety net will move it legitimately.
 *
 * <p><b>The fixtures are copied, not shared.</b> {@code MeoMissionTest} and {@code
 * PolarCoverageTest} keep theirs {@code private}, and more importantly a gate must not rest on
 * another test's fixture: a change over there would move the reference over here with nobody seeing
 * it.
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

  /**
   * One stage boundary: what the gate pins.
   *
   * <p>{@code failed} is pinned alongside the state because a boundary can otherwise stay green for
   * the wrong reason. The REPLAY runner swallows a propagation exception and reports the stage's
   * <em>entry</em> state as its final one. For thirty-one of the boundaries here that is caught
   * anyway — the entry state is not the pinned end state — but MEO's plane trim is a clean no-op
   * whose pinned value already <em>is</em> its entry state, so a stage that started throwing would
   * pin identically. The flag closes that. In the STANDALONE pass nothing catches, so it is always
   * {@code false}: there, an exception fails the test outright, which is the wanted behaviour.
   *
   * <p>{@code t} is seconds since the launch date. It costs nothing to pin and catches a stage that
   * reaches the right state at the wrong time — a re-planned coast, above all.
   */
  record Boundary(
      String stage,
      double t,
      double x,
      double y,
      double z,
      double vx,
      double vy,
      double vz,
      double mass,
      boolean failed) {}

  // ════════════════════════════════════════════════════════════════════════
  // The pinned boundaries — measured on be7a320 + this gate, 2026-08-16
  // ════════════════════════════════════════════════════════════════════════
  //
  // Cross-checked against 02-baseline-L0.md §3, which is the control §5.3 of the spec asks for:
  // on the REPLAY pass the LEO-400 MECO sits 0.098 m and the GEO MECO 1.06 m from the figures
  // recorded there, which is the truncation of the six-decimal transitionTime being re-flown and
  // nothing else. The polar masses land on §4 exactly — 39 787.241 kg after the ascent,
  // 29 438.109 kg after the trim.
  //
  // Read the STANDALONE constants against the REPLAY ones only with the class javadoc in hand:
  // past the first coast they are deliberately different trajectories, not a discrepancy.

  private static final List<Boundary> LEO_400_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              76.39164210526314,
              -4161034.9606876844,
              -4836957.645658843,
              590773.6731263066,
              2829.2607217724676,
              -3458.967438127062,
              54.124323290628595,
              170150.00000000003,
              false),
          new Boundary(
              "S1 separation",
              78.39164210526314,
              -4155363.820830963,
              -4843860.898629096,
              590880.1233619973,
              2841.8747467619964,
              -3444.283813047835,
              52.32607603022072,
              104150.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              314.193166,
              -3065580.756105344,
              -5677159.277736631,
              577919.1129824509,
              6963.0515855951,
              -3780.275434104066,
              -187.4060930376476,
              36368.08203548459,
              false),
          new Boundary(
              "Transfert",
              3002.836747688871,
              3168936.400792205,
              5961285.082526461,
              -603008.6377632977,
              -6761.441907162198,
              3613.1489887745934,
              186.66828509878627,
              35369.63057846537,
              false),
          new Boundary(
              "Trim",
              8546.404567668282,
              3129196.3682721797,
              5982644.292413696,
              -600467.5597814192,
              -6789.696463594801,
              3570.901488014221,
              195.0811207201827,
              35306.788175214075,
              false),
          new Boundary(
              "Coasting",
              15746.404567668282,
              -6650440.181998898,
              1262824.364510979,
              346567.7896302069,
              -1397.544179285111,
              -7522.756152872497,
              589.336816348514,
              35306.788175214075,
              false));

  private static final List<Boundary> LEO_400_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              76.39164210526314,
              -4161034.9606876844,
              -4836957.645658843,
              590773.6731263066,
              2829.2607217724676,
              -3458.967438127062,
              54.124323290628595,
              170150.00000000003,
              false),
          new Boundary(
              "S1 separation",
              76.39164210526314,
              -4161034.9606876844,
              -4836957.645658843,
              590773.6731263066,
              2829.2607217724676,
              -3458.967438127062,
              54.124323290628595,
              104150.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              314.193166,
              -3065580.7561138854,
              -5677159.277738428,
              577919.1129831139,
              6963.051585579812,
              -3780.2754341155096,
              -187.40609303591012,
              36368.082035484586,
              false),
          new Boundary(
              "Transfert",
              3002.836747690568,
              3168936.400798508,
              5961285.0825230675,
              -603008.6377634697,
              -6761.441907158411,
              3613.1489887817174,
              186.6682850980789,
              35369.63057844603,
              false),
          new Boundary(
              "Trim",
              8546.404567671001,
              3129196.3682713145,
              5982644.29241414,
              -600467.5597813929,
              -6789.696463595714,
              3570.9014880134114,
              195.08112072012372,
              35306.788175189766,
              false),
          new Boundary(
              "Coasting",
              8546.404567671001,
              3129196.3682713145,
              5982644.29241414,
              -600467.5597813929,
              -6789.696463595714,
              3570.9014880134114,
              195.08112072012372,
              35306.788175189766,
              false));

  private static final List<Boundary> GEO_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              156.98165952631575,
              -3990331.7912686206,
              -4958119.857950621,
              588076.4351483264,
              4504.610783911383,
              -4008.6745458054506,
              -16.167527453699485,
              181500.00000000006,
              false),
          new Boundary(
              "S1 separation",
              158.98165952631575,
              -3981310.378170853,
              -4966122.0390679445,
              588042.2960715811,
              4516.797160451363,
              -3993.503187457715,
              -17.971448853860903,
              115500.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              336.124209,
              -2971528.72158108,
              -5650536.517216826,
              569924.046261095,
              7056.0501252158965,
              -3730.6083302299016,
              -197.7687123269583,
              64579.867269962386,
              false),
          new Boundary(
              "Parking",
              3004.340967453397,
              3049387.2626123563,
              6000586.895582041,
              -597754.1923737081,
              -6847.031726449716,
              3498.656712279812,
              203.32624112120058,
              62362.56256610243,
              false),
          new Boundary(
              "Coasting parking",
              4128.768062524889,
              -4888593.466937612,
              4674822.352430858,
              9.094947017729282E-12,
              -5296.1521302287,
              -5522.254603714591,
              710.3410199952696,
              62362.56256610243,
              false),
          new Boundary(
              "GTO injection",
              6979.164251867068,
              5363171.335655637,
              -4159043.9513475136,
              -67915.37855673787,
              6496.609094744228,
              7625.961523348982,
              -928.2804138707179,
              30818.80579511038,
              false),
          new Boundary(
              "S2 separation",
              6981.164251867068,
              5376150.850214461,
              -4143781.4230961213,
              -69771.7639833136,
              6482.900904128504,
              7636.5594898672025,
              -928.1042330659104,
              4000.0,
              false),
          new Boundary(
              "Circularization",
              31384.5768722633,
              -3.828866276610513E7,
              1.6334616275741128E7,
              52816.01938498162,
              -1005.8825168545008,
              -2765.5324237421733,
              -1.7609573410251111,
              2614.5271678302447,
              false),
          new Boundary(
              "Trim",
              98965.00027151976,
              -2.8587485781660516E7,
              3.0999543080187935E7,
              57789.760286321194,
              -2260.033980619004,
              -2084.2841847009554,
              -0.16972274969871384,
              2473.698472986933,
              false),
          new Boundary(
              "Plane trim",
              119970.35251483496,
              -3.2093155824133564E7,
              -2.7347087468971446E7,
              -0.012361423339541489,
              1994.2257198823486,
              -2340.3223268548413,
              -2.021527985007765E-7,
              2470.3698765048334,
              false),
          new Boundary(
              "Coasting",
              127170.35251483496,
              -1.4062589177404426E7,
              -3.9750315103151195E7,
              0.6907753783797899,
              2898.6763664681303,
              -1025.5079966092255,
              1.691760385748791E-4,
              2470.3698765048334,
              false));

  private static final List<Boundary> GEO_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              156.98165952631575,
              -3990331.7912686206,
              -4958119.857950621,
              588076.4351483264,
              4504.610783911383,
              -4008.6745458054506,
              -16.167527453699485,
              181500.00000000006,
              false),
          new Boundary(
              "S1 separation",
              156.98165952631575,
              -3990331.7912686206,
              -4958119.857950621,
              588076.4351483264,
              4504.610783911383,
              -4008.6745458054506,
              -16.167527453699485,
              115500.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              336.124209,
              -2971528.7215811373,
              -5650536.51721683,
              569924.0462610982,
              7056.050125216002,
              -3730.6083302299116,
              -197.76871232697135,
              64579.86726996239,
              false),
          new Boundary(
              "Parking",
              3004.340967453401,
              3049387.2626045384,
              6000586.89558266,
              -597754.1923732482,
              -6847.031726456779,
              3498.6567122740826,
              203.3262411220465,
              62362.5625661001,
              false),
          new Boundary(
              "Coasting parking",
              3004.340967453401,
              3049387.2626045384,
              6000586.89558266,
              -597754.1923732482,
              -6847.031726456779,
              3498.6567122740826,
              203.3262411220465,
              62362.5625661001,
              false),
          new Boundary(
              "GTO injection",
              4209.23137455066,
              -5376098.8720440855,
              4130329.6045724633,
              67813.28327898387,
              -6466.7032171517985,
              -7659.400763571335,
              928.8369321426724,
              30851.546666889808,
              false),
          new Boundary(
              "S2 separation",
              4209.23137455066,
              -5376098.8720440855,
              4130329.6045724633,
              67813.28327898387,
              -6466.7032171517985,
              -7659.400763571335,
              928.8369321426724,
              4000.0,
              false),
          new Boundary(
              "Circularization",
              28612.333010474806,
              3.8360429670312084E7,
              -1.6163088180657953E7,
              -52372.85659070388,
              993.2743595774258,
              2769.9651206314275,
              1.748003361598744,
              2614.0333639516966,
              false),
          new Boundary(
              "Trim",
              96179.84081357492,
              2.872310739256278E7,
              -3.087387831902785E7,
              -57311.29791880287,
              2250.874766019591,
              2094.174614671705,
              0.16846328855420775,
              2473.084462128199,
              false),
          new Boundary(
              "Plane trim",
              117184.47656186146,
              3.1974317348213818E7,
              2.7485908173401117E7,
              0.012055005619515669,
              -2004.3502276534023,
              2331.657823592583,
              2.2826521917096443E-7,
              2469.7841263700943,
              false),
          new Boundary(
              "Coasting",
              117184.47656186146,
              3.1974317348213818E7,
              2.7485908173401117E7,
              0.012055005619515669,
              -2004.3502276534023,
              2331.657823592583,
              2.2826521917096443E-7,
              2469.7841263700943,
              false));

  private static final List<Boundary> MEO_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              128.19836445783133,
              -4098925.1609306443,
              -4850403.573956941,
              778109.0943139206,
              2934.219598059122,
              -2350.3131155661185,
              4666.557620526424,
              63579.63568392503,
              false),
          new Boundary(
              "S1 separation",
              133.19836445783133,
              -4084176.0862850533,
              -4862062.682046478,
              801426.8647898303,
              2965.3859049671037,
              -2313.322008299819,
              4660.521967437738,
              27579.63568392501,
              false),
          new Boundary(
              "Gravity turn (S2)",
              384.663107,
              -3036572.823018373,
              -5272633.81560809,
              2106204.153357493,
              5467.410169764329,
              -858.7645657697162,
              5732.4922537815855,
              17479.876833959104,
              false),
          new Boundary(
              "Parking",
              3062.62607212073,
              3162581.3910491304,
              5541087.882508149,
              -2262386.3036700296,
              -5303.850686703881,
              777.6089488638333,
              -5497.250197444033,
              17096.508695948487,
              false),
          new Boundary(
              "Coasting parking",
              5447.749677305269,
              -4861256.266797463,
              -4722125.557930212,
              0.002282458241097629,
              3271.355811467949,
              -3373.849936247002,
              6062.418740240285,
              17096.508695948487,
              false),
          new Boundary(
              "GTO injection",
              8339.161545610841,
              4387268.762007029,
              5126276.66957439,
              -824025.1829344148,
              -4764.39385061451,
              3592.871796769579,
              -7603.625718716541,
              10888.634298302268,
              false),
          new Boundary(
              "S2 separation",
              8344.161545610841,
              4363377.257129568,
              5144159.541947063,
              -862029.9894382439,
              -4792.176956486476,
              3560.265102120205,
              -7598.257679743834,
              3241.135805750434,
              false),
          new Boundary(
              "Circularization",
              23711.81879971823,
              -8077774.002682127,
              -2.085747850548936E7,
              1.3378327843698116E7,
              2993.81433779354,
              535.9553915025411,
              2424.0079247864933,
              2025.3465169413505,
              false),
          new Boundary(
              "Trim",
              56901.63503019795,
              -2.184029848144173E7,
              -1.121468609463065E7,
              -1.0176420215644725E7,
              64.4243434532862,
              -2655.4105966441653,
              2789.647240115543,
              1999.9999999999893,
              false),
          new Boundary(
              "Plane trim",
              56901.63503019795,
              -2.184029848144173E7,
              -1.121468609463065E7,
              -1.0176420215644725E7,
              64.4243434532862,
              -2655.4105966441653,
              2789.647240115543,
              1999.9999999999893,
              false),
          new Boundary(
              "Coasting",
              64101.63503019795,
              -1.0471457254281698E7,
              -2.1355984620962016E7,
              1.1522959599503215E7,
              2803.9831547831855,
              111.19972997934396,
              2670.225624598103,
              1999.9999999999893,
              false));

  private static final List<Boundary> MEO_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              128.19836445783133,
              -4098925.1609306443,
              -4850403.573956941,
              778109.0943139206,
              2934.219598059122,
              -2350.3131155661185,
              4666.557620526424,
              63579.63568392503,
              false),
          new Boundary(
              "S1 separation",
              128.19836445783133,
              -4098925.1609306443,
              -4850403.573956941,
              778109.0943139206,
              2934.219598059122,
              -2350.3131155661185,
              4666.557620526424,
              27579.63568392501,
              false),
          new Boundary(
              "Gravity turn (S2)",
              384.663107,
              -3036572.8230184163,
              -5272633.815608114,
              2106204.1533574737,
              5467.410169764217,
              -858.7645657697918,
              5732.492253781548,
              17479.876833959104,
              false),
          new Boundary(
              "Parking",
              3062.6260721207423,
              3162581.391049235,
              5541087.882508118,
              -2262386.3036699044,
              -5303.850686704274,
              777.6089488640098,
              -5497.250197444544,
              17096.508695945864,
              false),
          new Boundary(
              "Coasting parking",
              3062.6260721207423,
              3162581.391049235,
              5541087.882508118,
              -2262386.3036699044,
              -5303.850686704274,
              777.6089488640098,
              -5497.250197444544,
              17096.508695945864,
              false),
          new Boundary(
              "GTO injection",
              5565.142798099389,
              -4378107.00198885,
              -5142826.066184822,
              823562.9803047867,
              4770.939371611712,
              -3578.306573040108,
              7599.335988644411,
              10886.291225577303,
              false),
          new Boundary(
              "S2 separation",
              5565.142798099389,
              -4378107.00198885,
              -5142826.066184822,
              823562.9803047867,
              4770.939371611712,
              -3578.306573040108,
              7599.335988644411,
              3241.135805750434,
              false),
          new Boundary(
              "Circularization",
              20947.51559302727,
              7994558.513798372,
              2.08808932212392E7,
              -1.3405294667386945E7,
              -2994.149012355223,
              -545.98906942051,
              -2424.3591460356806,
              2023.706935168799,
              false),
          new Boundary(
              "Trim",
              54033.470700000704,
              2.1818101765416104E7,
              1.0772303996375386E7,
              1.0701957247754177E7,
              14.781474293151035,
              2698.7270776995715,
              -2748.0025729211143,
              1999.99999999999,
              false),
          new Boundary(
              "Plane trim",
              54033.470700000704,
              2.1818101765416104E7,
              1.0772303996375386E7,
              1.0701957247754177E7,
              14.781474293151035,
              2698.7270776995715,
              -2748.0025729211143,
              1999.99999999999,
              false),
          new Boundary(
              "Coasting",
              54033.470700000704,
              2.1818101765416104E7,
              1.0772303996375386E7,
              1.0701957247754177E7,
              14.781474293151035,
              2698.7270776995715,
              -2748.0025729211143,
              1999.99999999999,
              false));

  private static final List<Boundary> POLAR_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              156.98165952631575,
              -4237604.577084436,
              -4825708.974082522,
              813316.9491830231,
              -379.4219889459279,
              -1132.5178453548897,
              5028.679324490893,
              177650.00719250954,
              false),
          new Boundary(
              "S1 separation",
              158.98165952631575,
              -4238350.95635028,
              -4827959.813731344,
              823371.8980372399,
              -366.960227704707,
              -1118.3244783127097,
              5026.265230553439,
              111650.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              408.97965952631574,
              -4138653.747594576,
              -4888695.687681091,
              2386294.83898584,
              1385.9811073767362,
              888.8786253415311,
              7953.379096638929,
              39787.2413195339,
              false),
          new Boundary(
              "Plane trim",
              4199.328119068313,
              5920681.068147236,
              6699823.596242506,
              -111577.28891540543,
              -59.44375121101879,
              -67.24883572063003,
              -6202.174007220991,
              29438.108788232836,
              false));

  private static final List<Boundary> POLAR_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              156.98165952631575,
              -4237604.577084436,
              -4825708.974082522,
              813316.9491830231,
              -379.4219889459279,
              -1132.5178453548897,
              5028.679324490893,
              177650.00719250954,
              false),
          new Boundary(
              "S1 separation",
              156.98165952631575,
              -4237604.577084436,
              -4825708.974082522,
              813316.9491830231,
              -379.4219889459279,
              -1132.5178453548897,
              5028.679324490893,
              111650.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              408.97965952631574,
              -4138653.747595213,
              -4888695.687681808,
              2386294.8389846594,
              1385.9811073811122,
              888.8786253464555,
              7953.37909664752,
              39787.24131953392,
              false),
          new Boundary(
              "Plane trim",
              4199.3281190825,
              5920681.068182074,
              6699823.596282349,
              -111577.28891380837,
              -59.44375120976201,
              -67.2488357192065,
              -6202.174007191841,
              29438.108788332982,
              false));

  // ════════════════════════════════════════════════════════════════════════
  // The four profiles, each flown twice
  // ════════════════════════════════════════════════════════════════════════

  @Test
  void leo400_hasNotMoved() {
    assertPinned("LEO-400 REPLAY", fly(leo400Mission(), 307.193166, 0.127161), LEO_400_REPLAY);
    assertPinned(
        "LEO-400 STANDALONE",
        flyStandalone(leo400Mission(), 307.193166, 0.127161),
        LEO_400_STANDALONE);
  }

  @Test
  void geo_hasNotMoved() {
    assertPinned("GEO REPLAY", fly(geoMission(), 329.124209, 0.177424), GEO_REPLAY);
    assertPinned(
        "GEO STANDALONE", flyStandalone(geoMission(), 329.124209, 0.177424), GEO_STANDALONE);
  }

  @Test
  void meo_hasNotMoved() {
    assertPinned("MEO REPLAY", fly(meoMission(), 378.663107, 0.131995), MEO_REPLAY);
    assertPinned(
        "MEO STANDALONE", flyStandalone(meoMission(), 378.663107, 0.131995), MEO_STANDALONE);
  }

  /**
   * The polar profile freezes a <em>second-burn duration</em>, not an absolute transition time: its
   * fixture writes {@code transitionTime = stagingCompleteTime + 250}. The staging time is read off
   * a reference maneuver rebuilt from the same inputs the first burn phase reads, at the mass the
   * vertical ascent actually leaves behind.
   *
   * <p><b>It flies the ascent and the plane trim, and nothing between them</b> — which is exactly
   * what {@code PolarCoverageTest} flies, and what {@code 02-baseline-L0.md} §4 records. This is
   * not a shortcut: the fixture is knowingly out of envelope (baseline §5.6, {@code bugs.md}
   * BUG-6). Its ascent ends on a suborbital arc whose perigee is −131 km, and {@code
   * AnalyticHohmannTransferStage} refuses to plan from it — "No apogee found within one transfer
   * half-period", thrown from {@code configure()}, which the runner does not catch. Putting the
   * fixture back in envelope would move the polar figures of a baseline five lots are about to rest
   * on, which §5.6 forbids for the duration of PHY-4.
   *
   * <p>Its two passes agree to within 4e-8 m, because the chain it flies contains no coast — the
   * one construct the two passes treat differently.
   */
  @Test
  void polar_hasNotMoved() {
    LaunchPlane polar = LaunchPlane.ofDegrees(90.0);
    Mission replay = polarMission(polar);
    assertPinned(
        "POLAR REPLAY",
        fly(
            replay,
            entry ->
                maneuverOf(replay, entry, polar).getStagingCompleteTime() + POLAR_BURN2_SECONDS,
            POLAR_TURN_EXPONENT,
            CentralBodyBaselineTest::ascentThenPlaneTrim),
        POLAR_REPLAY);

    Mission standalone = polarMission(polar);
    assertPinned(
        "POLAR STANDALONE",
        flyStandalone(
            standalone,
            entry ->
                maneuverOf(standalone, entry, polar).getStagingCompleteTime() + POLAR_BURN2_SECONDS,
            POLAR_TURN_EXPONENT,
            CentralBodyBaselineTest::ascentThenPlaneTrim),
        POLAR_STANDALONE);
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
   * names the pass and the offending stage.
   *
   * @param profile the profile and pass being checked, as it appears in a failure message
   * @param actual the boundaries just flown
   * @param expected the pinned boundaries
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
  // The missions — rebuilt per pass, never reused
  // ════════════════════════════════════════════════════════════════════════
  //
  // A mission carries mutable state (the current state, the stage's injected plan), so the two
  // passes each get their own. Handing one mission to both would make the second pass depend on
  // where the first left off.

  private static Mission leo400Mission() {
    return new EarthOrbitMission("Falcon Heavy", falconHeavyBaselineLoads(), 400_000.0);
  }

  private static Mission geoMission() {
    return new GEOMission("GTO mission", 400_000.0, 35_786_000.0);
  }

  private static Mission meoMission() {
    return MissionComposer.compose(meoSpec(), OptimizationType.FAST);
  }

  private static Mission polarMission(LaunchPlane plane) {
    return MissionComposer.compose(polarSpec(plane), OptimizationType.FAST);
  }

  // ════════════════════════════════════════════════════════════════════════
  // The two passes
  // ════════════════════════════════════════════════════════════════════════

  /**
   * REPLAY pass: flies the chain through {@link StageChainRunner} at fixed ascent variables.
   *
   * @param mission the mission to fly, freshly built
   * @param transitionTime the gravity turn's transition time (s), a fixed literal
   * @param exponent the pitch exponent, a fixed literal
   * @return the boundary state of every stage flown, in flight order
   */
  private static List<Boundary> fly(Mission mission, double transitionTime, double exponent) {
    return fly(
        mission,
        entry -> transitionTime,
        exponent,
        CentralBodyBaselineTest::everythingAfterTheVerticalAscent);
  }

  /**
   * REPLAY pass, with the chain and the transition time chosen by the caller.
   *
   * <p>The transition time is a function of the gravity-turn entry state rather than a literal,
   * because the polar fixture expresses it as a second-burn duration added to a staging time the
   * entry mass determines. The three other profiles pass a constant function. The chain is a
   * function of the mission's stages for the same kind of reason — see {@link
   * #polar_hasNotMoved()}.
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
    SpacecraftState entry = armFirstBurn(mission, initial, transitionTime, exponent);
    AbsoluteDate launch = initial.getDate();

    List<Boundary> recorded = new ArrayList<>();
    StageChainRunner runner =
        StageChainRunner.sampling(
            null,
            0.0,
            run ->
                recorded.add(
                    boundaryOf(
                        run.stage().getName(), run.finalState(), launch, run.propagationFailed())));

    mission.setCurrentState(entry);
    runner.run(chain.apply(mission.getStages()), entry, mission);
    return recorded;
  }

  /**
   * STANDALONE pass: advances the chain the way {@code MissionOptimizer} does, one {@code
   * propagateStandalone} at a time.
   *
   * @param mission the mission to fly, freshly built
   * @param transitionTime the gravity turn's transition time (s), a fixed literal
   * @param exponent the pitch exponent, a fixed literal
   * @return the boundary state of every stage flown, in flight order
   */
  private static List<Boundary> flyStandalone(
      Mission mission, double transitionTime, double exponent) {
    return flyStandalone(
        mission,
        entry -> transitionTime,
        exponent,
        CentralBodyBaselineTest::everythingAfterTheVerticalAscent);
  }

  /**
   * STANDALONE pass, with the chain and the transition time chosen by the caller.
   *
   * <p>This mirrors {@code MissionOptimizer.optimize()}: inject the result into the one optimizable
   * stage of these chains — the gravity-turn first burn, which declares {@code advancesByReplay()}
   * — then advance stage by stage from {@code mission.getCurrentState()}, setting it back after
   * each. No CMA-ES runs; the variables are the fixed literals, so what remains is exactly the
   * loop's non-optimizable branch.
   *
   * <p>Nothing here catches: a stage that throws fails the test where it stands, which is what a
   * gate wants. The REPLAY pass deliberately behaves otherwise, and pins {@code failed} to say so.
   *
   * @param mission the mission to fly, freshly built
   * @param transitionTime the gravity turn's transition time (s), derived from the turn entry state
   * @param exponent the pitch exponent, a fixed literal
   * @param chain picks the stages to fly out of the mission's own list
   * @return the boundary state of every stage flown, in flight order
   */
  private static List<Boundary> flyStandalone(
      Mission mission,
      ToDoubleFunction<SpacecraftState> transitionTime,
      double exponent,
      UnaryOperator<List<MissionStage>> chain) {
    SpacecraftState initial = mission.getInitialState(epoch());
    SpacecraftState entry = armFirstBurn(mission, initial, transitionTime, exponent);
    AbsoluteDate launch = initial.getDate();
    mission.setCurrentState(entry);

    List<Boundary> recorded = new ArrayList<>();
    for (MissionStage stage : chain.apply(mission.getStages())) {
      SpacecraftState propagated = stage.propagateStandalone(mission.getCurrentState(), mission);
      mission.setCurrentState(propagated);
      recorded.add(boundaryOf(stage.getName(), propagated, launch, false));
    }
    return recorded;
  }

  /**
   * Flies the vertical ascent and hands the gravity turn its fixed variables.
   *
   * <p>Only the gravity turn takes variables. {@code AnalyticHohmannTransferStage} and {@code
   * AnalyticParkingInsertionStage} store a plan they compute themselves — injecting anything into
   * them would be injecting an optimizer output, which spec §5.2 forbids this gate.
   *
   * @return the state at gravity-turn entry, shared by both passes
   */
  private static SpacecraftState armFirstBurn(
      Mission mission,
      SpacecraftState initial,
      ToDoubleFunction<SpacecraftState> transitionTime,
      double exponent) {
    mission.setCurrentState(initial);
    SpacecraftState entry = mission.getStages().getFirst().propagateStandalone(initial, mission);
    GravityTurnFirstBurnStage firstBurn = firstBurnOf(mission);
    double[] variables = {transitionTime.applyAsDouble(entry), exponent};
    firstBurn.applyOptimization(new OptimizationResult(variables, 0.0, entry, 1, entry));
    return entry;
  }

  private static Boundary boundaryOf(
      String stage, SpacecraftState state, AbsoluteDate launch, boolean failed) {
    return new Boundary(
        stage,
        state.getDate().durationFrom(launch),
        state.getPosition().getX(),
        state.getPosition().getY(),
        state.getPosition().getZ(),
        state.getPVCoordinates().getVelocity().getX(),
        state.getPVCoordinates().getVelocity().getY(),
        state.getPVCoordinates().getVelocity().getZ(),
        state.getMass(),
        failed);
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
        plane.commands(latitude),
        FlightContext.earth());
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures — copied, never borrowed
  // ════════════════════════════════════════════════════════════════════════

  /**
   * The Falcon Heavy of the <b>LEO-400 baseline</b>: 600 t and 100 t of propellant, <em>not</em>
   * the tank capacities.
   *
   * <p>These are the loads {@code AscentBaselineN2Test.leo400Baseline} flies and the ones {@code
   * 02-baseline-L0.md} §3 records against. A fully loaded stack is a different vehicle: flown at
   * the baseline's fixed variables it re-enters during the second gravity-turn phase, which is how
   * the mismatch was caught. The polar profile below <em>does</em> want the fully loaded stack,
   * because that is what its own fixture flies — the two are deliberately different.
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

  /**
   * A copy of {@code PolarCoverageTest}'s mission: a fully loaded Falcon Heavy to 400 km at 90°.
   */
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
