package com.smousseur.orbitlab.app;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.orekit.time.AbsoluteDate;

/**
 * Thread-safe simulation clock driven by the JME application time (tpf seconds).
 *
 * <p>Design goals:
 *
 * <ul>
 *   <li>Deterministic: time advances only when {@link #update(double)} is called.
 *   <li>Thread-safe reads: {@link #now()} can be called from any thread.
 *   <li>Unit-testable: no dependency on system time.
 *   <li>Event-driven: publishes changes to time/play/speed/seek.
 * </ul>
 */
public final class SimulationClock {

  public enum ChangeCause {
    USER,
    TICK
  }

  public sealed interface ClockEvent
      permits TimeChanged, PlayStateChanged, SpeedChanged, SeekPerformed {}

  public record TimeChanged(AbsoluteDate oldTime, AbsoluteDate newTime, ChangeCause cause)
      implements ClockEvent {
    public TimeChanged {
      Objects.requireNonNull(oldTime, "oldTime");
      Objects.requireNonNull(newTime, "newTime");
      Objects.requireNonNull(cause, "cause");
    }
  }

  public record PlayStateChanged(boolean oldPlaying, boolean newPlaying) implements ClockEvent {}

  public record SpeedChanged(double oldSpeed, double newSpeed) implements ClockEvent {}

  public record SeekPerformed(AbsoluteDate oldTime, AbsoluteDate newTime) implements ClockEvent {
    public SeekPerformed {
      Objects.requireNonNull(oldTime, "oldTime");
      Objects.requireNonNull(newTime, "newTime");
    }
  }

  private final CopyOnWriteArrayList<Consumer<ClockEvent>> listeners = new CopyOnWriteArrayList<>();

  /**
   * Guarded by {@code this}. We synchronize updates and state changes so that: - now/play/speed
   * changes are atomic w.r.t event emission ordering - update() can't interleave with seek()
   * half-way
   */
  private AbsoluteDate now;

  private boolean playing;
  private double speed; // seconds of simulation per 1 second of app time (can be negative)

  public SimulationClock(AbsoluteDate initialTime) {
    this.now = Objects.requireNonNull(initialTime, "initialTime");
    this.playing = true;
    this.speed = 1.0;
  }

  /** Current simulation time (thread-safe). */
  public AbsoluteDate now() {
    synchronized (this) {
      return now;
    }
  }

  /** Whether the clock is advancing on {@link #update(double)}. */
  public boolean isPlaying() {
    synchronized (this) {
      return playing;
    }
  }

  /** Current speed multiplier: simSeconds advanced per app second (can be negative). */
  public double speed() {
    synchronized (this) {
      return speed;
    }
  }

  /** Subscribe to clock events. Returns an AutoCloseable to unsubscribe. */
  public AutoCloseable subscribe(Consumer<ClockEvent> listener) {
    Objects.requireNonNull(listener, "listener");
    listeners.add(listener);
    return () -> listeners.remove(listener);
  }

  /** Convenience: start playback (no-op if already playing). */
  public void play() {
    setPlaying(true);
  }

  /** Convenience: pause playback (no-op if already paused). */
  public void pause() {
    setPlaying(false);
  }

  public void setPlaying(boolean playing) {
    PlayStateChanged evt = null;
    synchronized (this) {
      if (this.playing == playing) {
        return;
      }
      boolean old = this.playing;
      this.playing = playing;
      evt = new PlayStateChanged(old, playing);
    }
    publish(evt);
  }

  /**
   * Sets the speed multiplier.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>1.0 = real-time
   *   <li>10.0 = 10x faster
   *   <li>-60.0 = rewind at 60x
   * </ul>
   */
  public void setSpeed(double newSpeed) {
    if (!Double.isFinite(newSpeed)) {
      throw new IllegalArgumentException("speed must be finite");
    }
    SpeedChanged evt;
    synchronized (this) {
      if (Double.compare(this.speed, newSpeed) == 0) {
        return;
      }
      double old = this.speed;
      this.speed = newSpeed;
      evt = new SpeedChanged(old, newSpeed);
    }
    publish(evt);
  }

  /** Seek to a new absolute simulation time. Does not change playing state. */
  public void seek(AbsoluteDate newTime) {
    Objects.requireNonNull(newTime, "newTime");
    SeekPerformed seekEvt;
    TimeChanged timeEvt;
    synchronized (this) {
      AbsoluteDate old = this.now;
      if (old.equals(newTime)) {
        return;
      }
      this.now = newTime;
      seekEvt = new SeekPerformed(old, newTime);
      timeEvt = new TimeChanged(old, newTime, ChangeCause.USER);
    }
    publish(seekEvt);
    publish(timeEvt);
  }

  /**
   * Advance simulation time according to the application (JME) frame delta time in seconds.
   *
   * <p>This method is intended to be called from the JME update loop. It is deterministic: the only
   * time source is the provided {@code tpfSeconds}.
   */
  public void update(double tpfSeconds) {
    if (!Double.isFinite(tpfSeconds) || tpfSeconds < 0.0) {
      throw new IllegalArgumentException("tpfSeconds must be finite and >= 0");
    }

    TimeChanged evt;
    synchronized (this) {
      if (!playing) {
        return;
      }
      double deltaSimSeconds = tpfSeconds * speed;
      if (deltaSimSeconds == 0.0) {
        return;
      }
      AbsoluteDate old = this.now;
      AbsoluteDate next = old.shiftedBy(deltaSimSeconds);
      this.now = next;
      evt = new TimeChanged(old, next, ChangeCause.TICK);
    }
    publish(evt);
  }

  private void publish(ClockEvent event) {
    if (event == null) {
      return;
    }
    for (Consumer<ClockEvent> l : listeners) {
      l.accept(event);
    }
  }

  // For debugging/tests
  List<Consumer<ClockEvent>> listenersSnapshot() {
    return List.copyOf(listeners);
  }
}
