package com.smousseur.orbitlab.simulation.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.orekit.time.AbsoluteDate;

/**
 * Thread-safe runtime container for a single celestial body's orbit visualization data.
 *
 * <p>Holds the current {@link OrbitSnapshot} and manages concurrent access between the
 * render thread (reader) and the orbit computation thread (writer). Provides anti-spam
 * logic to prevent overlapping rebuild jobs via a compare-and-set in-flight flag.
 */
public final class OrbitRuntimeSlot {
  private final SolarSystemBody body;
  private final AtomicReference<OrbitSnapshot> ref = new AtomicReference<>();
  private final AtomicBoolean inFlight = new AtomicBoolean(false);
  private final AtomicLong nextVersion = new AtomicLong(1);

  /**
   * Creates a new runtime slot for the given body with no initial snapshot.
   *
   * @param body the celestial body this slot tracks
   */
  public OrbitRuntimeSlot(SolarSystemBody body) {
    this.body = Objects.requireNonNull(body, "body");
  }

  /**
   * Returns the celestial body associated with this slot.
   *
   * @return the solar system body
   */
  public SolarSystemBody body() {
    return body;
  }

  /**
   * Returns the currently published orbit snapshot, or null if none has been published yet.
   *
   * @return the current snapshot, or null
   */
  public OrbitSnapshot snapshot() {
    return ref.get();
  }

  /**
   * Generates and returns the next version number for snapshot versioning.
   *
   * @return a monotonically increasing version number
   */
  public long newVersion() {
    return nextVersion.getAndIncrement();
  }

  /**
   * Atomically publishes a new orbit snapshot, replacing any previous one.
   *
   * @param snapshot the snapshot to publish
   */
  public void publish(OrbitSnapshot snapshot) {
    ref.set(Objects.requireNonNull(snapshot, "snapshot"));
  }

  /**
   * Attempts to acquire the in-flight flag to start a rebuild job.
   *
   * @return true if the flag was successfully acquired (no job was in progress), false otherwise
   */
  public boolean tryStartJob() {
    return inFlight.compareAndSet(false, true);
  }

  /**
   * Releases the in-flight flag, signaling that the rebuild job has completed.
   */
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
