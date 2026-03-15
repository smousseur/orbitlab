package com.smousseur.orbitlab.engine.events;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.orbit.OrbitPath;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Minimal thread-safe event queue from "simulation/warmup" side to "render" side.
 *
 * <p>Producer: any thread.
 *
 * <p>Consumer: typically JME render thread (drain in AppState.update()).
 */
public final class OrbitEventBus {

  /**
   * Event indicating that an orbit path has been computed and is ready for rendering.
   *
   * @param body the solar system body whose orbit was computed
   * @param path the computed orbit path
   */
  public record OrbitPathReady(SolarSystemBody body, OrbitPath path) {
    public OrbitPathReady {
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(path, "path");
    }
  }

  private final ConcurrentLinkedQueue<OrbitPathReady> orbitPathReadyQueue =
      new ConcurrentLinkedQueue<>();

  /**
   * Publishes an orbit path ready event. Can be called from any thread.
   *
   * @param body the solar system body whose orbit path is ready
   * @param path the computed orbit path
   */
  public void publishOrbitPathReady(SolarSystemBody body, OrbitPath path) {
    orbitPathReadyQueue.add(new OrbitPathReady(body, path));
  }

  /** Poll one event; returns null if none. Consumer is expected to call this on the JME thread. */
  public OrbitPathReady pollOrbitPathReady() {
    return orbitPathReadyQueue.poll();
  }
}
