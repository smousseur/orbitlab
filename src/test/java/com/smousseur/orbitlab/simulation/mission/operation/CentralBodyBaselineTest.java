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
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnSecondBurnStage;
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
 * <b>PHY-4 / L1 — the central-body refactor gate</b>.
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
 *
 * <p><b>It runs in the {@code gateTest} Gradle task, not in {@code test}.</b> The 62 boundaries are
 * compared at strict {@code double} equality, and a lunar propagation earlier in the same JVM moves
 * them by the last bit through Orekit's shared time caches. {@code
 * gateTest} forks one JVM per class, which removes the contamination; {@code test} runs everything
 * in one JVM and excludes this class. It carried {@code @Disabled("To be run only standalone")}
 * from 2026-08-31 to PHY-8 / L0, which had the same effect and no way to run it.
 */
class CentralBodyBaselineTest {

  private static final Logger logger = LogManager.getLogger(CentralBodyBaselineTest.class);

  /**
   * When {@code -Dorbitlab.recordBaseline=true}, {@link #assertPinned} prints the flown boundaries
   * as pasteable {@code new Boundary(...)} literals instead of asserting, so a legitimate
   * re-baseline regenerates the pinned constants exactly rather than by hand. Off by default: the
   * gate asserts.
   */
  private static final boolean RECORD_BASELINE = Boolean.getBoolean("orbitlab.recordBaseline");

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
  // The pinned boundaries — measured on be7a320 + this gate, 2026-08-16, and re-measured on the
  // three Falcon Heavy profiles at PHY-8 / L3, which throttles that launcher's core to 0.81 and so
  // splits its ascent into five phases. The MEO profile flies an Ariane 64 and its twenty-two
  // boundaries are byte-identical across that change — which is what makes the re-baseline
  // attributable to the throttle and to nothing else. Forty-two boundaries moved and became
  // fifty-four; twenty-two did not move at all.
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
              76.90778157894736,
              -4164668.1748928297,
              -4827349.187134846,
              590353.930350174,
              2547.591040310054,
              -2988.8135155204122,
              40.194922301576085,
              208150.0000000001,
              false),
          new Boundary(
              "Booster separation",
              76.90878157894737,
              -4164665.6272986224,
              -4827352.175944691,
              590353.9705446459,
              2547.5973740290638,
              -2988.806173924294,
              40.19402148720605,
              164150.00000000003,
              false),
          new Boundary(
              "Gravity turn (core)",
              91.52269007894738,
              -4123430.068932636,
              -4874463.288304344,
              590918.7146906375,
              3120.311817111082,
              -3481.8869354567037,
              37.10422988292738,
              126150.0,
              false),
          new Boundary(
              "S1 separation",
              93.52269007894738,
              -4117176.965161573,
              -4881412.294381424,
              590991.1277456145,
              3132.787557316076,
              -3467.117104793002,
              35.30896774774298,
              104150.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              314.193166,
              -3062082.8517169217,
              -5655492.564903674,
              576217.1621287308,
              6877.91341952498,
              -3659.6843373440156,
              -190.22113329452833,
              40717.56962579739,
              false),
          new Boundary(
              "Transfert",
              2999.8379040545924,
              3151090.067586401,
              5970753.542224896,
              -602485.56878803,
              -6772.0978950886565,
              3593.0265794719508,
              188.779541873161,
              37638.24789236337,
              false),
          new Boundary(
              "Trim",
              8550.070935700733,
              3065596.3142166263,
              6015739.250590403,
              -598566.8255393656,
              -6826.475660495901,
              3499.0111386052895,
              204.695599323282,
              37578.04935702623,
              false),
          new Boundary(
              "Coasting",
              15750.070935700733,
              -6663076.091579355,
              1189344.0473481352,
              354285.3831981311,
              -1313.977248630608,
              -7538.584836961966,
              584.1721522193216,
              37578.04935702623,
              false));

  private static final List<Boundary> LEO_400_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              76.90778157894736,
              -4164668.1748928297,
              -4827349.187134846,
              590353.930350174,
              2547.591040310054,
              -2988.8135155204122,
              40.194922301576085,
              208150.0000000001,
              false),
          new Boundary(
              "Booster separation",
              76.90778157894736,
              -4164668.1748928297,
              -4827349.187134846,
              590353.930350174,
              2547.591040310054,
              -2988.8135155204122,
              40.194922301576085,
              164150.00000000003,
              false),
          new Boundary(
              "Gravity turn (core)",
              91.52269007894738,
              -4123430.0689326366,
              -4874463.288304345,
              590918.7146906374,
              3120.3118171110764,
              -3481.886935456702,
              37.104229882927186,
              126150.0,
              false),
          new Boundary(
              "S1 separation",
              91.52269007894738,
              -4123430.0689326366,
              -4874463.288304345,
              590918.7146906374,
              3120.3118171110764,
              -3481.886935456702,
              37.104229882927186,
              104150.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              314.193166,
              -3062082.8517192635,
              -5655492.564904112,
              576217.1621289079,
              6877.9134195227,
              -3659.684337347208,
              -190.2211332941781,
              40717.569625797405,
              false),
          new Boundary(
              "Transfert",
              2999.8379040550417,
              3151090.0675882353,
              5970753.54222393,
              -602485.5687880797,
              -6772.097895089782,
              3593.0265794751895,
              188.77954187295822,
              37638.24789235701,
              false),
          new Boundary(
              "Trim",
              8550.070935705187,
              3065596.3142283214,
              6015739.250584397,
              -598566.8255397178,
              -6826.475660491258,
              3499.0111386196336,
              204.695599321957,
              37578.049357021446,
              false),
          new Boundary(
              "Coasting",
              8550.070935705187,
              3065596.3142283214,
              6015739.250584397,
              -598566.8255397178,
              -6826.475660491258,
              3499.0111386196336,
              204.695599321957,
              37578.049357021446,
              false));

  private static final List<Boundary> GEO_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              158.04232614473685,
              -4014923.791765251,
              -4934602.660445158,
              588047.7450203883,
              3760.707681147242,
              -3296.6858440151354,
              -16.992529069692438,
              259590.0065991914,
              false),
          new Boundary(
              "Booster separation",
              158.04332614473682,
              -4014920.0310544968,
              -4934605.957127225,
              588047.7280274075,
              3760.713827748841,
              -3296.6782894183925,
              -16.993432332192036,
              215590.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              188.07279811223685,
              -3886927.678963286,
              -5043652.004158667,
              587255.3483402331,
              4840.208128500803,
              -4027.124567307732,
              -36.52315129657161,
              137500.0,
              false),
          new Boundary(
              "S1 separation",
              190.07279811223685,
              -3877235.4051727373,
              -5051690.846107605,
              587180.5032119021,
              4852.060309111121,
              -4011.7138397809154,
              -38.32187430394749,
              115500.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              350.538381,
              -2928393.973668162,
              -5671418.7428382635,
              568539.8470412773,
              7105.256922573013,
              -3702.308637059593,
              -203.05608975059005,
              69373.73609753328,
              false),
          new Boundary(
              "Parking",
              5780.509618086626,
              -3291779.035395169,
              -5872128.791601778,
              603905.7327987744,
              6698.457513494568,
              -3773.659115257164,
              -178.5972112374294,
              67086.45753085142,
              false),
          new Boundary(
              "Coasting parking",
              6937.063840627581,
              4867669.343643123,
              -4699841.529438644,
              -0.030396279876185872,
              5320.717415749592,
              5495.427162159525,
              -710.1562869865994,
              67086.45753085142,
              false),
          new Boundary(
              "GTO injection",
              9794.010476471705,
              -5379615.872621557,
              4139554.9020264708,
              73176.05715767041,
              -6494.109889943865,
              -7626.793789321053,
              928.0262418495735,
              33150.178359389465,
              false),
          new Boundary(
              "S2 separation",
              9796.010476471705,
              -5392590.353848198,
              4124290.7643985837,
              75031.92075233154,
              -6480.366856600592,
              -7637.336551784559,
              927.8365759413688,
              4000.0,
              false),
          new Boundary(
              "Circularization",
              34193.34516064228,
              3.82119151702508E7,
              -1.651293197853841E7,
              -52733.13714709235,
              1018.7492817006089,
              2760.8293282118593,
              1.758548035553224,
              2614.3797782045094,
              false),
          new Boundary(
              "Trim",
              101772.61843567825,
              2.8440580887914263E7,
              -3.1134353119588308E7,
              -57696.137530355285,
              2269.864492079416,
              2073.57546031101,
              0.16944233635154834,
              2473.5564350862405,
              false),
          new Boundary(
              "Plane trim",
              122777.89807134288,
              3.2222232312587168E7,
              2.719489119734858E7,
              0.01230043255350921,
              -1983.1267341395187,
              2349.7344591340507,
              2.1794321991563947E-7,
              2470.2334817028513,
              false),
          new Boundary(
              "Coasting",
              129977.89807134288,
              1.4250573017394153E7,
              3.96833187372041E7,
              -0.6562499271533195,
              -2893.7898965719523,
              1039.216379582467,
              -1.5994226779278965E-4,
              2470.2334817028513,
              false));

  private static final List<Boundary> GEO_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              158.04232614473685,
              -4014923.791765251,
              -4934602.660445158,
              588047.7450203883,
              3760.707681147242,
              -3296.6858440151354,
              -16.992529069692438,
              259590.0065991914,
              false),
          new Boundary(
              "Booster separation",
              158.04232614473685,
              -4014923.791765251,
              -4934602.660445158,
              588047.7450203883,
              3760.707681147242,
              -3296.6858440151354,
              -16.992529069692438,
              215590.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              188.07279811223685,
              -3886927.6789632845,
              -5043652.004158669,
              587255.348340233,
              4840.208128500804,
              -4027.1245673077315,
              -36.52315129657182,
              137500.0,
              false),
          new Boundary(
              "S1 separation",
              188.07279811223685,
              -3886927.6789632845,
              -5043652.004158669,
              587255.348340233,
              4840.208128500804,
              -4027.1245673077315,
              -36.52315129657182,
              115500.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              350.538381,
              -2928393.9736681897,
              -5671418.742838267,
              568539.8470412793,
              7105.256922573069,
              -3702.3086370595956,
              -203.05608975059255,
              69373.7360975333,
              false),
          new Boundary(
              "Parking",
              5780.509618086872,
              -3291779.035394354,
              -5872128.791602186,
              603905.7327987497,
              6698.457513495962,
              -3773.659115256721,
              -178.5972112375488,
              67086.45753086949,
              false),
          new Boundary(
              "Coasting parking",
              5780.509618086872,
              -3291779.035394354,
              -5872128.791602186,
              603905.7327987497,
              6698.457513495962,
              -3773.659115256721,
              -178.5972112375488,
              67086.45753086949,
              false),
          new Boundary(
              "GTO injection",
              7023.947571401662,
              5394826.23082466,
              -4113477.996522454,
              -73157.7364553205,
              6462.702317775705,
              7657.583526902382,
              -928.200737967063,
              33176.114504856174,
              false),
          new Boundary(
              "S2 separation",
              7023.947571401662,
              5394826.23082466,
              -4113477.996522454,
              -73157.7364553205,
              6462.702317775705,
              7657.583526902382,
              -928.200737967063,
              4000.0,
              false),
          new Boundary(
              "Circularization",
              31421.997025513534,
              -3.828334518574794E7,
              1.6345437783019952E7,
              52400.91144904161,
              -1006.5151439102846,
              -2765.22877172308,
              -1.7486764082745236,
              2614.143946340941,
              false),
          new Boundary(
              "Trim",
              98993.58146740039,
              -2.857591106718464E7,
              3.1010217753475595E7,
              57344.693439803516,
              -2260.811811619573,
              -2083.440108147652,
              -0.1685052501226828,
              2473.240429601583,
              false),
          new Boundary(
              "Plane trim",
              119998.55165269948,
              -3.2104125457211673E7,
              -2.7334205012358762E7,
              -0.01207661520285086,
              1993.2865139213625,
              -2341.1225192915363,
              -1.9062695599814106E-7,
              2469.9379438441733,
              false),
          new Boundary(
              "Coasting",
              119998.55165269948,
              -3.2104125457211673E7,
              -2.7334205012358762E7,
              -0.01207661520285086,
              1993.2865139213625,
              -2341.1225192915363,
              -1.9062695599814106E-7,
              2469.9379438441733,
              false));

  private static final List<Boundary> MEO_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              130.01395054861578,
              -4181147.0239757053,
              -4849056.837177314,
              693171.5898373628,
              990.8168896003506,
              -2101.691655150398,
              2465.6417599080482,
              198831.8620316959,
              false),
          new Boundary(
              "Booster separation",
              130.0149505486158,
              -4181146.0331556913,
              -4849058.938865346,
              693174.055478603,
              990.8231381561349,
              -2101.684408386968,
              2465.6407205982896,
              154831.86203169587,
              false),
          new Boundary(
              "Gravity turn (core)",
              479.9880107334525,
              -3242839.325101181,
              -5393940.7344444515,
              1962744.225255426,
              5031.583144255974,
              -934.5657898969802,
              5388.078694684702,
              44003.53938933713,
              false),
          new Boundary(
              "S1 separation",
              484.9880107334525,
              -3217625.113449188,
              -5398519.658635045,
              1989650.1985776117,
              5054.073117599737,
              -896.9971438943041,
              5374.278795276398,
              30003.539389337115,
              false),
          new Boundary(
              "Gravity turn (S2)",
              504.913955,
              -3115260.536771893,
              -5415027.891585052,
              2097083.4244698456,
              5220.674652206111,
              -759.3504184287041,
              5408.6584740586,
              29203.27637663899,
              false),
          new Boundary(
              "Parking",
              3249.913386399196,
              2722136.6455559954,
              5611375.009183482,
              -2652458.7686659866,
              -5536.117984209627,
              337.24599596628815,
              -5296.002557192556,
              27125.939826767382,
              false),
          new Boundary(
              "Coasting parking",
              5620.332265345109,
              -4862164.122652487,
              -4789911.4618431,
              0.17730976414168254,
              3371.4132497823307,
              -3256.7727051319216,
              6003.09495391811,
              27125.939826767382,
              false),
          new Boundary(
              "GTO injection",
              8526.41303686276,
              3991197.8669566438,
              5343218.133869467,
              -1311773.7546371317,
              -5176.802488443613,
              3153.7316654418264,
              -7526.420479554678,
              17316.15985124011,
              false),
          new Boundary(
              "S2 separation",
              8531.41303686276,
              3965250.632966969,
              5358901.89159697,
              -1349384.7748157342,
              -5202.055189835251,
              3119.7662180788043,
              -7517.950530668454,
              3241.135805750434,
              false),
          new Boundary(
              "Circularization",
              23831.56626798745,
              -7874160.006659925,
              -2.089666778510585E7,
              1.3413308880490629E7,
              2993.618802896216,
              567.4910047102964,
              2417.0575757138763,
              2020.3161850881095,
              false),
          new Boundary(
              "Trim",
              56985.46691685281,
              -2.17538239473853E7,
              -1.1481370772803273E7,
              -1.0055990600085236E7,
              102.69810384966749,
              -2638.227147549792,
              2791.8819220918335,
              2000.0,
              false),
          new Boundary(
              "Plane trim",
              56985.46691685281,
              -2.17538239473853E7,
              -1.1481370772803273E7,
              -1.0055990600085236E7,
              102.69810384966749,
              -2638.227147549792,
              2791.8819220918335,
              2000.0,
              false),
          new Boundary(
              "Coasting",
              64185.46691685281,
              -1.0185806822936453E7,
              -2.1369694266277637E7,
              1.159425723202366E7,
              2818.1351181866044,
              162.19656314849937,
              2653.4335196522948,
              2000.0,
              false));

  private static final List<Boundary> MEO_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              130.01395054861578,
              -4181147.0239757053,
              -4849056.837177314,
              693171.5898373628,
              990.8168896003506,
              -2101.691655150398,
              2465.6417599080482,
              198831.8620316959,
              false),
          new Boundary(
              "Booster separation",
              130.01395054861578,
              -4181147.0239757053,
              -4849056.837177314,
              693171.5898373628,
              990.8168896003506,
              -2101.691655150398,
              2465.6417599080482,
              154831.86203169587,
              false),
          new Boundary(
              "Gravity turn (core)",
              479.9880107334525,
              -3242839.3251011847,
              -5393940.73444445,
              1962744.2252554232,
              5031.583144255949,
              -934.5657898969805,
              5388.078694684698,
              44003.539389337115,
              false),
          new Boundary(
              "S1 separation",
              479.9880107334525,
              -3242839.3251011847,
              -5393940.73444445,
              1962744.2252554232,
              5031.583144255949,
              -934.5657898969805,
              5388.078694684698,
              30003.539389337115,
              false),
          new Boundary(
              "Gravity turn (S2)",
              504.913955,
              -3115260.5367718977,
              -5415027.89158505,
              2097083.4244698423,
              5220.674652206088,
              -759.3504184287056,
              5408.658474058595,
              29203.27637663899,
              false),
          new Boundary(
              "Parking",
              3249.913386399198,
              2722136.645556017,
              5611375.009183479,
              -2652458.7686659684,
              -5536.117984209474,
              337.24599596629076,
              -5296.0025571924425,
              27125.939826768383,
              false),
          new Boundary(
              "Coasting parking",
              3249.913386399198,
              2722136.645556017,
              5611375.009183479,
              -2652458.7686659684,
              -5536.117984209474,
              337.24599596629076,
              -5296.0025571924425,
              27125.939826768383,
              false),
          new Boundary(
              "GTO injection",
              5810.1551842761355,
              -4036433.9332184885,
              -5419197.841393659,
              1316606.7591235705,
              5139.719346477715,
              -3122.5691202137536,
              7469.939357961367,
              17093.041784185785,
              false),
          new Boundary(
              "S2 separation",
              5810.1551842761355,
              -4036433.9332184885,
              -5419197.841393659,
              1316606.7591235705,
              5139.719346477715,
              -3122.5691202137536,
              7469.939357961367,
              3241.135805750434,
              false),
          new Boundary(
              "Circularization",
              21124.72024299462,
              7899468.029511385,
              2.0946495168471582E7,
              -1.335910309390482E7,
              -2986.303900594293,
              -560.01202903744,
              -2428.0083579683524,
              2028.1157641661855,
              false),
          new Boundary(
              "Trim",
              54330.66048126564,
              2.1730286313735083E7,
              1.1353078588862516E7,
              1.0260027575597E7,
              -77.27578427542034,
              2665.4970166657185,
              -2787.089552587791,
              1999.9999999999998,
              false),
          new Boundary(
              "Plane trim",
              54330.66048126564,
              2.1730286313735083E7,
              1.1353078588862516E7,
              1.0260027575597E7,
              -77.27578427542034,
              2665.4970166657185,
              -2787.089552587791,
              1999.9999999999998,
              false),
          new Boundary(
              "Coasting",
              54330.66048126564,
              2.1730286313735083E7,
              1.1353078588862516E7,
              1.0260027575597E7,
              -77.27578427542034,
              2665.4970166657185,
              -2787.089552587791,
              1999.9999999999998,
              false));

  private static final List<Boundary> POLAR_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              158.04232614473685,
              -4228327.253279578,
              -4816000.608925714,
              783586.923013382,
              -205.23031741934497,
              -934.2414978304607,
              4039.430456516934,
              255740.00000000006,
              false),
          new Boundary(
              "Booster separation",
              158.04332614473682,
              -4228327.45850676,
              -4816001.54316364,
              783590.9624432556,
              -205.22404643107697,
              -934.2343552259421,
              4039.429290608874,
              211740.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              188.07279811223685,
              -4234418.547324662,
              -4844014.383009641,
              922508.7613635841,
              -201.87208758908693,
              -933.496156747518,
              5316.4848238610275,
              133650.0,
              false),
          new Boundary(
              "S1 separation",
              190.07279811223685,
              -4234809.986359817,
              -4845867.297304368,
              933139.0314364722,
              -189.56975096394626,
              -919.4206656092322,
              5313.780745315618,
              111650.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              440.0687981122369,
              -4083391.1322264047,
              -4847816.370228109,
              2562316.463746502,
              1625.4329535976785,
              1163.6227389259127,
              8180.393319748925,
              39787.81622850223,
              false),
          new Boundary(
              "Plane trim",
              4724.190585653393,
              7090794.131177691,
              8045365.302376592,
              -72811.67706381617,
              -31.207907940916954,
              -35.404199281241915,
              -5350.037534272545,
              31960.92698832929,
              false));

  private static final List<Boundary> POLAR_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              158.04232614473685,
              -4228327.253279578,
              -4816000.608925714,
              783586.923013382,
              -205.23031741934497,
              -934.2414978304607,
              4039.430456516934,
              255740.00000000006,
              false),
          new Boundary(
              "Booster separation",
              158.04232614473685,
              -4228327.253279578,
              -4816000.608925714,
              783586.923013382,
              -205.23031741934497,
              -934.2414978304607,
              4039.430456516934,
              211740.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              188.07279811223685,
              -4234418.547324661,
              -4844014.383009643,
              922508.7613635845,
              -201.87208758908605,
              -933.4961567475153,
              5316.484823861018,
              133650.0,
              false),
          new Boundary(
              "S1 separation",
              188.07279811223685,
              -4234418.547324661,
              -4844014.383009643,
              922508.7613635845,
              -201.87208758908605,
              -933.4961567475153,
              5316.484823861018,
              111650.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              440.0687981122369,
              -4083391.132226465,
              -4847816.370228181,
              2562316.4637463945,
              1625.4329535980473,
              1163.6227389263145,
              8180.393319749691,
              39787.816228502255,
              false),
          new Boundary(
              "Plane trim",
              4724.190585655062,
              7090794.131181675,
              8045365.302381095,
              -72811.67706361724,
              -31.20790793888972,
              -35.404199280214556,
              -5350.0375342702655,
              31960.926988318333,
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

  /**
   * <b>The one profile whose variables PHY-8 / L3 had to move</b>, and it was not a choice. At the
   * pre-L3 literals (329.124209 / 0.177424) the throttled ascent hands over a state the analytic
   * chain cannot plan a transfer from: the STANDALONE pass throws "No apogee found within one
   * transfer half-period" at the GTO injection, and a pass that throws cannot be pinned at all.
   *
   * <p>The replacement is measured, not chosen: it is the optimum {@code AscentBaselineN2Test}
   * re-recorded for this very vehicle and target on the same lot. Nothing is read off these numbers
   * — see the class javadoc on what a fingerprint is and is not.
   */
  @Test
  void geo_hasNotMoved() {
    assertPinned("GEO REPLAY", fly(geoMission(), 343.538381, 0.193846), GEO_REPLAY);
    assertPinned(
        "GEO STANDALONE", flyStandalone(geoMission(), 343.538381, 0.193846), GEO_STANDALONE);
  }

  /**
   * <b>The one profile PHY-8 / L4 had to give new variables</b>, and it was not a choice. This
   * fixture flies the Ariane, and the Ariane 64 that replaces the 62 stages at 479 s where the
   * aggregate staged at 128 — so the frozen 378.663107 now falls <em>below</em> the staging floor
   * and the ascent refuses it outright. The replacement is measured, not chosen: it is the optimum
   * {@code MeoMissionTest} retains for this very profile, which converges 500x below the acceptable
   * cost at 63 % of its box.
   *
   * <p><b>RE-RECORDED again at PHY-8, closing the L3 fallout.</b> {@code PropellantBudget} now
   * reserves 1 300 m/s of insertion ΔV on the top stage, and this is the one profile of the four
   * that reads its loads from the budget — {@link #meoSpec()} calls {@code loadsForHighOrbit},
   * where the LEO and polar fixtures fly hand-written loads and the GEO one the historical
   * constructor. Those three are therefore untouched, and that is the witness: 204 lines moved
   * here, none anywhere else in this file, and no boundary gained or lost.
   *
   * <p>The reserve exists because the Falcon Heavy's throttled core hands over with more energy
   * than an upper stage sized on the ideal ΔV chain can correct — measured at 448 m/s aboard
   * against the ~1 300 the transfer asked for. See {@code PropellantBudget.TOP_STAGE_INSERTION_-
   * RESERVE_DV} for why it is added rather than floored.
   */
  @Test
  void meo_hasNotMoved() {
    assertPinned("MEO REPLAY", fly(meoMission(), 498.913955, 0.293836), MEO_REPLAY);
    assertPinned(
        "MEO STANDALONE", flyStandalone(meoMission(), 498.913955, 0.293836), MEO_STANDALONE);
  }

  /**
   * The polar profile freezes a <em>second-burn duration</em>, not an absolute transition time: its
   * fixture writes {@code transitionTime = stagingCompleteTime + 250}. The staging time is read off
   * a reference maneuver rebuilt from the same inputs the first burn phase reads, at the mass the
   * vertical ascent actually leaves behind.
   *
   * <p><b>It flies the ascent and the plane trim, and nothing between them</b> — which is what
   * {@code PolarCoverageTest} flew until 2026-08-31, and what {@code 02-baseline-L0.md} §4 records.
   * Its ascent ends on a suborbital arc whose perigee is −131 km, and {@code
   * AnalyticHohmannTransferStage} refuses to plan from it — "No apogee found within one transfer
   * half-period", thrown from {@code configure()}, which the runner does not catch.
   *
   * <p><b>It stays that way now that {@code bugs.md} BUG-6 is closed, and that is a decision.</b>
   * {@code PolarCoverageTest} moved back into envelope on 2026-08-31 and its figures moved with it;
   * this gate did not follow. What it guards is that twenty propagator construction sites still
   * produce the same arithmetic, and a frozen deterministic trajectory does that whether or not it
   * is a flight anyone would fly. Re-pinning would cost a fresh measurement, buy no guard strength,
   * and break the continuity with the L1 reference. The values below are an arithmetic fingerprint:
   * reading a mission cost off them is precisely the mistake BUG-6 records.
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
    if (RECORD_BASELINE) {
      logger.info("recordBaseline [{}] =\n{}", profile, asLiterals(actual));
      return;
    }
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

  /**
   * One {@link Boundary} per line, full round-trip precision, wrapped in a {@code List.of(...)}.
   */
  private static String asLiterals(List<Boundary> boundaries) {
    StringBuilder sb = new StringBuilder("List.of(");
    for (int i = 0; i < boundaries.size(); i++) {
      Boundary b = boundaries.get(i);
      sb.append("\n    new Boundary(\"")
          .append(b.stage())
          .append("\", ")
          .append(b.t())
          .append(", ")
          .append(b.x())
          .append(", ")
          .append(b.y())
          .append(", ")
          .append(b.z())
          .append(", ")
          .append(b.vx())
          .append(", ")
          .append(b.vy())
          .append(", ")
          .append(b.vz())
          .append(", ")
          .append(b.mass())
          .append(", ")
          .append(b.failed())
          .append(i < boundaries.size() - 1 ? ")," : "));");
    }
    return sb.toString();
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
    // Every ascent phase after the vertical one, up to and including the second burn. Selected by
    // type rather than by a subList(1, 4): PHY-8 / L3 gives the Falcon Heavy a five-phase ascent,
    // and the index form silently dropped the S1 separation and the whole second burn, flying the
    // plane trim from the end of the core burn.
    List<MissionStage> chain = new ArrayList<>();
    for (MissionStage stage : stages.subList(1, stages.size())) {
      chain.add(stage);
      if (stage instanceof GravityTurnSecondBurnStage) {
        break;
      }
    }
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
        Launchers.FALCON_HEAVY, new double[] {400_000, 200_000, 100_000}, Spacecraft.LEGACY);
  }

  /** The fully loaded Falcon Heavy the polar fixture flies. */
  private static LaunchConfiguration falconHeavyFullyLoaded() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  /** A copy of {@code MeoMissionTest}'s hand-built spec: Ariane 64 to 20 200 km at 55°. */
  private static MissionSpec.EarthOrbit meoSpec() {
    PayloadModel model = Payloads.GEO_SAT;
    double payloadDryMass = model.defaultDryMass();
    LaunchPlane plane = LaunchPlane.ofDegrees(MEO_INCLINATION_DEG, NodeBranch.ASCENDING);
    double azimuth = plane.launchAzimuth(FastMath.toRadians(LAT));

    PropellantBudget.SizedLoads loads =
        PropellantBudget.loadsForHighOrbit(
            Launchers.ARIANE_64,
            model,
            payloadDryMass,
            MEO_PARKING_ALTITUDE,
            MEO_ALTITUDE,
            LAT,
            0.0,
            azimuth);
    Spacecraft payload = model.toSpacecraft(payloadDryMass, loads.payloadLoad());

    return new MissionSpec.EarthOrbit(
        "MEO Galileo",
        new LaunchConfiguration(Launchers.ARIANE_64, loads.launcherLoads(), payload, model.id()),
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
