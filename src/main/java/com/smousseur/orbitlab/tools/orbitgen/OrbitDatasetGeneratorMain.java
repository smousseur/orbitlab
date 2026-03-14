package com.smousseur.orbitlab.tools.orbitgen;

import com.smousseur.orbitlab.simulation.OrekitService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * CLI entry point for generating pre-computed orbital path dataset files.
 *
 * <p>Usage:
 * <pre>
 *   OrbitDatasetGeneratorMain &lt;orekit-data.zip&gt; &lt;outputDir&gt;
 * </pre>
 */
public final class OrbitDatasetGeneratorMain {
  private OrbitDatasetGeneratorMain() {}

  private static final Logger LOG = Logger.getLogger(OrbitDatasetGeneratorMain.class.getName());

  /**
   * Entry point for orbit dataset generation.
   *
   * <p>Initializes Orekit, validates the command-line arguments, and runs the
   * {@link OrbitDatasetGenerator} to produce binary orbit files for all configured bodies.
   *
   * @param args command-line arguments: {@code <orekit-data.zip> <outputDir>}
   * @throws Exception if initialization or generation fails
   */
  public static void main(String[] args) throws Exception {
    OrekitService.get().initialize();
    if (args.length != 2) {
      System.err.println("Usage: OrbitDatasetGeneratorMain <orekit-data.zip> <outputDir>");
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
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  Duration elapsed = Duration.between(startedAt, Instant.now());
                  LOG.info(() -> "Shutdown hook: elapsed=" + formatDuration(elapsed));
                },
                "orbitgen-shutdown-hook"));
    OrbitDatasetGenerator generator = new OrbitDatasetGenerator(outputDir);
    generator.generate();
  }

  private static String formatDuration(Duration d) {
    long s = d.getSeconds();
    long h = s / 3600;
    long m = (s % 3600) / 60;
    long sec = s % 60;
    return String.format(Locale.ROOT, "%dh%02dm%02ds", h, m, sec);
  }
}
