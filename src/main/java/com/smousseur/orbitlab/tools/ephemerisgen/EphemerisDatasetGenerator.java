package com.smousseur.orbitlab.tools.ephemerisgen;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.ZipJarCrawler;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

/**
 * Generates a complete ephemeris dataset for all configured solar system bodies.
 *
 * <p>This generator initializes Orekit, sets up thread pools for parallel computation,
 * and delegates per-body file writing to {@link BodyFileWriterV1}. Bodies are processed
 * concurrently up to the configured parallelism limit, while chunk computation within
 * each body is further parallelized with backpressure to bound memory usage.
 */
public final class EphemerisDatasetGenerator {

  private final GeneratorConfigV1 cfg;

  /**
   * Creates a new ephemeris dataset generator with the given configuration.
   *
   * @param cfg the generator configuration specifying bodies, output paths, and parallelism settings
   */
  public EphemerisDatasetGenerator(GeneratorConfigV1 cfg) {
    this.cfg = Objects.requireNonNull(cfg, "cfg");
  }

  /**
   * Generates ephemeris binary files for all configured bodies.
   *
   * <p>Initializes Orekit data from the configured zip file, creates the output directory,
   * and writes one binary ephemeris file per body. Bodies are processed in parallel up to
   * {@link GeneratorConfigV1#maxBodiesInParallel()}, and this method blocks until all
   * bodies are complete.
   *
   * @throws Exception if Orekit initialization, computation, or file I/O fails
   */
  public void generateAll() throws Exception {
    Files.createDirectories(cfg.outputDir());

    initOrekitData(cfg.orekitDataZipPath().toFile());

    TimeScale tai = TimeScalesFactory.getTAI();

    // T_START (offset origin)
    // Effective generated range (Orekit clamp, end exclusive)
    AbsoluteDate tStart = new AbsoluteDate(1990, 1, 1, 0, 0, 0.0, tai);
    AbsoluteDate tEndExclusive = new AbsoluteDate(2100, 12, 31, 0, 0, 0.0, tai);

    double endOffsetSecondsExclusive = tEndExclusive.durationFrom(tStart);

    // Shared compute pool + global in-flight semaphore (memory bound).
    ExecutorService computePool =
        Executors.newFixedThreadPool(
            cfg.computeThreads(),
            r -> {
              Thread t = new Thread(r, "ephemgen-compute");
              t.setDaemon(false);
              return t;
            });

    Semaphore globalInFlight = new Semaphore(cfg.maxChunksInFlightGlobal(), true);

    // Generate bodies in fixed order, but run up to maxBodiesInParallel writers at once.
    ExecutorService bodyPool =
        Executors.newFixedThreadPool(
            cfg.maxBodiesInParallel(),
            r -> {
              Thread t = new Thread(r, "ephemgen-body");
              t.setDaemon(false);
              return t;
            });

    try {
      List<SolarSystemBody> bodies = cfg.bodiesInOrder();
      List<Runnable> jobs = new ArrayList<>(bodies.size());

      for (SolarSystemBody body : bodies) {
        BodyGenerationParams p = cfg.paramsByBody().get(body);
        if (p == null) {
          throw new IllegalStateException("Missing generation params for body=" + body);
        }

        jobs.add(
            () -> {
              try {
                BodyFileWriterV1 writer =
                    new BodyFileWriterV1(
                        cfg,
                        body,
                        p,
                        tStart,
                        tEndExclusive,
                        endOffsetSecondsExclusive,
                        computePool,
                        globalInFlight);
                writer.generateAndWrite();
              } catch (Exception e) {
                throw new RuntimeException("Failed for body=" + body, e);
              }
            });
      }

      // Submit in order; executor will run up to maxBodiesInParallel concurrently.
      for (Runnable job : jobs) {
        bodyPool.submit(job);
      }

    } finally {
      bodyPool.shutdown();
      bodyPool.awaitTermination(7, TimeUnit.DAYS);

      computePool.shutdown();
      computePool.awaitTermination(7, TimeUnit.DAYS);
    }
  }

  private static void initOrekitData(File zipFile) throws Exception {
    // Use default global context.
    DataContext.getDefault();

    DataProvidersManager mgr = DataContext.getDefault().getDataProvidersManager();
    mgr.clearProviders();
    mgr.addProvider(new ZipJarCrawler(zipFile));
  }
}
