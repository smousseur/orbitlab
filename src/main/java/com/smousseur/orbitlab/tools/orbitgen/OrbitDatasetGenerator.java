package com.smousseur.orbitlab.tools.orbitgen;

import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.SimulationConfig;
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
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

public class OrbitDatasetGenerator {
  private static final Logger LOG = Logger.getLogger(OrbitDatasetGenerator.class.getName());

  private final Path outputDir;
  private final OrbitPathCache cache;
  private final ExecutorService executor;
  private final SimulationConfig simulationConfig;
  private final AbsoluteDate referenceStart = OrekitTime.utcNow(); // AbsoluteDate.J2000_EPOCH;

  public OrbitDatasetGenerator(Path outputDir) {
    this.outputDir = outputDir;
    this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    Path datasetDir = Path.of("dataset", "ephemeris");
    DatasetEphemerisSource datasetSource = new DatasetEphemerisSource(datasetDir, 32);
    this.simulationConfig = SimulationConfig.defaultSolarSystem();

    this.cache =
        new OrbitPathCache(
            datasetSource,
            simulationConfig.ephemerisConfig(),
            simulationConfig.orbitWindowConfig(),
            executor);
  }

  public void generate() {
    AtomicInteger counter = new AtomicInteger(0);
    EnumSet<SolarSystemBody> bodies = simulationConfig.orbitBodies();
    for (SolarSystemBody body : bodies) {
      CompletableFuture.supplyAsync(
              () -> cache.getOrComputeOnePeriod(body, referenceStart).join(), executor)
          .thenAccept(
              path -> {
                writeOrbitFile(body, path);
                counter.incrementAndGet();
                if (counter.get() == bodies.size()) {
                  LOG.info("Orbit dataset generation completed.");
                  executor.shutdown();
                }
              })
          .exceptionally(
              ex -> {
                LOG.severe("Failed to generate orbit file for body: " + body);
                return null;
              });
    }
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
}
