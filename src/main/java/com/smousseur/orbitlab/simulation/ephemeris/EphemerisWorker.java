package com.smousseur.orbitlab.simulation.ephemeris;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Background worker that periodically updates {@link SlidingWindowEphemerisBuffer}s for each
 * celestial body to keep them in sync with the simulation clock.
 *
 * <p>Runs on a dedicated daemon thread, ticking at a fixed rate (200 ms). Supports seek
 * operations that trigger a full buffer rebuild, as well as incremental sliding updates
 * during normal playback.
 */
public final class EphemerisWorker implements AutoCloseable {

  private static final Logger logger = LogManager.getLogger(EphemerisWorker.class);

  private final SlidingWindowConfig windowConfig;
  private final Map<SolarSystemBody, SlidingWindowEphemerisBuffer> buffersByBodyId;

  private final Supplier<AbsoluteDate> nowSupplier;
  private final DoubleSupplier speedSupplier;

  private final AbsoluteDate sessionAnchor;

  private final ScheduledExecutorService scheduler;

  private final AtomicReference<AbsoluteDate> pendingSeek = new AtomicReference<>(null);

  /**
   * Creates a new ephemeris worker.
   *
   * @param windowConfig configuration controlling window sizing and step selection per body
   * @param buffersByBodyId the sliding window buffers to maintain, keyed by celestial body
   * @param nowSupplier supplies the current simulation time
   * @param speedSupplier supplies the current simulation clock speed multiplier
   * @param sessionAnchor the fixed reference date used to align buffer grid boundaries
   */
  public EphemerisWorker(
      SlidingWindowConfig windowConfig,
      Map<SolarSystemBody, SlidingWindowEphemerisBuffer> buffersByBodyId,
      Supplier<AbsoluteDate> nowSupplier,
      DoubleSupplier speedSupplier,
      AbsoluteDate sessionAnchor) {
    this.windowConfig = Objects.requireNonNull(windowConfig, "windowConfig");
    this.buffersByBodyId = Objects.requireNonNull(buffersByBodyId, "buffersByBodyId");
    this.nowSupplier = Objects.requireNonNull(nowSupplier, "nowSupplier");
    this.speedSupplier = Objects.requireNonNull(speedSupplier, "speedSupplier");
    this.sessionAnchor = Objects.requireNonNull(sessionAnchor, "sessionAnchor");

    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "ephemeris-worker");
              t.setDaemon(true);
              return t;
            });
  }

  /**
   * Starts the periodic background tick that maintains the ephemeris buffers.
   */
  public void start() {
    scheduler.scheduleAtFixedRate(this::tickSafe, 0, 200, TimeUnit.MILLISECONDS);
  }

  /**
   * Signals that the simulation clock has been seeked to a new time, triggering a full
   * buffer rebuild on the next tick.
   *
   * @param newNow the new simulation time after the seek
   */
  public void onSeek(AbsoluteDate newNow) {
    pendingSeek.set(Objects.requireNonNull(newNow, "newNow"));
  }

  /**
   * Forces an immediate full rebuild of all buffers centered on the current simulation time.
   */
  public void rebuildAllNow() {
    pendingSeek.set(nowSupplier.get());
  }

  void tickOnceForTests() {
    tick();
  }

  private void tickSafe() {
    try {
      tick();
    } catch (Throwable t) {
      logger.error("Unexpected error in ephemeris worker tick", t);
    }
  }

  private void tick() {
    AbsoluteDate seek = pendingSeek.getAndSet(null);
    AbsoluteDate now = (seek != null) ? seek : nowSupplier.get();
    double speed = speedSupplier.getAsDouble();

    boolean forceFullRebuild = (seek != null);

    for (Map.Entry<SolarSystemBody, SlidingWindowEphemerisBuffer> e : buffersByBodyId.entrySet()) {
      SolarSystemBody body = e.getKey();
      SlidingWindowEphemerisBuffer buffer = e.getValue();

      SlidingWindowConfig.WindowPlan plan = windowConfig.plan(body, speed);

      buffer.ensureWindow(sessionAnchor, now, speed, plan, forceFullRebuild);
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }
}
