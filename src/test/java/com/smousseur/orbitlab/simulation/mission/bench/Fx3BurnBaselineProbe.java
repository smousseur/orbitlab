package com.smousseur.orbitlab.simulation.mission.bench;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.LunarOrbitMission;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.stage.StageNames;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import com.smousseur.orbitlab.simulation.mission.window.problem.MissionScheduler;
import com.smousseur.orbitlab.states.mission.BurnTimeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The measured baseline of the burns of the production missions: what {@link BurnTimeline} reads
 * off the raw ephemeris {@code MissionPlanOptimizer.compute()} returns, and the five verdicts whose
 * rules were fixed before this ran. NOT a gate: it changes no {@code src/main} and asserts nothing.
 * Run with {@code -Dorbitlab.probe=true --tests '*Fx3BurnBaselineProbe*'}; the report is also
 * written to {@code build/fx3-burn-baseline.txt}, which a later test run does not erase.
 *
 * <p>Seven missions from Kourou, each built from wizard values by {@code
 * MissionFactory.specFromWizardValues} and computed in FAST: Falcon Heavy and Ariane 64 to a 400 km
 * LEO, a Falcon Heavy LEO whose payload deorbits, an Ariane 64 LEO on a commanded 97.5° plane — the
 * one Earth orbit that flies a plane trim — both launchers to GEO, and a Falcon Heavy to a 100 km
 * lunar orbit. The lunar entry is scheduled the way the wizard schedules it, through {@link
 * MissionScheduler}, since its date and plane come from the launch window.
 *
 * <p><b>Retrograde by construction</b> is decided from the code, not from the figure under test: a
 * deorbit burn and a lunar orbit insertion always brake; the analytic Hohmann transfer, parking
 * insertion and circular trim choose their direction from the sign of the ΔV they log. Those plans
 * are read off the log, from the ephemeris generation of the retained pass — the last one, unless
 * the measured-load sizing fell back on an earlier pass, which the report flags.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Fx3BurnBaselineProbe {

  private static final double ORBIT_ALTITUDE_KM = 400.0;
  private static final String LUNAR_FLOOR = "2026-10-06T00:00:00Z";
  private static final double LUNAR_PAYLOAD_MASS_KG = 2_000.0;

  private static final double RETROGRADE_THRESHOLD = -0.5;
  private static final double RETROGRADE_CEILING = -0.8;
  private static final double OTHERS_FLOOR = -0.2;
  private static final double[] FLIP_CANDIDATES_S = {300.0, 120.0, 60.0};
  private static final double FLOW_LOW = 0.99;
  private static final double FLOW_HIGH = 1.01;
  private static final double RENDER_THREAD_BUDGET_MS = 16.0;
  private static final int WARM_REPETITIONS = 10;
  private static final double[] LIFTOFF_OFFSETS_S = {0.0, 1.0, 5.0, 10.0, 30.0};
  private static final double INTERNAL_COAST_MIN_S = 1.0;

  private static final String HOHMANN_LOGGER =
      "com.smousseur.orbitlab.simulation.mission.stage.AnalyticHohmannTransferStage";
  private static final String PARKING_LOGGER =
      "com.smousseur.orbitlab.simulation.mission.stage.AnalyticParkingInsertionStage";
  private static final String TRIM_LOGGER =
      "com.smousseur.orbitlab.simulation.mission.stage.AnalyticTrimBurnStage";
  private static final String GENERATOR_LOGGER =
      "com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator";
  private static final String SIZING_LOGGER =
      "com.smousseur.orbitlab.simulation.mission.planner.MeasuredLoadPlanner";

  private static final Pattern GENERATING =
      Pattern.compile("Generating ephemeris for stage '(.+)'");
  private static final Pattern STAGE_END =
      Pattern.compile("Stage '(.+)': \\d+ points, ended at .*");
  private static final String NUMBER = "(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)";
  private static final Pattern DV1_DT1 =
      Pattern.compile("dv1=" + NUMBER + " m/s(?:, dt1=| over )" + NUMBER + "s");
  private static final Pattern DV2_DT2 =
      Pattern.compile("dv2=" + NUMBER + " m/s(?:, dt2=| over )" + NUMBER + "s");
  private static final Pattern CIRCULAR_TRIM =
      Pattern.compile("dv1=" + NUMBER + " m/s at mean perigee, dv2=" + NUMBER + " m/s");

  private static AbsoluteDate epoch;
  private static LogCapture capture;

  private final List<String> report = new ArrayList<>();
  private final List<MeasuredBurn> allBurns = new ArrayList<>();
  private final Map<String, Double> warmMediansMs = new LinkedHashMap<>();
  private final List<String> failures = new ArrayList<>();
  private final List<String> uncertainPlans = new ArrayList<>();

  /** One burn of one mission, with what the verdicts read of it. */
  private record MeasuredBurn(
      String mission,
      String stage,
      BurnTimeline.Burn burn,
      boolean retrogradeByConstruction,
      double gapBeforeS,
      double gapAfterS) {}

  // PMD.CloseResource: the logger context is the process-wide one, borrowed and never owned here.
  // Closing it would shut logging down for every class that runs after this probe.
  @BeforeAll
  @SuppressWarnings("PMD.CloseResource")
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    capture = new LogCapture();
    capture.start();
    LoggerContext context = (LoggerContext) LogManager.getContext(false);
    Configuration configuration = context.getConfiguration();
    configuration.addAppender(capture);
    configuration.getRootLogger().addAppender(capture, Level.INFO, null);
    context.updateLoggers();
  }

  // PMD.CloseResource: see init — the shared context is borrowed.
  @AfterAll
  @SuppressWarnings("PMD.CloseResource")
  static void detach() {
    LoggerContext context = (LoggerContext) LogManager.getContext(false);
    context.getConfiguration().getRootLogger().removeAppender(capture.getName());
    context.updateLoggers();
    capture.stop();
  }

  @Test
  void burnsOfProductionMissions() throws IOException {
    flyEarth("FH LEO 400", MissionType.LEO, leoValues(Launchers.FALCON_HEAVY), true);
    flyEarth("A64 LEO 400", MissionType.LEO, leoValues(Launchers.ARIANE_64), true);
    Map<String, Object> deorbit = leoValues(Launchers.FALCON_HEAVY);
    deorbit.put("DEORBIT", Boolean.TRUE);
    flyEarth("FH LEO 400 DEORBIT", MissionType.LEO, deorbit, false);
    Map<String, Object> polar = leoValues(Launchers.ARIANE_64);
    polar.put("TARGET_INCLINATION", 97.5);
    flyEarth("A64 LEO 400 i=97.5", MissionType.LEO, polar, false);
    flyEarth("FH GEO", MissionType.GEO, geoValues(Launchers.FALCON_HEAVY), false);
    flyEarth("A64 GEO", MissionType.GEO, geoValues(Launchers.ARIANE_64), false);
    flyLunarOrbit("FH LUNAR ORBIT 100");
    printVerdicts();
    Path out = Path.of("build/fx3-burn-baseline.txt");
    Files.createDirectories(out.getParent());
    Files.write(out, report, StandardCharsets.UTF_8);
  }

  private void flyEarth(
      String label, MissionType type, Map<String, Object> values, boolean liftoff) {
    capture.clear();
    long started = System.nanoTime();
    MissionComputeResult result;
    try {
      MissionEntry entry = new MissionEntry(MissionFactory.specFromWizardValues(values, type));
      requireFast(entry);
      result = new MissionPlanOptimizer(entry, epoch).compute().computation();
    } catch (RuntimeException e) {
      line("%n=== %s — compute() FAILED: %s", label, e);
      failures.add(label);
      return;
    }
    measure(label, result, (System.nanoTime() - started) / 1e9, liftoff);
  }

  private void flyLunarOrbit(String label) {
    capture.clear();
    Map<String, Object> values =
        commonValues(Launchers.FALCON_HEAVY, Payloads.LUNAR_ORBITER, LUNAR_PAYLOAD_MASS_KG);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    long started = System.nanoTime();
    MissionComputeResult result;
    try {
      MissionEntry entry =
          new MissionEntry(MissionScheduler.unplannedSpec(values, MissionType.LUNAR_ORBIT));
      AbsoluteDate floor = new AbsoluteDate(LUNAR_FLOOR, TimeScalesFactory.getUTC());
      MissionScheduler.Schedule schedule = MissionScheduler.schedule(entry, values, floor);
      if (schedule.refusal() != null) {
        line("%n=== %s — no launch window: %s", label, schedule.refusal());
        failures.add(label);
        return;
      }
      requireFast(entry);
      result = new MissionPlanOptimizer(entry, schedule.date()).compute().computation();
    } catch (RuntimeException e) {
      line("%n=== %s — compute() FAILED: %s", label, e);
      failures.add(label);
      return;
    }
    measure(label, result, (System.nanoTime() - started) / 1e9, false);
  }

  private static void requireFast(MissionEntry entry) {
    if (entry.getOptimizationType() != OptimizationType.FAST) {
      throw new IllegalStateException(
          "the entry composed " + entry.getOptimizationType() + " instead of FAST");
    }
  }

  private void measure(
      String label, MissionComputeResult result, double computeSeconds, boolean liftoff) {
    MissionEphemeris ephemeris = result.ephemeris();
    Vehicle vehicle = result.mission().getVehicle();
    PlanLog plans = PlanLog.read(capture.snapshot());
    if (plans.sizingFellBack()) {
      uncertainPlans.add(label);
    }
    line(
        "%n=== %s — compute() %.1f s, %d points, ephemeris %s%s",
        label,
        computeSeconds,
        ephemeris.size(),
        ephemeris.isComplete() ? "complete" : "INCOMPLETE",
        plans.sizingFellBack()
            ? ", SIZING FELL BACK ON AN EARLIER PASS: logged plans uncertain"
            : "");

    double[] costs = timeConstruction(ephemeris, vehicle);
    line(
        "  BurnTimeline.of: first call %.2f ms, warm median %.2f ms over %d",
        costs[0], costs[1], WARM_REPETITIONS);
    warmMediansMs.put(label, costs[1]);

    List<BurnTimeline.Burn> burns = BurnTimeline.of(ephemeris, vehicle).burns();
    AbsoluteDate start = ephemeris.startDate();
    line(
        "  %-3s %-24s %-7s %11s %11s %9s %9s %11s %8s %13s %10s %10s %s",
        "#",
        "stage",
        "role",
        "T+start",
        "T+end",
        "dur s",
        "dv m/s",
        "dE J/kg",
        "cos",
        "flow in",
        "gap bef s",
        "gap aft s",
        "class");
    Map<String, Integer> orderInStage = new HashMap<>();
    for (int i = 0; i < burns.size(); i++) {
      BurnTimeline.Burn burn = burns.get(i);
      AbsoluteDate middle = burn.start().shiftedBy(0.5 * burn.end().durationFrom(burn.start()));
      String stage = ephemeris.interpolate(middle).stageName();
      int order = orderInStage.merge(stage, 1, Integer::sum) - 1;
      boolean byConstruction = plans.retrogradeByConstruction(stage, order);
      double gapBefore =
          i == 0
              ? burn.start().durationFrom(start)
              : burn.start().durationFrom(burns.get(i - 1).end());
      double gapAfter =
          i == burns.size() - 1
              ? ephemeris.endDate().durationFrom(burn.end())
              : burns.get(i + 1).start().durationFrom(burn.end());
      allBurns.add(new MeasuredBurn(label, stage, burn, byConstruction, gapBefore, gapAfter));
      line(
          "  %-3d %-24s %-7s %11.2f %11.2f %9.2f %9.2f %11.4g %8.4f %13s %10.1f %10.1f %s%s",
          i,
          stage,
          roleOf(burn),
          burn.start().durationFrom(start),
          burn.end().durationFrom(start),
          burn.end().durationFrom(burn.start()),
          burn.deltaV(),
          burn.deltaEnergy(),
          burn.cosTheta(),
          burn.hasInteriorFlow()
              ? String.format(
                  Locale.ROOT,
                  "%.4f-%.4f",
                  burn.minInteriorFlowRatio(),
                  burn.maxInteriorFlowRatio())
              : "(edges only)",
          gapBefore,
          gapAfter,
          burn.cosTheta() < RETROGRADE_THRESHOLD ? "RETRO" : "pro",
          byConstruction ? " [retrograde by construction]" : "");
    }
    printInternalCoasts(ephemeris);
    if (liftoff) {
      printLiftoff(ephemeris);
    }
  }

  private static double[] timeConstruction(MissionEphemeris ephemeris, Vehicle vehicle) {
    long cold = System.nanoTime();
    BurnTimeline.of(ephemeris, vehicle);
    double coldMs = (System.nanoTime() - cold) / 1e6;
    double[] warm = new double[WARM_REPETITIONS];
    for (int i = 0; i < WARM_REPETITIONS; i++) {
      long t0 = System.nanoTime();
      BurnTimeline.of(ephemeris, vehicle);
      warm[i] = (System.nanoTime() - t0) / 1e6;
    }
    Arrays.sort(warm);
    return new double[] {
      coldMs, 0.5 * (warm[WARM_REPETITIONS / 2 - 1] + warm[WARM_REPETITIONS / 2])
    };
  }

  /** Stretches inside a propulsive stage where the mass holds while time passes. */
  private void printInternalCoasts(MissionEphemeris ephemeris) {
    List<MissionEphemerisPoint> points = ephemeris.allPoints();
    AbsoluteDate start = ephemeris.startDate();
    line("  internal coasts of propulsive stages (>= %.0f s):", INTERNAL_COAST_MIN_S);
    int shorter = 0;
    int k = 0;
    while (k + 1 < points.size()) {
      if (!coastsInsidePropulsiveStage(points, k)) {
        k++;
        continue;
      }
      MissionEphemerisPoint first = points.get(k);
      while (k + 1 < points.size()
          && coastsInsidePropulsiveStage(points, k)
          && points.get(k).stageName().equals(first.stageName())) {
        k++;
      }
      double seconds = points.get(k).time().durationFrom(first.time());
      if (seconds >= INTERNAL_COAST_MIN_S) {
        line(
            "    %-24s T+%11.2f  %10.1f s",
            first.stageName(), first.time().durationFrom(start), seconds);
      } else {
        shorter++;
      }
    }
    line("    (%d shorter stretch(es) not listed)", shorter);
  }

  private static boolean coastsInsidePropulsiveStage(List<MissionEphemerisPoint> points, int k) {
    MissionEphemerisPoint a = points.get(k);
    MissionEphemerisPoint b = points.get(k + 1);
    return a.propulsive() && b.time().durationFrom(a.time()) > 0.0 && a.mass() - b.mass() <= 0.0;
  }

  /**
   * The angle between the drawn nose — the inertial velocity, which the spacecraft presenter looks
   * along — and the local vertical, in the first seconds of the flight.
   */
  private void printLiftoff(MissionEphemeris ephemeris) {
    line("  liftoff: angle nose (inertial velocity) to local vertical");
    for (double offset : LIFTOFF_OFFSETS_S) {
      MissionEphemerisPoint p = ephemeris.displayPointAt(ephemeris.startDate().shiftedBy(offset));
      double cos = p.velocity().normalize().dotProduct(p.position().normalize());
      line(
          "    T+%4.0f s  |v| %8.1f m/s  angle to vertical %6.2f deg  altitude %8.1f m",
          offset, p.velocity().getNorm(), Math.toDegrees(Math.acos(cos)), p.altitudeMeters());
    }
  }

  private void printVerdicts() {
    line("%n=== VERDICTS (rules fixed before the measurement)");
    if (!failures.isEmpty()) {
      line("  missions that did not fly: %s", failures);
    }
    if (!uncertainPlans.isEmpty()) {
      line("  missions whose logged plans are uncertain (sizing fell back): %s", uncertainPlans);
    }

    double worstRetrograde = Double.NEGATIVE_INFINITY;
    double worstOther = Double.POSITIVE_INFINITY;
    int retrogradeCount = 0;
    for (MeasuredBurn m : allBurns) {
      if (m.retrogradeByConstruction()) {
        retrogradeCount++;
        worstRetrograde = Math.max(worstRetrograde, m.burn().cosTheta());
      } else {
        worstOther = Math.min(worstOther, m.burn().cosTheta());
      }
    }
    boolean v1 = worstRetrograde < RETROGRADE_CEILING && worstOther > OTHERS_FLOOR;
    line(
        "  V1 threshold: %d retrograde-by-construction burn(s), highest cos %.4f (rule < %.1f);"
            + " lowest cos of the others %.4f (rule > %.1f) -> %s",
        retrogradeCount,
        worstRetrograde,
        RETROGRADE_CEILING,
        worstOther,
        OTHERS_FLOOR,
        v1 ? "-0.5 RETAINED" : "BACK TO THE DECISION before L1");
    for (MeasuredBurn m : allBurns) {
      boolean offending =
          m.retrogradeByConstruction()
              ? m.burn().cosTheta() >= RETROGRADE_CEILING
              : m.burn().cosTheta() <= OTHERS_FLOOR;
      if (offending) {
        line("    offending: %s / %s cos %.4f", m.mission(), m.stage(), m.burn().cosTheta());
      }
    }

    double shortestGap = Double.POSITIVE_INFINITY;
    for (MeasuredBurn m : allBurns) {
      if (m.burn().cosTheta() < RETROGRADE_THRESHOLD) {
        shortestGap = Math.min(shortestGap, m.gapBeforeS());
        line(
            "    retrograde %s / %s: gap before %.1f s, after %.1f s",
            m.mission(), m.stage(), m.gapBeforeS(), m.gapAfterS());
      }
    }
    double flip = Double.NaN;
    for (double candidate : FLIP_CANDIDATES_S) {
      if (shortestGap >= 2.0 * candidate) {
        flip = candidate;
        break;
      }
    }
    line(
        "  V2 x: shortest gap before a retrograde burn %.1f s -> x = %s",
        shortestGap,
        Double.isNaN(flip)
            ? "NONE OF 300/120/60 FITS"
            : String.format(Locale.ROOT, "%.0f s", flip));

    double lowestFlow = Double.POSITIVE_INFINITY;
    double highestFlow = Double.NEGATIVE_INFINITY;
    int withInterior = 0;
    for (MeasuredBurn m : allBurns) {
      if (m.burn().hasInteriorFlow()) {
        withInterior++;
        lowestFlow = Math.min(lowestFlow, m.burn().minInteriorFlowRatio());
        highestFlow = Math.max(highestFlow, m.burn().maxInteriorFlowRatio());
      }
    }
    boolean v3 = lowestFlow >= FLOW_LOW && highestFlow <= FLOW_HIGH;
    line(
        "  V3 flow: interior flow over %d burn(s) in [%.4f ; %.4f] (rule [%.2f ; %.2f]) -> %s",
        withInterior,
        lowestFlow,
        highestFlow,
        FLOW_LOW,
        FLOW_HIGH,
        v3 ? "CONSTANT, D1 holds" : "A THROTTLE EXISTS, D1 to revisit");

    double worstMedian =
        warmMediansMs.values().stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    line(
        "  V4 cost: worst warm median %.2f ms (budget %.0f ms) -> %s; per mission %s",
        worstMedian,
        RENDER_THREAD_BUDGET_MS,
        worstMedian <= RENDER_THREAD_BUDGET_MS ? "RENDER THREAD in L1" : "EXECUTOR in L1",
        warmMediansMs);

    List<String> launcherRetrogrades = new ArrayList<>();
    for (MeasuredBurn m : allBurns) {
      boolean retrograde =
          m.retrogradeByConstruction() || m.burn().cosTheta() < RETROGRADE_THRESHOLD;
      if (retrograde && m.burn().role() != StageRole.KICK) {
        launcherRetrogrades.add(m.mission() + " / " + m.stage() + " (" + roleOf(m.burn()) + ")");
      }
    }
    line(
        "  V5 upper-stage retrograde: %s",
        launcherRetrogrades.isEmpty()
            ? "NONE - every retrograde burn is the payload's; the L3/L4 order is to be raised"
            : "yes: " + launcherRetrogrades);
  }

  private static String roleOf(BurnTimeline.Burn burn) {
    return burn.role() == null ? "-" : burn.role().name();
  }

  private void line(String format, Object... args) {
    String text = String.format(Locale.ROOT, format, args);
    System.out.println(text);
    report.add(text);
  }

  private static Map<String, Object> commonValues(
      LauncherModel launcher, PayloadModel payload, double payloadMass) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "FX-3 burn probe");
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", 5.23);
    values.put("LAUNCH_SITE_LONG", -52.77);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", launcher.id());
    values.put("PAYLOAD_TYPE", payload.id());
    values.put("PAYLOAD_MASS", payloadMass);
    return values;
  }

  private static Map<String, Object> leoValues(LauncherModel launcher) {
    PayloadModel payload = Payloads.EARTH_OBSERVATION_SAT;
    Map<String, Object> values = commonValues(launcher, payload, payload.defaultDryMass());
    values.put("LEO_PERIGEE_ALT", ORBIT_ALTITUDE_KM);
    values.put("LEO_APOGEE_ALT", ORBIT_ALTITUDE_KM);
    return values;
  }

  private static Map<String, Object> geoValues(LauncherModel launcher) {
    PayloadModel payload = Payloads.GEO_SAT;
    Map<String, Object> values = commonValues(launcher, payload, payload.defaultDryMass());
    values.put("GTO_PARKING_ALT", ORBIT_ALTITUDE_KM);
    return values;
  }

  /**
   * The plans the conditional stages logged while the retained pass's ephemeris was generated, and
   * whether the sizing kept an earlier pass than the last.
   */
  private record PlanLog(Map<String, List<Boolean>> retrogradeFlags, boolean sizingFellBack) {

    static PlanLog read(List<String[]> events) {
      Map<String, List<Boolean>> flags = new HashMap<>();
      boolean fellBack = false;
      String generating = null;
      for (String[] event : events) {
        String logger = event[0];
        String message = event[1];
        if (SIZING_LOGGER.equals(logger) && message.startsWith("Sizing did not converge")) {
          fellBack = true;
        }
        if (GENERATOR_LOGGER.equals(logger)) {
          Matcher begins = GENERATING.matcher(message);
          if (begins.matches()) {
            generating = begins.group(1);
          } else if (STAGE_END.matcher(message).matches()) {
            generating = null;
          }
          continue;
        }
        if (generating == null) {
          continue;
        }
        String stage = generating;
        parse(logger, message).ifPresent(parsed -> flags.put(stage, parsed));
      }
      return new PlanLog(flags, fellBack);
    }

    /**
     * One flag per burn the plan fires, in order: true when it brakes. Empty when the message is
     * not a plan, which differs from a plan that fires nothing.
     */
    private static Optional<List<Boolean>> parse(String logger, String message) {
      if (HOHMANN_LOGGER.equals(logger) && message.startsWith("Analytic Hohmann, descending")) {
        return Optional.of(List.of(true));
      }
      if ((HOHMANN_LOGGER.equals(logger) && message.startsWith("Analytic Hohmann plan"))
          || (PARKING_LOGGER.equals(logger) && message.startsWith("Analytic parking plan"))) {
        List<Boolean> burns = new ArrayList<>();
        addIfFired(burns, DV1_DT1.matcher(message));
        addIfFired(burns, DV2_DT2.matcher(message));
        return Optional.of(burns);
      }
      if (TRIM_LOGGER.equals(logger) && message.startsWith("Circular trim (mean Hohmann)")) {
        Matcher m = CIRCULAR_TRIM.matcher(message);
        if (!m.find()) {
          return Optional.empty();
        }
        List<Boolean> burns = new ArrayList<>();
        for (int group = 1; group <= 2; group++) {
          double dv = Double.parseDouble(m.group(group));
          if (dv != 0.0) {
            burns.add(dv < 0.0);
          }
        }
        return Optional.of(burns);
      }
      return Optional.empty();
    }

    private static void addIfFired(List<Boolean> burns, Matcher m) {
      if (m.find() && Double.parseDouble(m.group(2)) > 0.0) {
        burns.add(Double.parseDouble(m.group(1)) < 0.0);
      }
    }

    boolean retrogradeByConstruction(String stage, int order) {
      if (StageNames.DEORBIT_BURN.equals(stage) || LunarOrbitMission.INSERTION_NAME.equals(stage)) {
        return true;
      }
      List<Boolean> stageFlags = retrogradeFlags.get(stage);
      return stageFlags != null && order < stageFlags.size() && stageFlags.get(order);
    }
  }

  /** Keeps the logger name and the formatted message of every event, in arrival order. */
  private static final class LogCapture extends AbstractAppender {

    private final List<String[]> events = Collections.synchronizedList(new ArrayList<>());

    LogCapture() {
      super("fx3-burn-probe", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(new String[] {event.getLoggerName(), event.getMessage().getFormattedMessage()});
    }

    void clear() {
      events.clear();
    }

    List<String[]> snapshot() {
      synchronized (events) {
        return new ArrayList<>(events);
      }
    }
  }
}
