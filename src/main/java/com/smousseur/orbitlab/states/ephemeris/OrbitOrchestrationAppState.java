package com.smousseur.orbitlab.states.ephemeris;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.SimulationConfig;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.OrbitEventBus;
import com.smousseur.orbitlab.simulation.orbit.OrbitPathCache;
import com.smousseur.orbitlab.simulation.source.DatasetEphemerisSource;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class OrbitOrchestrationAppState extends BaseAppState {

  private final SimulationClock clock;
  private final SimulationConfig simConfig;
  private final OrbitEventBus orbitBus;

  private ExecutorService orbitComputePool;

  public OrbitOrchestrationAppState(ApplicationContext context) {
    this.clock = Objects.requireNonNull(context.clock(), "clock");
    this.simConfig = Objects.requireNonNull(context.config(), "simConfig");
    this.orbitBus = Objects.requireNonNull(context.orbitBus(), "orbitBus");
  }

  @Override
  protected void initialize(Application app) {
    orbitComputePool =
        Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors(), 4));
    Path datasetDir = Path.of("dataset", "ephemeris");

    DatasetEphemerisSource source =
        new DatasetEphemerisSource(datasetDir, /*chunksInCachePerBody*/ 32);

    OrbitPathCache orbitPathCache =
        new OrbitPathCache(
            source, simConfig.ephemerisConfig(), simConfig.orbitPathConfig(), orbitComputePool);

    var orbitReferenceStart = simConfig.computeOrbitReferenceStart(clock.now());

    getStateManager()
        .attach(
            new OrbitPathWarmupAppState(
                orbitPathCache,
                simConfig.orbitWarmupBodies(),
                orbitReferenceStart,
                orbitBus::publishOrbitPathReady));
  }

  @Override
  protected void cleanup(Application app) {
    if (orbitComputePool != null) {
      orbitComputePool.shutdown();
      try {
        if (!orbitComputePool.awaitTermination(2, TimeUnit.SECONDS)) {
          orbitComputePool.shutdownNow();
        }
      } catch (InterruptedException e) {
        orbitComputePool.shutdownNow();
        Thread.currentThread().interrupt();
      } finally {
        orbitComputePool = null;
      }
    }
  }

  @Override
  protected void onEnable() {
    // no-op
  }

  @Override
  protected void onDisable() {
    // no-op
  }
}
