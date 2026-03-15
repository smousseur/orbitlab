package com.smousseur.orbitlab.tools.ephemerisgen;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * CLI entry-point to generate the full ephemeris dataset files.
 *
 * <p>Usage:
 *
 * <pre>
 *   EphemerisDatasetGeneratorMain &lt;orekit-data.zip&gt; &lt;outputDir&gt;
 * </pre>
 *
 * <p>Example:
 *
 * <pre>
 *   java -Xmx6g -cp build/libs/orbitlab.jar com.smousseur.orbitlab.tools.ephemerisgen.EphemerisDatasetGeneratorMain \
 *     ./orekit-data.zip ./build/ephemeris-dataset
 * </pre>
 */
public final class EphemerisDatasetGeneratorMain {

  private static final Logger LOG = Logger.getLogger(EphemerisDatasetGeneratorMain.class.getName());

  private EphemerisDatasetGeneratorMain() {}

  /**
   * Entry point for ephemeris dataset generation.
   *
   * <p>Expects exactly two arguments: the path to the Orekit data zip file and the output
   * directory. Configures logging, builds a {@link GeneratorConfigV1} with tuned per-body
   * sampling parameters, and runs the full generation pipeline.
   *
   * @param args command-line arguments: {@code <orekit-data.zip> <outputDir>}
   * @throws Exception if generation fails
   */
  public static void main(String[] args) throws Exception {
    configureLogging();

    if (args.length != 2) {
      System.err.println("Usage: EphemerisDatasetGeneratorMain <orekit-data.zip> <outputDir>");
      System.exit(2);
      return;
    }

    Path orekitZip = Path.of(args[0]).toAbsolutePath().normalize();
    Path outputDir = Path.of(args[1]).toAbsolutePath().normalize();

    if (!Files.isRegularFile(orekitZip)) {
      throw new IllegalArgumentException("orekit-data.zip not found: " + orekitZip);
    }
    Files.createDirectories(outputDir);

    LOG.info(() -> "Starting ephemeris dataset generation");
    LOG.info(() -> "orekitZip = " + orekitZip);
    LOG.info(() -> "outputDir = " + outputDir);

    Instant startedAt = Instant.now();

    GeneratorConfigV1 baseCfg = GeneratorConfigV1.defaultV1(orekitZip, outputDir);

    GeneratorConfigV1 cfg = withPvFirstUnder10GoParams(baseCfg);

    logConfigSummary(cfg);
    logRoughSizeEstimate(cfg);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  Duration elapsed = Duration.between(startedAt, Instant.now());
                  LOG.info(() -> "Shutdown hook: elapsed=" + formatDuration(elapsed));
                },
                "ephemgen-shutdown-hook"));

    new EphemerisDatasetGenerator(cfg).generateAll();

    Duration elapsed = Duration.between(startedAt, Instant.now());
    LOG.info(() -> "Generation complete. elapsed=" + formatDuration(elapsed));
    LOG.info(() -> "Output directory: " + outputDir);
  }

  private static void configureLogging() {
    Logger root = Logger.getLogger("");
    root.setLevel(Level.INFO);

    // Replace default handlers with a single concise console handler.
    for (var h : root.getHandlers()) {
      root.removeHandler(h);
    }

    ConsoleHandler h = new ConsoleHandler();
    h.setLevel(Level.ALL);
    h.setFormatter(
        new Formatter() {
          @Override
          public String format(LogRecord r) {
            // Example: 2026-01-15T12:34:56.789Z INFO  [EphemerisDatasetGeneratorMain] message
            Instant t = Instant.ofEpochMilli(r.getMillis());
            String level = r.getLevel().getName();
            String logger = shortLoggerName(r.getLoggerName());
            String msg = formatMessage(r);
            String thrown =
                (r.getThrown() == null) ? "" : ("\n" + stackTraceToString(r.getThrown()));
            return t + " " + padRight(level, 5) + " [" + logger + "] " + msg + thrown + "\n";
          }
        });

    root.addHandler(h);
  }

  private static GeneratorConfigV1 withPvFirstUnder10GoParams(GeneratorConfigV1 baseCfg) {
    Objects.requireNonNull(baseCfg, "baseCfg");

    double chunkDur = 86_400.0;

    EnumMap<SolarSystemBody, BodyGenerationParams> map = new EnumMap<>(SolarSystemBody.class);

    map.put(SolarSystemBody.SUN, new BodyGenerationParams(1800.0, 3600.0, chunkDur));
    map.put(SolarSystemBody.MERCURY, new BodyGenerationParams(120.0, 900.0, chunkDur));
    map.put(SolarSystemBody.VENUS, new BodyGenerationParams(300.0, 7200.0, chunkDur));
    map.put(SolarSystemBody.EARTH, new BodyGenerationParams(300.0, 600.0, chunkDur));
    map.put(SolarSystemBody.MARS, new BodyGenerationParams(600.0, 600.0, chunkDur));
    map.put(SolarSystemBody.JUPITER, new BodyGenerationParams(900.0, 300.0, chunkDur));
    map.put(SolarSystemBody.SATURN, new BodyGenerationParams(1800.0, 300.0, chunkDur));
    map.put(SolarSystemBody.URANUS, new BodyGenerationParams(3600.0, 900.0, chunkDur));
    map.put(SolarSystemBody.NEPTUNE, new BodyGenerationParams(7200.0, 1800.0, chunkDur));
    map.put(SolarSystemBody.PLUTO, new BodyGenerationParams(14400.0, 7200.0, chunkDur));

    List<SolarSystemBody> bodies =
        List.of(
            SolarSystemBody.SUN,
            SolarSystemBody.MERCURY,
            SolarSystemBody.VENUS,
            SolarSystemBody.EARTH,
            SolarSystemBody.MARS,
            SolarSystemBody.JUPITER,
            SolarSystemBody.SATURN,
            SolarSystemBody.URANUS,
            SolarSystemBody.NEPTUNE,
            SolarSystemBody.PLUTO);

    // Record "copy with modifications": rewrite the config with our params & fixed body order.
    return new GeneratorConfigV1(
        baseCfg.orekitDataZipPath(),
        baseCfg.orekitDataIdSha256Hex(),
        baseCfg.outputDir(),
        bodies,
        baseCfg.datasetStartTaiOffsetSeconds(),
        baseCfg.datasetEndTaiOffsetSecondsExclusive(),
        baseCfg.computeThreads(),
        baseCfg.maxBodiesInParallel(),
        baseCfg.maxChunksInFlightPerBody(),
        baseCfg.maxChunksInFlightGlobal(),
        baseCfg.zstdLevel(),
        map);
  }

  private static void logConfigSummary(GeneratorConfigV1 cfg) {
    LOG.info(() -> "Config:");
    LOG.info(() -> "  bodies            = " + cfg.bodiesInOrder());
    LOG.info(() -> "  computeThreads    = " + cfg.computeThreads());
    LOG.info(() -> "  bodiesInParallel  = " + cfg.maxBodiesInParallel());
    LOG.info(() -> "  chunksInFlight/body   = " + cfg.maxChunksInFlightPerBody());
    LOG.info(() -> "  chunksInFlight/global = " + cfg.maxChunksInFlightGlobal());
    LOG.info(() -> "  zstdLevel         = " + cfg.zstdLevel());
    LOG.info(() -> "  orekitDataId      = sha256:" + cfg.orekitDataIdSha256Hex());
  }

  /**
   * Rough payload-only estimate (ignores headers/index, ignores Zstd compression).
   *
   * <p>This is useful for sanity: if this number is huge, the run can explode disk usage.
   */
  private static void logRoughSizeEstimate(GeneratorConfigV1 cfg) {
    // We don't know your final date range here (it lives in the generator), so we log per-day
    // costs.
    // This still gives a very good "directional" estimate.
    final double secondsPerDay = 86_400.0;

    long bytesPerDayPv = 0L;
    long bytesPerDayRot = 0L;

    for (SolarSystemBody b : cfg.bodiesInOrder()) {
      BodyGenerationParams p = cfg.paramsByBody().get(b);
      if (p == null) continue;

      long pvSamplesPerDay = (long) Math.ceil(secondsPerDay / p.dtPvSeconds());
      long rotSamplesPerDay = (long) Math.ceil(secondsPerDay / p.dtRotSeconds());

      bytesPerDayPv += pvSamplesPerDay * 6L * 8L; // 6 f64
      bytesPerDayRot += rotSamplesPerDay * 4L * 8L; // 4 f64
    }

    final long bytesPerDayPvFinal = bytesPerDayPv;
    final long bytesPerDayRotFinal = bytesPerDayRot;

    double gb = 1_000_000_000.0;
    LOG.info(
        () ->
            String.format(
                Locale.ROOT,
                "Rough payload (uncompressed) per day: PV=%.3f GB/day, ROT=%.3f GB/day, total=%.3f GB/day",
                bytesPerDayPvFinal / gb,
                bytesPerDayRotFinal / gb,
                (bytesPerDayPvFinal + bytesPerDayRotFinal) / gb));
    LOG.info(
        () ->
            "Note: actual size will be smaller due to Zstd (typically), plus a small overhead for headers/index/CRCs.");
  }

  private static String shortLoggerName(String name) {
    if (name == null || name.isBlank()) return "root";
    int idx = name.lastIndexOf('.');
    return (idx < 0) ? name : name.substring(idx + 1);
  }

  private static String padRight(String s, int minLen) {
    if (s.length() >= minLen) return s;
    return s + " ".repeat(minLen - s.length());
  }

  private static String stackTraceToString(Throwable t) {
    StringBuilder sb = new StringBuilder();
    sb.append(t).append('\n');
    for (StackTraceElement e : t.getStackTrace()) {
      sb.append("  at ").append(e).append('\n');
    }
    Throwable cause = t.getCause();
    if (cause != null && cause != t) {
      sb.append("Caused by: ").append(stackTraceToString(cause));
    }
    return sb.toString();
  }

  private static String formatDuration(Duration d) {
    long s = d.getSeconds();
    long h = s / 3600;
    long m = (s % 3600) / 60;
    long sec = s % 60;
    return String.format(Locale.ROOT, "%dh%02dm%02ds", h, m, sec);
  }
}
