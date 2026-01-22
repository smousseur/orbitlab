package com.smousseur.orbitlab.simulation.ephemeris.service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class EphemerisServiceRegistry {
  private static final AtomicReference<EphemerisService> REF = new AtomicReference<>();

  private EphemerisServiceRegistry() {}

  public static void publish(EphemerisService svc) {
    REF.set(svc);
  }

  public static void clear(EphemerisService svc) {
    REF.compareAndSet(svc, null);
  }

  public static Optional<EphemerisService> get() {
    return Optional.ofNullable(REF.get());
  }
}
