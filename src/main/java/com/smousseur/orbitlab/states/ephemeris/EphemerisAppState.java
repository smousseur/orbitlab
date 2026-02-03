package com.smousseur.orbitlab.states.ephemeris;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import com.smousseur.orbitlab.simulation.ephemeris.EphemerisWorker;
import com.smousseur.orbitlab.simulation.ephemeris.SlidingWindowEphemerisBuffer;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisService;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisServiceRegistry;
import com.smousseur.orbitlab.simulation.source.DatasetEphemerisSource;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

/**
 * JME AppState responsible for maintaining the sliding-window ephemeris buffers (PV + rotation).
 *
 * <p>Heavy Orekit computations never run on the render thread; it delegates refresh to {@link
 * EphemerisWorker}.
 */
public final class EphemerisAppState extends BaseAppState {

  private final SimulationClock clock;
  private final EphemerisConfig ephemerisConfig;
  private final SlidingWindowConfig windowConfig;

  private final EphemerisService EPHEMERIS_SERVICE = this::trySampleInterpolated;

  private final EnumMap<SolarSystemBody, SlidingWindowEphemerisBuffer> buffers =
      new EnumMap<>(SolarSystemBody.class);

  private EphemerisWorker worker;
  private AutoCloseable clockSubscription;

  private DatasetEphemerisSource source;

  public EphemerisAppState(ApplicationContext context) {
    this.clock = Objects.requireNonNull(context.clock(), "clock");
    this.ephemerisConfig =
        Objects.requireNonNull(context.config().ephemerisConfig(), "ephemerisConfig");
    this.windowConfig =
        Objects.requireNonNull(context.config().slidingWindowConfig(), "windowConfig");
  }

  /** Exposes the underlying buffer for advanced uses (debug, metrics, etc.). */
  public Optional<SlidingWindowEphemerisBuffer> buffer(SolarSystemBody body) {
    return Optional.ofNullable(buffers.get(body));
  }

  /** Non-blocking sample, empty if no window is built yet or outside the current window. */
  public Optional<BodySample> trySampleInterpolated(SolarSystemBody body, AbsoluteDate t) {

    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(t, "t");
    SlidingWindowEphemerisBuffer buf = buffers.get(body);
    if (buf == null) return Optional.empty();
    return buf.trySampleInterpolated(t);
  }

  /**
   * Forces a rebuild of all bodies around "now" (e.g., at startup or after a significant event).
   */
  public void rebuildAllNow() {
    if (worker != null) {
      worker.rebuildAllNow();
    }
  }

  @Override
  protected void initialize(Application app) {
    Path datasetDir = Path.of("dataset", "ephemeris");

    this.source = new DatasetEphemerisSource(datasetDir, /*chunksInCachePerBody*/ 32);

    for (SolarSystemBody body : SolarSystemBody.values()) {
      buffers.put(body, new SlidingWindowEphemerisBuffer(source, ephemerisConfig, body));
    }

    EphemerisServiceRegistry.publish(EPHEMERIS_SERVICE);
    worker = new EphemerisWorker(windowConfig, buffers, clock::now, clock::speed, clock.now());
    worker.start();

    clockSubscription =
        clock.subscribe(
            evt -> {
              if (evt instanceof SimulationClock.SeekPerformed seek) {
                worker.onSeek(seek.newTime());
              }
            });

    worker.rebuildAllNow();
  }

  @Override
  protected void cleanup(Application app) {
    if (clockSubscription != null) {
      try {
        clockSubscription.close();
      } catch (Exception e) {
        // ignore (or log)
      } finally {
        clockSubscription = null;
      }
    }

    if (worker != null) {
      worker.close();
      worker = null;
    }
    source.close();
    source = null;
    EphemerisServiceRegistry.clear(EPHEMERIS_SERVICE);
    buffers.clear();
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
