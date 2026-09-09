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
 *
 * <p><b>It runs in the {@code gateTest} Gradle task, not in {@code test}.</b> The 62 boundaries are
 * compared at strict {@code double} equality, and a lunar propagation earlier in the same JVM moves
 * them by the last bit through Orekit's shared time caches ({@code docs/bugs.md} BUG-7). {@code
 * gateTest} forks one JVM per class, which removes the contamination; {@code test} runs everything
 * in one JVM and excludes this class. It carried {@code @Disabled("To be run only standalone")}
 * from 2026-08-31 to PHY-8 / L0, which had the same effect and no way to run it.
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
  // The pinned boundaries — measured on be7a320 + this gate, 2026-08-16, and re-measured on the
  // three Falcon Heavy profiles at PHY-8 / L3, which throttles that launcher's core to 0.81 and so
  // splits its ascent into five phases. The MEO profile flies an Ariane 64 and its twenty-two
  // boundaries are byte-identical across that change — which is what makes the re-baseline
  // attributable to the throttle and to nothing else (spec docs/etagement/05-conception-L3.md
  // §5.1). Forty-two boundaries moved and became fifty-four; twenty-two did not move at all.
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
              76.39164210526316,
              -4165765.7928744075,
              -4826164.610657123,
              590343.5997294071,
              2527.5122668327563,
              -2972.9869104658424,
              40.40260284584179,
              208150.0,
              false),
          new Boundary(
              "Booster separation",
              76.39264210526316,
              -4165763.265358973,
              -4826167.583640363,
              590343.6401315596,
              2527.518602755549,
              -2972.979570051706,
              40.40170197090738,
              164150.0,
              false),
          new Boundary(
              "Gravity turn (core)",
              90.90848410526316,
              -4125128.0061023585,
              -4872714.56393565,
              590908.4204645224,
              3095.6023584486925,
              -3463.4319783876876,
              37.42903854100886,
              126150.0,
              false),
          new Boundary(
              "S1 separation",
              92.90848410526316,
              -4118924.314666473,
              -4879626.663785931,
              590981.4829665112,
              3108.0847044756865,
              -3448.6658532275346,
              35.63360582013765,
              104150.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              314.193166,
              -3065171.9834796577,
              -5652251.568200303,
              576194.5594955221,
              6868.129910879236,
              -3647.533998665822,
              -190.4209993465533,
              40541.013364454375,
              false),
          new Boundary(
              "Transfert",
              3000.022691006276,
              3150515.8045862075,
              5971052.200452161,
              -602468.5828872334,
              -6772.438186842774,
              3592.3815480738394,
              188.8476052339383,
              37262.524952911044,
              false),
          new Boundary(
              "Trim",
              8543.261615874384,
              3112307.763643382,
              5991456.946383542,
              -599944.4041713069,
              -6799.654812223538,
              3551.863613430551,
              197.0506501301141,
              37195.9074567376,
              false),
          new Boundary(
              "Coasting",
              15743.261615874384,
              -6653917.7946810685,
              1243859.9566251638,
              348085.02830789646,
              -1376.0780602332648,
              -7526.810662246307,
              588.1613993808301,
              37195.9074567376,
              false));

  private static final List<Boundary> LEO_400_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              76.39164210526316,
              -4165765.7928744075,
              -4826164.610657123,
              590343.5997294071,
              2527.5122668327563,
              -2972.9869104658424,
              40.40260284584179,
              208150.0,
              false),
          new Boundary(
              "Booster separation",
              76.39164210526316,
              -4165765.7928744075,
              -4826164.610657123,
              590343.5997294071,
              2527.5122668327563,
              -2972.9869104658424,
              40.40260284584179,
              164150.0,
              false),
          new Boundary(
              "Gravity turn (core)",
              90.90848410526316,
              -4125128.00610232,
              -4872714.563935687,
              590908.4204645221,
              3095.6023584479017,
              -3463.4319783869355,
              37.42903854100838,
              126150.0,
              false),
          new Boundary(
              "S1 separation",
              90.90848410526316,
              -4125128.00610232,
              -4872714.563935687,
              590908.4204645221,
              3095.6023584479017,
              -3463.4319783869355,
              37.42903854100838,
              104150.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              314.193166,
              -3065171.9834796772,
              -5652251.56820071,
              576194.5594955494,
              6868.129910866669,
              -3647.533998664582,
              -190.42099934584746,
              40541.01336445439,
              false),
          new Boundary(
              "Transfert",
              3000.022691006788,
              3150515.8045831188,
              5971052.200453781,
              -602468.5828871441,
              -6772.438186842928,
              3592.381548069474,
              188.8476052342978,
              37262.52495284537,
              false),
          new Boundary(
              "Trim",
              8543.261615872143,
              3112307.763630599,
              5991456.946390214,
              -599944.404170929,
              -6799.654812229854,
              3551.8636134155604,
              197.05065013211217,
              37195.90745666801,
              false),
          new Boundary(
              "Coasting",
              8543.261615872143,
              3112307.763630599,
              5991456.946390214,
              -599944.404170929,
              -6799.654812229854,
              3551.8636134155604,
              197.05065013211217,
              37195.90745666801,
              false));

  private static final List<Boundary> GEO_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              156.98165952631578,
              -4018139.6186662726,
              -4932291.281872878,
              588096.6059905972,
              3729.6558425057256,
              -3281.5057688439133,
              -16.034975268904837,
              259590.00000000015,
              false),
          new Boundary(
              "Booster separation",
              156.98265952631576,
              -4018135.8890073546,
              -4932294.563374871,
              588096.5899551703,
              3729.661993332263,
              -3281.498218642054,
              -16.035878504065188,
              215590.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              186.8106048363158,
              -3892057.0363520803,
              -5040118.826028889,
              587343.776084144,
              4799.85951164632,
              -4009.2752914146063,
              -35.152016451295836,
              137500.0,
              false),
          new Boundary(
              "S1 separation",
              188.8106048363158,
              -3882445.4459787696,
              -5048121.982744497,
              587271.6732430652,
              4811.725541907227,
              -3993.8779112961665,
              -36.95072206437905,
              115500.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              350.538381,
              -2931211.88044389,
              -5669999.511485067,
              568624.2776461149,
              7085.78882783752,
              -3686.924954195374,
              -202.85070382632307,
              69010.91298047341,
              false),
          new Boundary(
              "Parking",
              3018.420199916283,
              3015487.3588061063,
              6017763.393749971,
              -596687.1981107217,
              -6867.697246259273,
              3457.737743998848,
              207.62655461221956,
              66726.17550349666,
              false),
          new Boundary(
              "Coasting parking",
              4137.160187826467,
              -4885367.8686931515,
              4674720.07999075,
              0.358442486525405,
              -5299.20000560054,
              -5523.089277155766,
              710.6023421849882,
              66726.17550349666,
              false),
          new Boundary(
              "GTO injection",
              6993.051795974421,
              5397497.477721633,
              -4121929.0895953514,
              -72809.0294937402,
              6461.808747941874,
              7650.365675928372,
              -927.6579603582135,
              32960.53541398003,
              false),
          new Boundary(
              "S2 separation",
              6995.051795974421,
              5410407.3322851285,
              -4106617.869387063,
              -74664.15787205778,
              6448.04136569771,
              7660.847245532499,
              -927.4696422303862,
              4000.0,
              false),
          new Boundary(
              "Circularization",
              31393.919914416278,
              -3.8283685042273626E7,
              1.6346953418534357E7,
              52891.600493070626,
              -1006.8539379796941,
              -2765.232654613686,
              -1.763064716346239,
              2614.6489208569737,
              false),
          new Boundary(
              "Trim",
              98978.86133481127,
              -2.857747526525073E7,
              3.100876764148781E7,
              57870.0288551439,
              -2260.7069081681793,
              -2083.5545870649416,
              -0.1698975543918948,
              2473.870675683239,
              false),
          new Boundary(
              "Plane trim",
              119984.42643774378,
              -3.210155640003511E7,
              -2.733722758563988E7,
              -0.012413634711030852,
              1993.506609169741,
              -2340.9348021814026,
              -2.041425135601571E-7,
              2470.5372582334235,
              false),
          new Boundary(
              "Coasting",
              127184.42643774378,
              -1.407480147980372E7,
              -3.974599362134355E7,
              0.690999183379928,
              2898.3611092947826,
              -1026.398464830183,
              1.692575420818689E-4,
              2470.5372582334235,
              false));

  private static final List<Boundary> GEO_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              156.98165952631578,
              -4018139.6186662726,
              -4932291.281872878,
              588096.6059905972,
              3729.6558425057256,
              -3281.5057688439133,
              -16.034975268904837,
              259590.00000000015,
              false),
          new Boundary(
              "Booster separation",
              156.98165952631578,
              -4018139.6186662726,
              -4932291.281872878,
              588096.6059905972,
              3729.6558425057256,
              -3281.5057688439133,
              -16.034975268904837,
              215590.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              186.8106048363158,
              -3892057.0363520803,
              -5040118.826028883,
              587343.7760841439,
              4799.859511646316,
              -4009.2752914146063,
              -35.15201645129511,
              137500.0,
              false),
          new Boundary(
              "S1 separation",
              186.8106048363158,
              -3892057.0363520803,
              -5040118.826028883,
              587343.7760841439,
              4799.859511646316,
              -4009.2752914146063,
              -35.15201645129511,
              115500.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              350.538381,
              -2931211.8804438724,
              -5669999.511485062,
              568624.277646113,
              7085.788827837403,
              -3686.924954195376,
              -202.85070382632057,
              69010.91298047341,
              false),
          new Boundary(
              "Parking",
              3018.4201999162838,
              3015487.358806337,
              6017763.393749877,
              -596687.1981107288,
              -6867.6972462591575,
              3457.737743999032,
              207.62655461220538,
              66726.17550349433,
              false),
          new Boundary(
              "Coasting parking",
              3018.4201999162838,
              3015487.358806337,
              6017763.393749877,
              -596687.1981107288,
              -6867.6972462591575,
              3457.737743999032,
              207.62655461220538,
              66726.17550349433,
              false),
          new Boundary(
              "GTO injection",
              4223.461263935652,
              -5406849.317093801,
              4089681.525834406,
              72714.23954292637,
              -6434.13377317695,
              -7686.9061328494045,
              928.5841678089411,
              33008.490848035195,
              false),
          new Boundary(
              "S2 separation",
              4223.461263935652,
              -5406849.317093801,
              4089681.525834406,
              72714.23954292637,
              -6434.13377317695,
              -7686.9061328494045,
              928.5841678089411,
              4000.0,
              false),
          new Boundary(
              "Circularization",
              28620.512904080653,
              3.835517057317062E7,
              -1.6174774697787333E7,
              -52252.30084785985,
              994.0621456868923,
              2769.666143734916,
              1.7445324026224096,
              2613.8220090608283,
              false),
          new Boundary(
              "Trim",
              96184.50394044665,
              2.8711436007668387E7,
              -3.088473226188892E7,
              -57182.43892566486,
              2251.666095206846,
              2093.3237452970557,
              0.16810849921568233,
              2472.8530207829785,
              false),
          new Boundary(
              "Plane trim",
              117189.03353156957,
              3.1984914024594232E7,
              2.747357418851783E7,
              0.011972983797598147,
              -2003.4509084699087,
              2332.4306910679375,
              2.2478136152415118E-7,
              2469.560376112267,
              false),
          new Boundary(
              "Coasting",
              117189.03353156957,
              3.1984914024594232E7,
              2.747357418851783E7,
              0.011972983797598147,
              -2003.4509084699087,
              2332.4306910679375,
              2.2478136152415118E-7,
              2469.560376112267,
              false));

  private static final List<Boundary> MEO_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              130.01395054861578,
              -4181325.949259994,
              -4850130.688985532,
              694084.1806024164,
              991.5047129596929,
              -2129.1086939330444,
              2494.133561130502,
              195052.75148642447,
              false),
          new Boundary(
              "Booster separation",
              130.0149505486158,
              -4181324.9577521584,
              -4850132.818090604,
              694086.6747354574,
              991.5109588018477,
              -2129.101449022937,
              2494.132520950253,
              151052.74720055403,
              false),
          new Boundary(
              "Gravity turn (core)",
              479.98801073345254,
              -3232870.6974030286,
              -5413423.97248421,
              1992380.7797119492,
              5151.431624568253,
              -1020.112670427751,
              5593.858369734807,
              40224.42455819527,
              false),
          new Boundary(
              "S1 separation",
              484.98801073345254,
              -3207057.9320366816,
              -5418431.1488853125,
              2020315.4473483136,
              5173.644765293055,
              -982.7522707241528,
              5579.976524314754,
              26224.424558195267,
              false),
          new Boundary(
              "Gravity turn (S2)",
              504.913955,
              -3102205.7304297565,
              -5436670.092333289,
              2131973.901081884,
              5350.937303501981,
              -847.2477466911712,
              5627.137466375962,
              25424.161545497147,
              false),
          new Boundary(
              "Parking",
              3234.398717553482,
              3154842.2088922993,
              5562560.81309218,
              -2221234.9031232586,
              -5288.1765033820675,
              793.9278025579558,
              -5509.644417965237,
              25152.546849074686,
              false),
          new Boundary(
              "Coasting parking",
              5626.537167932706,
              -4825502.821044031,
              -4758706.072434932,
              3.3568358048796654E-6,
              3304.1726383468094,
              -3356.642579990896,
              6054.267923351511,
              25152.546849074686,
              false),
          new Boundary(
              "GTO injection",
              8574.928597684691,
              4086906.184167315,
              5334389.648589128,
              -1217227.7947904347,
              -5080.3775009352785,
              3221.696964839574,
              -7528.6125984229975,
              15978.67578957909,
              false),
          new Boundary(
              "S2 separation",
              8579.928597684691,
              4061440.461421195,
              5350414.560138781,
              -1254851.5559125142,
              -5105.877220135159,
              3188.261181264769,
              -7520.854872071039,
              3241.135805750434,
              false),
          new Boundary(
              "Circularization",
              23897.82024282606,
              -7906537.265216769,
              -2.0915692671354957E7,
              1.3387677785382286E7,
              2990.494651668337,
              560.5421095579545,
              2423.0425391769054,
              2024.2373565736902,
              false),
          new Boundary(
              "Trim",
              57066.35394449255,
              -2.1756113157278117E7,
              -1.1321003212535556E7,
              -1.0239251749591371E7,
              74.34596254548833,
              -2659.106689063176,
              2783.679749021309,
              1999.9999999999939,
              false),
          new Boundary(
              "Plane trim",
              57066.35394449255,
              -2.1756113157278117E7,
              -1.1321003212535556E7,
              -1.0239251749591371E7,
              74.34596254548833,
              -2659.106689063176,
              2783.679749021309,
              1999.9999999999939,
              false),
          new Boundary(
              "Coasting",
              64266.35394449255,
              -1.036884625435912E7,
              -2.1428653911670707E7,
              1.1455899162399862E7,
              2799.074311179464,
              124.11787626112483,
              2674.853858576985,
              1999.9999999999939,
              false));

  private static final List<Boundary> MEO_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              130.01395054861578,
              -4181325.949259994,
              -4850130.688985532,
              694084.1806024164,
              991.5047129596929,
              -2129.1086939330444,
              2494.133561130502,
              195052.75148642447,
              false),
          new Boundary(
              "Booster separation",
              130.01395054861578,
              -4181325.949259994,
              -4850130.688985532,
              694084.1806024164,
              991.5047129596929,
              -2129.1086939330444,
              2494.133561130502,
              151052.74720055403,
              false),
          new Boundary(
              "Gravity turn (core)",
              479.98801073345254,
              -3232870.6974030253,
              -5413423.972484209,
              1992380.7797119494,
              5151.431624568261,
              -1020.1126704277483,
              5593.858369734806,
              40224.42455819528,
              false),
          new Boundary(
              "S1 separation",
              479.98801073345254,
              -3232870.6974030253,
              -5413423.972484209,
              1992380.7797119494,
              5151.431624568261,
              -1020.1126704277483,
              5593.858369734806,
              26224.424558195267,
              false),
          new Boundary(
              "Gravity turn (S2)",
              504.913955,
              -3102205.7304297527,
              -5436670.092333287,
              2131973.9010818843,
              5350.9373035019835,
              -847.2477466911687,
              5627.137466375959,
              25424.161545497147,
              false),
          new Boundary(
              "Parking",
              3234.3987175534803,
              3154842.2088923026,
              5562560.813092176,
              -2221234.903123259,
              -5288.176503382276,
              793.9278025579957,
              -5509.64441796546,
              25152.546849072918,
              false),
          new Boundary(
              "Coasting parking",
              3234.3987175534803,
              3154842.2088923026,
              5562560.813092176,
              -2221234.903123259,
              -5288.176503382276,
              793.9278025579957,
              -5509.64441796546,
              25152.546849072918,
              false),
          new Boundary(
              "GTO injection",
              5800.759480811579,
              -4077330.521055525,
              -5349939.091441176,
              1216352.148046627,
              5085.448987469494,
              -3207.0390856212025,
              7524.658358252804,
              15976.901872645127,
              false),
          new Boundary(
              "S2 separation",
              5800.759480811579,
              -4077330.521055525,
              -5349939.091441176,
              1216352.148046627,
              5085.448987469494,
              -3207.0390856212025,
              7524.658358252804,
              3241.135805750434,
              false),
          new Boundary(
              "Circularization",
              21123.679159543255,
              7855484.554564683,
              2.0939608508957196E7,
              -1.3382262538684774E7,
              -2988.3765449403545,
              -567.7471824247042,
              -2423.6493053154695,
              2025.0683089331678,
              false),
          new Boundary(
              "Trim",
              54307.41988932231,
              2.1723050002606966E7,
              1.1421219121036466E7,
              1.0197645957140956E7,
              -88.5832934001088,
              2656.190067077636,
              -2787.7786000704523,
              1999.9999999999936,
              false),
          new Boundary(
              "Plane trim",
              54307.41988932231,
              2.1723050002606966E7,
              1.1421219121036466E7,
              1.0197645957140956E7,
              -88.5832934001088,
              2656.190067077636,
              -2787.7786000704523,
              1999.9999999999936,
              false),
          new Boundary(
              "Coasting",
              54307.41988932231,
              2.1723050002606966E7,
              1.1421219121036466E7,
              1.0197645957140956E7,
              -88.5832934001088,
              2656.190067077636,
              -2787.7786000704523,
              1999.9999999999936,
              false));

  private static final List<Boundary> POLAR_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              156.98165952631578,
              -4228401.855032343,
              -4815341.290189934,
              780711.7210427757,
              -206.40273499128597,
              -935.4466308484657,
              4008.579679449359,
              255740.00000000017,
              false),
          new Boundary(
              "Booster separation",
              156.98265952631576,
              -4228402.061431942,
              -4815342.225632993,
              780715.729621874,
              -206.3964615788442,
              -935.4394865866628,
              4008.578517389355,
              211740.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              186.8106048363158,
              -4234511.9679654855,
              -4843230.501632864,
              917656.9136717594,
              -204.87538688997248,
              -936.7343791852926,
              5276.610439243942,
              133650.0,
              false),
          new Boundary(
              "S1 separation",
              188.8106048363158,
              -4234909.406401628,
              -4845089.886726777,
              928207.4476817471,
              -192.5658421849229,
              -922.6532295618729,
              5273.919097744498,
              111650.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              438.8066048363158,
              -4084535.6701211454,
              -4848206.646831667,
              2547756.8938586703,
              1619.720016727567,
              1157.1549873073736,
              8144.267627297191,
              39787.81622850228,
              false),
          new Boundary(
              "Plane trim",
              4622.386524269915,
              6919510.784126472,
              7850030.789082993,
              -75141.43919504878,
              -33.17084823453021,
              -37.625990519283846,
              -5456.070170932699,
              31867.476252668766,
              false));

  private static final List<Boundary> POLAR_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              156.98165952631578,
              -4228401.855032343,
              -4815341.290189934,
              780711.7210427757,
              -206.40273499128597,
              -935.4466308484657,
              4008.579679449359,
              255740.00000000017,
              false),
          new Boundary(
              "Booster separation",
              156.98165952631578,
              -4228401.855032343,
              -4815341.290189934,
              780711.7210427757,
              -206.40273499128597,
              -935.4466308484657,
              4008.579679449359,
              211740.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              186.8106048363158,
              -4234511.967965486,
              -4843230.501632865,
              917656.9136717591,
              -204.8753868899719,
              -936.7343791852936,
              5276.610439243945,
              133650.0,
              false),
          new Boundary(
              "S1 separation",
              186.8106048363158,
              -4234511.967965486,
              -4843230.501632865,
              917656.9136717591,
              -204.8753868899719,
              -936.7343791852936,
              5276.610439243945,
              111650.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              438.8066048363158,
              -4084535.670120563,
              -4848206.646831013,
              2547756.8938596835,
              1619.7200167234812,
              1157.15498730279,
              8144.267627289707,
              39787.81622850228,
              false),
          new Boundary(
              "Plane trim",
              4622.386524254387,
              6919510.784088489,
              7850030.789039517,
              -75141.43919625715,
              -33.17084823530314,
              -37.62599052014589,
              -5456.07017095718,
              31867.47625257282,
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
