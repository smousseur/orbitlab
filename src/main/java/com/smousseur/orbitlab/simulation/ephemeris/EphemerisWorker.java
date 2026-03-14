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

public final class EphemerisWorker implements AutoCloseable {

  private static final Logger logger = LogManager.getLogger(EphemerisWorker.class);

  private final SlidingWindowConfig windowConfig;
  private final Map<SolarSystemBody, SlidingWindowEphemerisBuffer> buffersByBodyId;

  private final Supplier<AbsoluteDate> nowSupplier;
  private final DoubleSupplier speedSupplier;

  private final AbsoluteDate sessionAnchor;

  private final ScheduledExecutorService scheduler;

  private final AtomicReference<AbsoluteDate> pendingSeek = new AtomicReference<>(null);

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

  public void start() {
    scheduler.scheduleAtFixedRate(this::tickSafe, 0, 200, TimeUnit.MILLISECONDS);
  }

  public void onSeek(AbsoluteDate newNow) {
    pendingSeek.set(Objects.requireNonNull(newNow, "newNow"));
  }

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
