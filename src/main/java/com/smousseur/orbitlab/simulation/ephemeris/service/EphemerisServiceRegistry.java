package com.smousseur.orbitlab.simulation.ephemeris.service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global thread-safe registry for the active {@link EphemerisService} instance.
 *
 * <p>Allows application states to publish and retrieve the current ephemeris service without
 * direct coupling. Uses an {@link AtomicReference} for lock-free concurrent access.
 */
public final class EphemerisServiceRegistry {
  private static final AtomicReference<EphemerisService> REF = new AtomicReference<>();

  private EphemerisServiceRegistry() {}

  /**
   * Publishes the given ephemeris service as the active instance.
   *
   * @param svc the ephemeris service to register
   */
  public static void publish(EphemerisService svc) {
    REF.set(svc);
  }

  /**
   * Removes the given ephemeris service from the registry, but only if it is the currently
   * registered instance (compare-and-set semantics).
   *
   * @param svc the ephemeris service to unregister
   */
  public static void clear(EphemerisService svc) {
    REF.compareAndSet(svc, null);
  }

  /**
   * Returns the currently registered ephemeris service, if any.
   *
   * @return the active ephemeris service, or empty if none is registered
   */
  public static Optional<EphemerisService> get() {
    return Optional.ofNullable(REF.get());
  }
}
