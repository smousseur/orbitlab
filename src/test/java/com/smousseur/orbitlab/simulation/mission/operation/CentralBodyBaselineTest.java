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
              76.90778157894736,
              -4164668.161345898,
              -4827349.195040115,
              590353.9300294485,
              2547.5911639214896,
              -2988.8135343733625,
              40.194915877599584,
              208150.0065034587,
              false),
          new Boundary(
              "Booster separation",
              76.90878157894737,
              -4164665.6137515674,
              -4827352.183849979,
              590353.9702239139,
              2547.5974976404873,
              -2988.8061927772223,
              40.19401506322882,
              164150.00000000003,
              false),
          new Boundary(
              "Gravity turn (core)",
              91.52269007894738,
              -4123430.053562891,
              -4874463.296481038,
              590918.7142749168,
              3120.3119419191216,
              -3481.886952613149,
              37.104223297064564,
              126150.0,
              false),
          new Boundary(
              "S1 separation",
              93.52269007894738,
              -4117176.949542237,
              -4881412.30259238,
              590991.1273167201,
              3132.7876820991805,
              -3467.117121898623,
              35.30896115999626,
              104150.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              314.193166,
              -3062082.8082133904,
              -5655492.57496921,
              576217.1601048842,
              6877.913548506941,
              -3659.6843315450906,
              -190.22114158337627,
              40717.569625797405,
              false),
          new Boundary(
              "Transfert",
              2999.837896963097,
              3151090.0526107037,
              5970753.55016088,
              -602485.5684339062,
              -6772.097904027633,
              3593.026562556012,
              188.7795432670336,
              37638.248795865686,
              false),
          new Boundary(
              "Trim",
              8543.082874665895,
              3112932.5671697115,
              5991136.479803182,
              -599963.7405803913,
              -6799.285701994859,
              3552.5643083989785,
              196.97808692351953,
              37571.00973003201,
              false),
          new Boundary(
              "Coasting",
              15743.082874665895,
              -6653786.935102202,
              1244586.8752985373,
              348027.03824428964,
              -1376.8984373597377,
              -7526.656431801223,
              588.2058538474611,
              37571.00973003201,
              false));

  private static final List<Boundary> LEO_400_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              76.90778157894736,
              -4164668.161345898,
              -4827349.195040115,
              590353.9300294485,
              2547.5911639214896,
              -2988.8135343733625,
              40.194915877599584,
              208150.0065034587,
              false),
          new Boundary(
              "Booster separation",
              76.90778157894736,
              -4164668.161345898,
              -4827349.195040115,
              590353.9300294485,
              2547.5911639214896,
              -2988.8135343733625,
              40.194915877599584,
              164150.00000000003,
              false),
          new Boundary(
              "Gravity turn (core)",
              91.52269007894738,
              -4123430.0535628926,
              -4874463.29648104,
              590918.714274917,
              3120.3119419191185,
              -3481.8869526131493,
              37.104223297064046,
              126150.0,
              false),
          new Boundary(
              "S1 separation",
              91.52269007894738,
              -4123430.0535628926,
              -4874463.29648104,
              590918.714274917,
              3120.3119419191185,
              -3481.8869526131493,
              37.104223297064046,
              104150.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              314.193166,
              -3062082.8082131753,
              -5655492.574969762,
              576217.1601049064,
              6877.913548494657,
              -3659.684331544451,
              -190.22114158264995,
              40717.569625797405,
              false),
          new Boundary(
              "Transfert",
              2999.837896963596,
              3151090.052607704,
              5970753.55016254,
              -602485.5684338236,
              -6772.0979040279535,
              3593.0265625518095,
              188.7795432673893,
              37638.24879580288,
              false),
          new Boundary(
              "Trim",
              8543.082874663085,
              3112932.567165597,
              5991136.479805467,
              -599963.7405802744,
              -6799.285701995994,
              3552.564308393653,
              196.97808692455885,
              37571.00972996868,
              false),
          new Boundary(
              "Coasting",
              8543.082874663085,
              3112932.567165597,
              5991136.479805467,
              -599963.7405802744,
              -6799.285701995994,
              3552.564308393653,
              196.97808692455885,
              37571.00972996868,
              false));

  private static final List<Boundary> GEO_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              158.04232614473685,
              -4014923.7558969394,
              -4934602.687217674,
              588047.7445792663,
              3760.7079114832654,
              -3296.6859938999623,
              -16.9925330690712,
              259590.0054051415,
              false),
          new Boundary(
              "Booster separation",
              158.04332614473682,
              -4014919.9951859545,
              -4934605.98389989,
              588047.7275862816,
              3760.7140580848154,
              -3296.6784393031717,
              -16.993436331570926,
              215590.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              188.07279811223685,
              -3886927.6360908253,
              -5043652.035326585,
              587255.3477683165,
              4840.208364329082,
              -4027.1247086257904,
              -36.52315608044824,
              137500.0,
              false),
          new Boundary(
              "S1 separation",
              190.07279811223685,
              -3877235.361828743,
              -5051690.877558053,
              587180.5026304183,
              4852.060544816203,
              -4011.7139809924747,
              -38.321879087324845,
              115500.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              350.538381,
              -2928393.8927805796,
              -5671418.79514583,
              568539.8456009798,
              7105.257153903603,
              -3702.308752505122,
              -203.05609582472684,
              69373.73609753327,
              false),
          new Boundary(
              "Parking",
              5780.509880069834,
              -3291781.2986500124,
              -5872127.51582351,
              603905.7934619216,
              6698.4560782883045,
              -3773.661681703989,
              -178.59694492497704,
              67086.45247976859,
              false),
          new Boundary(
              "Coasting parking",
              6937.064407635615,
              4867669.172606534,
              -4699841.710619275,
              -0.005657543379129493,
              5320.717622220965,
              5495.426963679046,
              -710.1562870007025,
              67086.45247976859,
              false),
          new Boundary(
              "GTO injection",
              9794.011075071025,
              -5379615.841797788,
              4139554.9562891503,
              73176.04993452976,
              -6494.109919888709,
              -7626.793752537825,
              928.0262414518329,
              33150.17589796061,
              false),
          new Boundary(
              "S2 separation",
              9796.011075071025,
              -5392590.32308445,
              4124290.818734731,
              75031.91352841457,
              -6480.366886675887,
              -7637.336515100051,
              927.8365755628023,
              4000.0,
              false),
          new Boundary(
              "Circularization",
              34193.34740260202,
              3.821191902935039E7,
              -1.6512925057599798E7,
              -52733.12607409262,
              1018.7493469157081,
              2760.829864613796,
              1.758547175711275,
              2614.3792462077067,
              false),
          new Boundary(
              "Trim",
              101772.64909675256,
              2.8440566326680645E7,
              -3.1134366355843585E7,
              -57696.121107657666,
              2269.865462563304,
              2073.574402577667,
              0.16944168628644246,
              2473.5564029041316,
              false),
          new Boundary(
              "Plane trim",
              122777.9306602247,
              3.222224112874915E7,
              2.7194880749157622E7,
              0.012300425411467586,
              -1983.1259721144704,
              2349.7351018968734,
              2.1794270033126395E-7,
              2470.233450660624,
              false),
          new Boundary(
              "Coasting",
              129977.9306602247,
              1.4250585883604523E7,
              3.968331411400295E7,
              -0.6562506884856998,
              -2893.789559543876,
              1039.2173174997847,
              -1.5994247695201322E-4,
              2470.233450660624,
              false));

  private static final List<Boundary> GEO_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              158.04232614473685,
              -4014923.7558969394,
              -4934602.687217674,
              588047.7445792663,
              3760.7079114832654,
              -3296.6859938999623,
              -16.9925330690712,
              259590.0054051415,
              false),
          new Boundary(
              "Booster separation",
              158.04232614473685,
              -4014923.7558969394,
              -4934602.687217674,
              588047.7445792663,
              3760.7079114832654,
              -3296.6859938999623,
              -16.9925330690712,
              215590.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              188.07279811223685,
              -3886927.6360912994,
              -5043652.035326219,
              587255.3477683226,
              4840.208364338022,
              -4027.124708632759,
              -36.52315608054735,
              137500.0,
              false),
          new Boundary(
              "S1 separation",
              188.07279811223685,
              -3886927.6360912994,
              -5043652.035326219,
              587255.3477683226,
              4840.208364338022,
              -4027.124708632759,
              -36.52315608054735,
              115500.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              350.538381,
              -2928393.8927795836,
              -5671418.795146594,
              568539.845600968,
              7105.257153912406,
              -3702.308752511983,
              -203.05609582482307,
              69373.7360975333,
              false),
          new Boundary(
              "Parking",
              5780.509880086054,
              -3291781.298708797,
              -5872127.515790587,
              603905.7934635107,
              6698.456078250813,
              -3773.6616817706295,
              -178.59694491805485,
              67086.45247955402,
              false),
          new Boundary(
              "Coasting parking",
              5780.509880086054,
              -3291781.298708797,
              -5872127.515790587,
              603905.7934635107,
              6698.456078250813,
              -3773.6616817706295,
              -178.59694491805485,
              67086.45247955402,
              false),
          new Boundary(
              "GTO injection",
              7023.948166441015,
              5394826.203262375,
              -4113478.033092447,
              -73157.73062945913,
              6462.7023418617055,
              7657.583505815719,
              -928.2007382208479,
              33176.112037024286,
              false),
          new Boundary(
              "S2 separation",
              7023.948166441015,
              5394826.203262375,
              -4113478.033092447,
              -73157.73062945913,
              6462.7023418617055,
              7657.583505815719,
              -928.2007382208479,
              4000.0,
              false),
          new Boundary(
              "Circularization",
              31421.998422369546,
              -3.828334744152066E7,
              1.6345434042700475E7,
              52400.880403661395,
              -1006.5152154113084,
              -2765.229063151754,
              -1.748675138448065,
              2614.1436344144913,
              false),
          new Boundary(
              "Trim",
              98993.60042410222,
              -2.8575904196130086E7,
              3.10102240467844E7,
              57344.657540571294,
              -2260.81227364404,
              -2083.4396094908093,
              -0.16850479320692388,
              2473.240411389438,
              false),
          new Boundary(
              "Plane trim",
              119998.57173648918,
              -3.210412922006615E7,
              -2.7334200591586437E7,
              -0.012076596881676949,
              1993.286191479109,
              -2341.1227936098426,
              -1.9065558152120666E-7,
              2469.9379278062943,
              false),
          new Boundary(
              "Coasting",
              119998.57173648918,
              -3.210412922006615E7,
              -2.7334200591586437E7,
              -0.012076596881676949,
              1993.286191479109,
              -2341.1227936098426,
              -1.9065558152120666E-7,
              2469.9379278062943,
              false));

  private static final List<Boundary> MEO_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              130.01395054861575,
              -4181147.01870142,
              -4849056.840945875,
              693171.5989455961,
              990.8169238651551,
              -2101.691662963645,
              2465.6418022821586,
              198831.8620316959,
              false),
          new Boundary(
              "Booster separation",
              130.01495054861576,
              -4181146.0278813723,
              -4849058.942633915,
              693174.0645868788,
              990.8231724209303,
              -2101.68441620021,
              2465.6407629723863,
              154831.86203169587,
              false),
          new Boundary(
              "Gravity turn (core)",
              479.9880107334524,
              -3242839.3074813127,
              -5393940.739379472,
              1962744.2479392735,
              5031.583181870083,
              -934.5657848150038,
              5388.078727174685,
              44003.53938933713,
              false),
          new Boundary(
              "S1 separation",
              484.9880107334524,
              -3217625.095641613,
              -5398519.663544663,
              1989650.2214235468,
              5054.073155067941,
              -896.9971388157993,
              5374.278827621312,
              30003.539389337115,
              false),
          new Boundary(
              "Gravity turn (S2)",
              504.913955,
              -3115260.5182209616,
              -5415027.896389422,
              2097083.4479527911,
              5220.674689344469,
              -759.3504129442119,
              5408.658505646738,
              29203.27637663899,
              false),
          new Boundary(
              "Parking",
              3249.9133833764186,
              2722136.71512236,
              5611375.002939255,
              -2652458.7028893903,
              -5536.117952070913,
              337.246062210483,
              -5296.002591541294,
              27125.940134556913,
              false),
          new Boundary(
              "Coasting parking",
              5620.332231882379,
              -4862164.216446683,
              -4789911.358781025,
              7.694325176998973E-10,
              3371.413057749241,
              -3256.772905873198,
              6003.094968387439,
              27125.940134556913,
              false),
          new Boundary(
              "GTO injection",
              8526.413050308798,
              3991197.8909256873,
              5343218.153625311,
              -1311773.7529604435,
              -5176.8024667761,
              3153.7316619009785,
              -7526.420461593924,
              17316.16004019649,
              false),
          new Boundary(
              "S2 separation",
              8531.413050308798,
              3965250.6570447856,
              5358901.911335895,
              -1349384.7730495422,
              -5202.055167994028,
              3119.766214851334,
              -7517.950512828286,
              3241.135805750434,
              false),
          new Boundary(
              "Circularization",
              23831.566098629533,
              -7874160.474099341,
              -2.0896667894394122E7,
              1.3413308522427112E7,
              2993.6187617409196,
              567.490926092284,
              2417.057606416911,
              2020.3162009120233,
              false),
          new Boundary(
              "Trim",
              56985.4684290231,
              -2.1753823636469338E7,
              -1.1481376144728756E7,
              -1.0055984762233118E7,
              102.6990444984142,
              -2638.226670829313,
              2791.8823766152486,
              1999.9999999999998,
              false),
          new Boundary(
              "Plane trim",
              56985.4684290231,
              -2.1753823636469338E7,
              -1.1481376144728756E7,
              -1.0055984762233118E7,
              102.6990444984142,
              -2638.226670829313,
              2791.8823766152486,
              1999.9999999999998,
              false),
          new Boundary(
              "Coasting",
              64185.4684290231,
              -1.0185800943477267E7,
              -2.1369693989581022E7,
              1.1594262853148296E7,
              2818.135566831226,
              162.19750567556477,
              2653.4330008027982,
              1999.9999999999998,
              false));

  private static final List<Boundary> MEO_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              130.01395054861575,
              -4181147.01870142,
              -4849056.840945875,
              693171.5989455961,
              990.8169238651551,
              -2101.691662963645,
              2465.6418022821586,
              198831.8620316959,
              false),
          new Boundary(
              "Booster separation",
              130.01395054861575,
              -4181147.01870142,
              -4849056.840945875,
              693171.5989455961,
              990.8169238651551,
              -2101.691662963645,
              2465.6418022821586,
              154831.86203169587,
              false),
          new Boundary(
              "Gravity turn (core)",
              479.9880107334524,
              -3242839.3074813117,
              -5393940.739379471,
              1962744.2479392744,
              5031.583181870099,
              -934.565784815009,
              5388.078727174693,
              44003.53938933712,
              false),
          new Boundary(
              "S1 separation",
              479.9880107334524,
              -3242839.3074813117,
              -5393940.739379471,
              1962744.2479392744,
              5031.583181870099,
              -934.565784815009,
              5388.078727174693,
              30003.539389337115,
              false),
          new Boundary(
              "Gravity turn (S2)",
              504.913955,
              -3115260.5182209616,
              -5415027.896389421,
              2097083.4479527925,
              5220.674689344486,
              -759.3504129442173,
              5408.658505646745,
              29203.27637663899,
              false),
          new Boundary(
              "Parking",
              3249.913383376417,
              2722136.7151223905,
              5611375.002939259,
              -2652458.70288937,
              -5536.117952070677,
              337.2460622105115,
              -5296.002591541092,
              27125.940134558867,
              false),
          new Boundary(
              "Coasting parking",
              3249.913383376417,
              2722136.7151223905,
              5611375.002939259,
              -2652458.70288937,
              -5536.117952070677,
              337.2460622105115,
              -5296.002591541092,
              27125.940134558867,
              false),
          new Boundary(
              "GTO injection",
              5810.155182498314,
              -4036433.9232941815,
              -5419197.839015117,
              1316606.7677745086,
              5139.719350649617,
              -3122.569115815257,
              7469.939362359897,
              17093.04200623992,
              false),
          new Boundary(
              "S2 separation",
              5810.155182498314,
              -4036433.9232941815,
              -5419197.839015117,
              1316606.7677745086,
              5139.719350649617,
              -3122.569115815257,
              7469.939362359897,
              3241.135805750434,
              false),
          new Boundary(
              "Circularization",
              21124.720370593495,
              7899467.469266566,
              2.0946495239793845E7,
              -1.3359103742314985E7,
              -2986.303935718386,
              -560.0120478270946,
              -2428.0083722168524,
              2028.1157028399264,
              false),
          new Boundary(
              "Trim",
              54330.658771308015,
              2.1730286658787847E7,
              1.1353068059393419E7,
              1.0260038610075908E7,
              -77.27397266404823,
              2665.4979427327826,
              -2787.088677901562,
              2000.0000000000102,
              false),
          new Boundary(
              "Plane trim",
              54330.658771308015,
              2.1730286658787847E7,
              1.1353068059393419E7,
              1.0260038610075908E7,
              -77.27397266404823,
              2665.4979427327826,
              -2787.088677901562,
              2000.0000000000102,
              false),
          new Boundary(
              "Coasting",
              54330.658771308015,
              2.1730286658787847E7,
              1.1353068059393419E7,
              1.0260038610075908E7,
              -77.27397266404823,
              2665.4979427327826,
              -2787.088677901562,
              2000.0000000000102,
              false));

  private static final List<Boundary> POLAR_REPLAY =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              158.04232614473685,
              -4228327.252580658,
              -4816000.608152841,
              783586.9339854427,
              -205.23029467871484,
              -934.2414718780419,
              4039.430423542925,
              255740.00654053967,
              false),
          new Boundary(
              "Booster separation",
              158.04332614473682,
              -4228327.457807817,
              -4816001.542390741,
              783590.9734152834,
              -205.22402369044877,
              -934.2343292735254,
              4039.4292576348485,
              211740.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              188.07279811223685,
              -4234418.54592352,
              -4844014.381436106,
              922508.7713851755,
              -201.8720634423643,
              -933.4961292241812,
              5316.484790101972,
              133650.0,
              false),
          new Boundary(
              "S1 separation",
              190.07279811223685,
              -4234809.984910381,
              -4845867.295675786,
              933139.0413905155,
              -189.56972681745182,
              -919.4206380860729,
              5313.780711526595,
              111650.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              440.0687981122369,
              -4083391.124302556,
              -4847816.36121932,
              2562316.464809254,
              1625.43298195068,
              1163.6227712904729,
              8180.393282004926,
              39787.81622850225,
              false),
          new Boundary(
              "Plane trim",
              4724.190462508688,
              7090793.987209726,
              8045365.136263405,
              -72811.66322414955,
              -31.20790025705682,
              -35.40419102450481,
              -5350.037619271398,
              31960.92717451523,
              false));

  private static final List<Boundary> POLAR_STANDALONE =
      List.of(
          new Boundary(
              "Gravity turn (S1)",
              158.04232614473685,
              -4228327.252580658,
              -4816000.608152841,
              783586.9339854427,
              -205.23029467871484,
              -934.2414718780419,
              4039.430423542925,
              255740.00654053967,
              false),
          new Boundary(
              "Booster separation",
              158.04232614473685,
              -4228327.252580658,
              -4816000.608152841,
              783586.9339854427,
              -205.23029467871484,
              -934.2414718780419,
              4039.430423542925,
              211740.00000000006,
              false),
          new Boundary(
              "Gravity turn (core)",
              188.07279811223685,
              -4234418.545923519,
              -4844014.381436104,
              922508.7713851752,
              -201.8720634423638,
              -933.49612922418,
              5316.484790101971,
              133650.0,
              false),
          new Boundary(
              "S1 separation",
              188.07279811223685,
              -4234418.545923519,
              -4844014.381436104,
              922508.7713851752,
              -201.8720634423638,
              -933.49612922418,
              5316.484790101971,
              111650.0,
              false),
          new Boundary(
              "Gravity turn (S2)",
              440.0687981122369,
              -4083391.124301972,
              -4847816.361218666,
              2562316.4648102643,
              1625.4329819466022,
              1163.6227712858745,
              8180.3932819974725,
              39787.816228502255,
              false),
          new Boundary(
              "Plane trim",
              4724.190462492488,
              7090793.98717056,
              8045365.136218556,
              -72811.66322527941,
              -31.207900257707024,
              -35.40419102524952,
              -5350.037619295663,
              31960.927174418503,
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
