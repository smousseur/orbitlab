package com.smousseur.orbitlab.simulation.source;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class EphemerisSourceRegistry {
  private static final AtomicReference<EphemerisSource> REF = new AtomicReference<>();

  private EphemerisSourceRegistry() {}

  public static void publish(EphemerisSource src) {
    REF.set(src);
  }

  public static void clear(EphemerisSource src) {
    REF.compareAndSet(src, null);
  }

  public static Optional<EphemerisSource> get() {
    return Optional.ofNullable(REF.get());
  }
}
