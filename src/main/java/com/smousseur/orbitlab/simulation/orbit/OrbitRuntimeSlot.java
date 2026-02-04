package com.smousseur.orbitlab.simulation.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.orekit.time.AbsoluteDate;

public final class OrbitRuntimeSlot {
  private final SolarSystemBody body;
  private final AtomicReference<OrbitSnapshot> ref = new AtomicReference<>();
  private final AtomicBoolean inFlight = new AtomicBoolean(false);
  private final AtomicLong nextVersion = new AtomicLong(1);

  public OrbitRuntimeSlot(SolarSystemBody body) {
    this.body = Objects.requireNonNull(body, "body");
  }

  public SolarSystemBody body() {
    return body;
  }

  public OrbitSnapshot snapshot() {
    return ref.get();
  }

  public long newVersion() {
    return nextVersion.getAndIncrement();
  }

  public void publish(OrbitSnapshot snapshot) {
    ref.set(Objects.requireNonNull(snapshot, "snapshot"));
  }

  public boolean tryStartJob() {
    return inFlight.compareAndSet(false, true);
  }

  public void endJob() {
    inFlight.set(false);
  }

  /**
   * V1 helper: decides and triggers rebuild (anti-spam).
   *
   * @param isInComfort predicate returning true if the current snapshot is still valid
   * @param requestJob callback invoked once if rebuild is needed (CAS inFlight)
   */
  public void requestRebuildIfNeeded(
      AbsoluteDate now,
      java.util.function.Predicate<OrbitSnapshot> isInComfort,
      BiConsumer<SolarSystemBody, AbsoluteDate> requestJob) {

    Objects.requireNonNull(now, "now");
    Objects.requireNonNull(isInComfort, "isInComfort");
    Objects.requireNonNull(requestJob, "requestJob");

    OrbitSnapshot s = ref.get();
    boolean ok = (s != null) && isInComfort.test(s);
    if (!ok && tryStartJob()) {
      requestJob.accept(body, now);
    }
  }
}
