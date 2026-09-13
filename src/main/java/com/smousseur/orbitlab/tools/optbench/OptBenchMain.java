package com.smousseur.orbitlab.tools.optbench;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlan;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.planner.PropellantSizing;
import com.smousseur.orbitlab.simulation.mission.runtime.AchievedOrbit;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPerformanceReport;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * Standalone benchmark for the trajectory optimizer ({@code OPT-1 / L0}, spec {@code
 * docs/optimization/02-conception-L0.md}). It flies each reference mission of the matrix through the
 * <em>same</em> path the application uses — {@link MissionFactory#specFromWizardValues} then {@link
 * MissionPlanOptimizer#compute()} — while timing it, and writes a Markdown report ready to seed
 * {@code 03-baseline-L0.md}.
 *
 * <p><b>It changes nothing in {@code src/main}.</b> The phase breakdown is reconstructed from the
 * {@link BenchProgressListener} attached to the optimizer (cold event timeline + hot evaluation
 * count) and from a per-cell JFR recording, both of which the production code already exposes.
 *
 * <p><b>Run it outside the jacoco-instrumented {@code Test} tasks</b> (fiche §Pièges de mesure): a
 * {@code JavaExec} Gradle task or an IDE launch, on JDK 21. A run is long — a PRECISE cell alone is
 * tens of minutes — so it is the user's to launch, not a CI step.
 *
 * <p>The one thread whose stack samples are the search is the exploration pool; the calc thread is
 * renamed {@code optbench-calc} so the JFR split can tell the two apart (see {@link
 * #classifyThread}).
 *
 * <pre>
 *   OptBenchMain [outputDir] [cellFilter...]   # outputDir defaults to ./optbench-out
 * </pre>
 *
 * <p>Each {@code cellFilter} is a case-insensitive substring of a cell id; only cells matching at
 * least one filter run, and a run with filters writes {@code baseline-L0-<filters>.md} so it never
 * overwrites the full baseline. {@code optBench --args="optbench-out GEO"} flies only the GEO cell.
 */
public final class OptBenchMain {

  private static final Logger logger = LogManager.getLogger(OptBenchMain.class);

  /** The calc thread's name, so JFR execution samples on it separate from the exploration pool. */
  private static final String CALC_THREAD_NAME = "optbench-calc";

  /** Kourou, the site the PHY-2 reference measurements were flown from. */
  private static final double SITE_LAT = 5.23;

  private static final double SITE_LON = -52.77;
  private static final double SITE_ALT = 0.0;

  /** The 10 t Earth-observation payload of the reference — not {@code Spacecraft.LEGACY} (150 kg). */
  private static final double REFERENCE_PAYLOAD_MASS = 10_000.0;

  private OptBenchMain() {}

  /**
   * Runs the benchmark matrix and writes the report.
   *
   * @param args optional single argument: the output directory (default {@code ./optbench-out})
   * @throws Exception if Orekit initialization or the report write fails
   */
  public static void main(String[] args) throws Exception {
    Thread.currentThread().setName(CALC_THREAD_NAME);
    OrekitService.get().initialize();

    Path outputDir = Path.of(args.length > 0 ? args[0] : "optbench-out");
    Files.createDirectories(outputDir);
    List<String> filters =
        args.length > 1 ? List.of(Arrays.copyOfRange(args, 1, args.length)) : List.of();

    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    BenchProgressListener listener = new BenchProgressListener();

    List<CellResult> results = new ArrayList<>();
    for (Cell cell : matrix()) {
      if (!matches(cell, filters)) {
        continue;
      }
      logger.info("Running cell {} ({} / {})...", cell.id(), cell.type(), cell.mode());
      CellResult result = runCell(cell, epoch, listener, outputDir);
      results.add(result);
      if (result.ok()) {
        logger.info(
            "Cell {} done in {} s ({} flights, {} evaluations)",
            cell.id(),
            String.format(Locale.ROOT, "%.1f", result.wallSeconds()),
            result.flights(),
            result.evaluations());
      } else {
        logger.warn("Cell {} FAILED after {} s: {}", cell.id(), (long) result.wallSeconds(), result.failure());
      }
    }

    String reportName =
        filters.isEmpty() ? "baseline-L0.md" : "baseline-L0-" + String.join("-", filters) + ".md";
    Path report = outputDir.resolve(reportName);
    Files.writeString(report, renderReport(results, epoch));
    logger.info("Report written to {}", report.toAbsolutePath());
  }

  // ── Reference matrix ──────────────────────────────────────────────────────

  /** One benchmark cell: a mission built from wizard values, flown in one optimization mode. */
  private record Cell(String id, MissionType type, OptimizationType mode, Map<String, Object> values) {}

  /**
   * The reference matrix of {@code 02-conception-L0.md} §2: Falcon Heavy + 10 t @ 400 km in the
   * three modes, a GEO FAST, and the symptomatic Ariane 64 sizing case in FAST.
   */
  private static List<Cell> matrix() {
    String fh = Launchers.FALCON_HEAVY.id();
    String a64 = Launchers.ARIANE_64.id();
    return List.of(
        new Cell("FH_LEO400_FAST", MissionType.LEO, OptimizationType.FAST, leo("FH LEO400", fh, 400.0)),
        new Cell(
            "FH_LEO400_BALANCED", MissionType.LEO, OptimizationType.BALANCED, leo("FH LEO400", fh, 400.0)),
        new Cell(
            "FH_LEO400_PRECISE", MissionType.LEO, OptimizationType.PRECISE, leo("FH LEO400", fh, 400.0)),
        new Cell("GEO_SAT_FAST", MissionType.GEO, OptimizationType.FAST, geo("GEO SAT", fh, 400.0)),
        new Cell(
            "ARIANE64_LEO400_FAST", MissionType.LEO, OptimizationType.FAST, leo("A64 LEO400", a64, 400.0)));
  }

  /** Wizard values for a circular LEO mission carrying the 10 t observation satellite. */
  private static Map<String, Object> leo(String name, String launcherId, double altitudeKm) {
    Map<String, Object> values = site(name, launcherId, Payloads.EARTH_OBSERVATION_SAT.id());
    values.put("PAYLOAD_MASS", REFERENCE_PAYLOAD_MASS);
    values.put("LEO_PERIGEE_ALT", altitudeKm);
    values.put("LEO_APOGEE_ALT", altitudeKm);
    return values;
  }

  /** Wizard values for a GEO mission; {@code PAYLOAD_MASS = 0} takes the payload's default mass. */
  private static Map<String, Object> geo(String name, String launcherId, double parkingKm) {
    Map<String, Object> values = site(name, launcherId, Payloads.GEO_SAT.id());
    values.put("PAYLOAD_MASS", 0.0);
    values.put("GTO_PARKING_ALT", parkingKm);
    return values;
  }

  private static Map<String, Object> site(String name, String launcherId, String payloadId) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("MISSION_NAME", name);
    values.put("LAUNCH_SITE_NAME", "Kourou");
    values.put("LAUNCH_SITE_LAT", SITE_LAT);
    values.put("LAUNCH_SITE_LONG", SITE_LON);
    values.put("LAUNCH_SITE_ALT", SITE_ALT);
    values.put("LAUNCHER_TYPE", launcherId);
    values.put("PAYLOAD_TYPE", payloadId);
    return values;
  }

  /** Whether a cell passes the id substring filters; an empty filter list runs everything. */
  private static boolean matches(Cell cell, List<String> filters) {
    if (filters.isEmpty()) {
      return true;
    }
    String id = cell.id().toLowerCase(Locale.ROOT);
    return filters.stream().anyMatch(filter -> id.contains(filter.toLowerCase(Locale.ROOT)));
  }

  // ── Running one cell ──────────────────────────────────────────────────────

  /** Everything one cell produced, or the failure it raised. Numeric fields are 0 on failure. */
  private record CellResult(
      Cell cell,
      boolean ok,
      String failure,
      double wallSeconds,
      long evaluations,
      int flights,
      Integer sizingPasses,
      double[] lambdas,
      String orbitOsculating,
      String orbitMean,
      double totalDeltaV,
      double residualKg,
      double residualRatio,
      List<BenchProgressListener.TimelineEntry> timeline,
      Path jfr,
      Map<String, Long> jfrSamplesByBucket) {}

  private static CellResult runCell(
      Cell cell, AbsoluteDate epoch, BenchProgressListener listener, Path outputDir) {
    listener.reset();
    Path jfr = outputDir.resolve(cell.id() + ".jfr");
    Recording recording = startRecording(jfr);
    long start = System.nanoTime();
    try {
      MissionSpec spec = MissionFactory.specFromWizardValues(cell.values(), cell.type());
      MissionEntry entry = new MissionEntry(spec);
      entry.setOptimizationType(cell.mode());

      MissionPlan plan = new MissionPlanOptimizer(entry, epoch, listener).compute();

      double wall = (System.nanoTime() - start) / 1e9;
      stopRecording(recording);

      MissionComputeResult computation = plan.computation();
      MissionPerformanceReport report = computation.performanceReport();
      AchievedOrbit orbit = computation.achievedOrbit();
      PropellantSizing sizing = plan.sizing();

      return new CellResult(
          cell,
          true,
          null,
          wall,
          listener.evaluations(),
          listener.flights(),
          sizing != null ? sizing.passes() : null,
          sizing != null ? sizing.lambdas() : null,
          orbit.formatOsculating(),
          orbit.formatMean(),
          report.totalDeltaV(),
          report.totalPropellantResidual(),
          report.residualRatio(),
          listener.timeline(),
          jfr,
          samplesByBucket(jfr));
    } catch (Exception e) {
      double wall = (System.nanoTime() - start) / 1e9;
      stopRecording(recording);
      return new CellResult(
          cell, false, e.toString(), wall, listener.evaluations(), listener.flights(), null, null,
          null, null, 0.0, 0.0, 0.0, listener.timeline(), jfr, samplesByBucket(jfr));
    }
  }

  // ── JFR ───────────────────────────────────────────────────────────────────

  /** Starts a per-cell execution-sample recording, or {@code null} when JFR is unavailable. */
  private static Recording startRecording(Path destination) {
    try {
      Recording recording = new Recording();
      recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
      recording.setToDisk(true);
      recording.setDestination(destination);
      recording.start();
      return recording;
    } catch (Exception e) {
      logger.warn("JFR unavailable, continuing without a recording: {}", e.toString());
      return null;
    }
  }

  private static void stopRecording(Recording recording) {
    if (recording == null) {
      return;
    }
    try {
      recording.stop();
      recording.close();
    } catch (Exception e) {
      logger.warn("JFR stop failed: {}", e.toString());
    }
  }

  /**
   * Aggregates execution samples of the recording by thread bucket, giving a first-order split
   * between the exploration search (pool threads) and the calc thread (analytic propagation,
   * ephemeris generation, and any main-thread refinement). Precise method-level attribution is left
   * to offline analysis of the {@code .jfr} itself.
   */
  private static Map<String, Long> samplesByBucket(Path jfr) {
    Map<String, Long> byBucket = new LinkedHashMap<>();
    if (!Files.exists(jfr)) {
      return byBucket;
    }
    try (RecordingFile file = new RecordingFile(jfr)) {
      while (file.hasMoreEvents()) {
        RecordedEvent event = file.readEvent();
        if (!"jdk.ExecutionSample".equals(event.getEventType().getName())) {
          continue;
        }
        byBucket.merge(classifyThread(sampledThreadName(event)), 1L, Long::sum);
      }
    } catch (Exception e) {
      logger.warn("JFR parse failed for {}: {}", jfr, e.toString());
    }
    return byBucket;
  }

  private static String sampledThreadName(RecordedEvent event) {
    try {
      RecordedThread thread = event.getThread("sampledThread");
      if (thread == null) {
        thread = event.getThread();
      }
      return thread == null ? null : thread.getJavaName();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String classifyThread(String threadName) {
    if (threadName == null) {
      return "other";
    }
    if (CALC_THREAD_NAME.equals(threadName)) {
      return "calc (propagation + serial refinement)";
    }
    if (threadName.startsWith("cmaes-eval")) {
      return "search (generation pool)";
    }
    if (threadName.startsWith("pool-") || threadName.contains("ForkJoinPool")) {
      return "search (exploration runs)";
    }
    return "other";
  }

  // ── Report ─────────────────────────────────────────────────────────────────

  private static String renderReport(List<CellResult> results, AbsoluteDate epoch) {
    StringBuilder md = new StringBuilder();
    md.append("# OPT-1 / L0 — baseline mesurée (brut du banc)\n\n");
    md.append("Produit par `tools/optbench/OptBenchMain`. À relire, puis reporter dans ")
        .append("`03-baseline-L0.md`.\n\n");
    md.append("- Machine : ")
        .append(Runtime.getRuntime().availableProcessors())
        .append(" processeurs logiques\n");
    md.append("- JVM : ")
        .append(System.getProperty("java.version"))
        .append(" (")
        .append(System.getProperty("java.vm.name"))
        .append(")\n");
    md.append("- OS : ").append(System.getProperty("os.name")).append('\n');
    md.append("- Époque de lancement : ").append(epoch).append('\n');
    md.append("- Atmosphère : NRLMSISE (défaut `MissionFactory`), drag-on\n\n");

    md.append("## Synthèse\n\n");
    md.append("| Cellule | Mode | Wall (s) | Vols | Évals | Résidu | CPU recherche |\n");
    md.append("|---|---|---:|---:|---:|---:|---:|\n");
    for (CellResult r : results) {
      md.append("| ")
          .append(r.cell().id())
          .append(" | ")
          .append(r.cell().mode())
          .append(" | ")
          .append(r.ok() ? String.format(Locale.ROOT, "%.1f", r.wallSeconds()) : "— échec")
          .append(" | ")
          .append(r.ok() ? Integer.toString(r.flights()) : "—")
          .append(" | ")
          .append(r.evaluations())
          .append(" | ")
          .append(r.ok() ? String.format(Locale.ROOT, "%.1f %%", 100.0 * r.residualRatio()) : "—")
          .append(" | ")
          .append(searchCpuShare(r))
          .append(" |\n");
    }
    md.append('\n');

    for (CellResult r : results) {
      renderCell(md, r);
    }
    return md.toString();
  }

  private static void renderCell(StringBuilder md, CellResult r) {
    md.append("## ").append(r.cell().id()).append('\n');
    md.append("- Type / mode : ").append(r.cell().type()).append(" / ").append(r.cell().mode()).append('\n');
    md.append("- Lanceur : ").append(r.cell().values().get("LAUNCHER_TYPE"));
    md.append(", charge utile : ").append(r.cell().values().get("PAYLOAD_TYPE")).append('\n');
    if (!r.ok()) {
      md.append("- **Échec** après ")
          .append(String.format(Locale.ROOT, "%.1f", r.wallSeconds()))
          .append(" s : `")
          .append(r.failure())
          .append("`\n\n");
      return;
    }
    md.append("- Wall-clock : **").append(String.format(Locale.ROOT, "%.1f", r.wallSeconds())).append(" s**\n");
    md.append("- Vols de dimensionnement : ").append(r.flights());
    if (r.sizingPasses() != null) {
      md.append(" (sizing.passes = ").append(r.sizingPasses()).append(')');
    }
    md.append('\n');
    md.append("- Évaluations (cumul tous vols) : ").append(r.evaluations()).append('\n');
    if (r.lambdas() != null) {
      md.append("- λ par étage : ").append(formatLambdas(r.lambdas())).append('\n');
    }
    md.append("- Orbite atteinte (osc.) : ").append(r.orbitOsculating()).append('\n');
    md.append("- Orbite atteinte (moy.) : ").append(r.orbitMean()).append('\n');
    md.append("- ΔV total : ")
        .append(String.format(Locale.ROOT, "%.0f m/s", r.totalDeltaV()))
        .append(", résidu ")
        .append(String.format(Locale.ROOT, "%.0f kg (%.1f %%)", r.residualKg(), 100.0 * r.residualRatio()))
        .append('\n');
    md.append("- JFR : `").append(r.jfr().getFileName()).append("` — ").append(bucketBreakdown(r)).append('\n');
    md.append("\n<details><summary>Timeline (").append(r.timeline().size()).append(" événements)</summary>\n\n```\n");
    appendTimeline(md, r.timeline());
    md.append("```\n</details>\n\n");
  }

  /** Full timeline when short, else the head and tail with the middle elided. */
  private static void appendTimeline(StringBuilder md, List<BenchProgressListener.TimelineEntry> timeline) {
    int cap = 80;
    if (timeline.size() <= cap) {
      for (BenchProgressListener.TimelineEntry e : timeline) {
        md.append(formatEntry(e)).append('\n');
      }
      return;
    }
    for (int i = 0; i < 50; i++) {
      md.append(formatEntry(timeline.get(i))).append('\n');
    }
    md.append("... (").append(timeline.size() - 70).append(" événements omis) ...\n");
    for (int i = timeline.size() - 20; i < timeline.size(); i++) {
      md.append(formatEntry(timeline.get(i))).append('\n');
    }
  }

  private static String formatEntry(BenchProgressListener.TimelineEntry e) {
    return String.format(Locale.ROOT, "t=%8.2fs  %s", e.seconds(), e.description());
  }

  private static String searchCpuShare(CellResult r) {
    long total = r.jfrSamplesByBucket().values().stream().mapToLong(Long::longValue).sum();
    if (total == 0) {
      return "—";
    }
    long search = r.jfrSamplesByBucket().getOrDefault("search (exploration pool)", 0L);
    return String.format(Locale.ROOT, "%.0f %%", 100.0 * search / total);
  }

  private static String bucketBreakdown(CellResult r) {
    long total = r.jfrSamplesByBucket().values().stream().mapToLong(Long::longValue).sum();
    if (total == 0) {
      return "pas d'échantillons JFR";
    }
    StringBuilder sb = new StringBuilder();
    r.jfrSamplesByBucket()
        .forEach(
            (bucket, count) -> {
              if (sb.length() > 0) {
                sb.append(", ");
              }
              sb.append(String.format(Locale.ROOT, "%s %.0f %%", bucket, 100.0 * count / total));
            });
    return sb.toString();
  }

  private static String formatLambdas(double[] lambdas) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < lambdas.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(String.format(Locale.ROOT, "%.3f", lambdas[i]));
    }
    return sb.append(']').toString();
  }
}
