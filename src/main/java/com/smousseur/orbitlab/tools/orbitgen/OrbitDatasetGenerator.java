package com.smousseur.orbitlab.tools.orbitgen;

import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.SimulationConfig;
import com.smousseur.orbitlab.core.OrbitlabPath;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.orbit.OrbitPath;
import com.smousseur.orbitlab.simulation.orbit.OrbitPathCache;
import com.smousseur.orbitlab.simulation.source.DatasetEphemerisSource;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * Generates pre-computed orbital path binary files for all configured solar system bodies.
 *
 * <p>Uses a {@link DatasetEphemerisSource} to obtain ephemeris data and an {@link OrbitPathCache}
 * to compute one full orbital period per body. Each orbit is written as a binary file containing
 * heliocentric position vectors in meters.
 */
public class OrbitDatasetGenerator {
  private static final Logger LOG = Logger.getLogger(OrbitDatasetGenerator.class.getName());

  private final Path outputDir;
  private final OrbitPathCache cache;
  private final ExecutorService executor;
  private final SimulationConfig simulationConfig;
  private final AbsoluteDate referenceStart = OrekitTime.utcNow(); // AbsoluteDate.J2000_EPOCH;
  private final DatasetEphemerisSource datasetSource;

  /**
   * Creates a new orbit dataset generator that writes output files to the specified directory.
   *
   * <p>Initializes a thread pool sized to the number of available processors, loads the ephemeris
   * dataset from the default location, and creates an orbit path cache.
   *
   * @param outputDir the directory where orbit binary files will be written
   */
  public OrbitDatasetGenerator(Path outputDir) {
    this.outputDir = outputDir;
    this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    Path datasetDir = OrbitlabPath.EPHEMERIS_PATH;
    this.datasetSource = new DatasetEphemerisSource(datasetDir, 32);
    this.simulationConfig = SimulationConfig.defaultSolarSystem();

    this.cache =
        new OrbitPathCache(
            datasetSource,
            simulationConfig.ephemerisConfig(),
            simulationConfig.orbitWindowConfig(),
            executor);
  }

  /**
   * Generates orbit binary files for all configured orbital bodies.
   *
   * <p>For each body, computes one full orbital period and writes the resulting heliocentric
   * positions to a binary file named {@code <BODY>-orbit.bin} in the output directory. Bodies are
   * computed in parallel, but the method only returns once every one of them has been written or
   * has failed: the ephemeris handles these computations read from belong to this generator, and
   * {@link #close} pulling them out from under a computation still in flight would fail it with a
   * closed channel.
   */
  public void generate() {
    Set<SolarSystemBody> bodies = simulationConfig.orbitBodies();
    List<CompletableFuture<Void>> pending = new ArrayList<>(bodies.size());
    for (SolarSystemBody body : bodies) {
      pending.add(
          CompletableFuture.supplyAsync(
                  () -> cache.getOrComputeOnePeriod(body, referenceStart).join(), executor)
              .thenAccept(path -> writeOrbitFile(body, path))
              .exceptionally(
                  ex -> {
                    LOG.severe("Failed to generate orbit file for body: " + body);
                    return null;
                  }));
    }
    CompletableFuture.allOf(pending.toArray(new CompletableFuture<?>[0])).join();
    LOG.info("Orbit dataset generation completed.");
    executor.shutdown();
  }

  private void writeOrbitFile(SolarSystemBody body, OrbitPath path) {
    Path outFile = outputDir.resolve(body.name() + "-orbit.bin");
    try (var out =
        new DataOutputStream(
            new BufferedOutputStream(
                Files.newOutputStream(
                    outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
      out.writeInt(path.positionsHelioMeters().size());
      for (Vector3D pos : path.positionsHelioMeters()) {
        out.writeDouble(pos.getX());
        out.writeDouble(pos.getY());
        out.writeDouble(pos.getZ());
      }
    } catch (IOException e) {
      LOG.severe("Failed to create file for body: " + body);
    }
  }

  /**
   * Releases the ephemeris dataset handles this generator reads from.
   *
   * <p>Only safe once {@link #generate} has returned: the handles are shared by every body
   * computation, and closing them mid-flight fails the ones still running.
   */
  public void close() {
    datasetSource.close();
  }
}
