package com.smousseur.orbitlab.simulation.source;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe global registry for the active {@link EphemerisSource}.
 *
 * <p>At most one ephemeris source is active at a time. Components that need ephemeris data
 * retrieve the current source via {@link #get()}, while the application lifecycle publishes
 * and clears the source as the dataset becomes available or is shut down.
 */
public final class EphemerisSourceRegistry {
  private static final AtomicReference<EphemerisSource> REF = new AtomicReference<>();

  private EphemerisSourceRegistry() {}

  /**
   * Publishes the given ephemeris source as the globally active source, replacing any previously
   * published source.
   *
   * @param src the ephemeris source to publish
   */
  public static void publish(EphemerisSource src) {
    REF.set(src);
  }

  /**
   * Clears the globally active ephemeris source, but only if the current source matches the given
   * instance. This prevents accidentally clearing a source that was replaced by another publisher.
   *
   * @param src the ephemeris source to clear (compared by reference)
   */
  public static void clear(EphemerisSource src) {
    REF.compareAndSet(src, null);
  }

  /**
   * Returns the currently active ephemeris source, if one has been published.
   *
   * @return an {@link Optional} containing the active source, or empty if none is published
   */
  public static Optional<EphemerisSource> get() {
    return Optional.ofNullable(REF.get());
  }
}
