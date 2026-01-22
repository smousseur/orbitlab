package com.smousseur.orbitlab.states.ephemeris;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.orbit.OrbitPath;
import com.smousseur.orbitlab.simulation.orbit.OrbitPathCache;
import org.orekit.time.AbsoluteDate;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * JME AppState responsible for batch precomputation of "one-period orbit paths" (polylines).
 *
 * <p>Computation happens off-thread. When a path is ready, it is published on the JME thread via
 * {@code app.enqueue(...)}.
 */
public final class OrbitPathWarmupAppState extends BaseAppState {

  /** Called on the JME thread when an orbit path is ready. */
  @FunctionalInterface
  public interface OrbitPathReadyListener extends BiConsumer<SolarSystemBody, OrbitPath> {
    @Override
    void accept(SolarSystemBody body, OrbitPath path);
  }

  private final OrbitPathCache orbitPathCache;
  private final Collection<SolarSystemBody> bodiesToWarmup;
  private final AbsoluteDate referenceStart;
  private final OrbitPathReadyListener listener;

  private ExecutorService pool;

  public OrbitPathWarmupAppState(
      OrbitPathCache orbitPathCache,
      Collection<SolarSystemBody> bodiesToWarmup,
      AbsoluteDate referenceStart,
      OrbitPathReadyListener listener) {
    this.orbitPathCache = Objects.requireNonNull(orbitPathCache, "orbitPathCache");
    this.bodiesToWarmup = Objects.requireNonNull(bodiesToWarmup, "bodiesToWarmup");
    this.referenceStart = Objects.requireNonNull(referenceStart, "referenceStart");
    this.listener = Objects.requireNonNull(listener, "listener");
  }

  @Override
  protected void initialize(Application app) {
    int cpu = Runtime.getRuntime().availableProcessors();
    int k = Math.min(cpu, 4);
    pool =
        Executors.newFixedThreadPool(
            k,
            r -> {
              Thread t = new Thread(r, "orbitpath-warmup");
              t.setDaemon(true);
              return t;
            });

    // Submit 1 job per body. They compute in background and publish on the JME thread.
    for (SolarSystemBody body : bodiesToWarmup) {
      CompletableFuture.supplyAsync(
              () -> orbitPathCache.getOrComputeOnePeriod(body, referenceStart).join(), pool)
          .thenAccept(
              path ->
                  app.enqueue(
                      () -> {
                        listener.accept(body, path);
                        return null;
                      }))
          .exceptionally(
              ex -> {
                // Keep warmup best-effort; you can plug a logger here.
                ex.printStackTrace();
                return null;
              });
    }
  }

  @Override
  protected void cleanup(Application app) {
    if (pool != null) {
      pool.shutdownNow();
      try {
        pool.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        pool = null;
      }
    }
  }

  @Override
  protected void onEnable() {
    // No-op
  }

  @Override
  protected void onDisable() {
    // No-op (we could choose to cancel warmup here later)
  }
}
