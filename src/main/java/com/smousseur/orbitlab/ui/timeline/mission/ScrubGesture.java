package com.smousseur.orbitlab.ui.timeline.mission;

/**
 * The click-vs-drag decision for the mission timeline's continuous scrub ({@code NAV-3}).
 *
 * <p>A single capture panel already serves two discrete {@code NAV-2} gestures — a rail click and a
 * cluster click — and now must also serve a continuous drag. Those three intents are
 * indistinguishable at {@code press} time: every gesture starts as a press at some x. This class is
 * the state machine that watches the cursor and decides, frame by frame, which intent is in play,
 * so the caller (the widget) never has to reconstruct that logic itself.
 *
 * <p><b>Why a pixel threshold, not an immediate drag.</b> {@link #DRAG_THRESHOLD_PX} is what lets
 * the existing {@code NAV-2} click gestures keep working unchanged: a press that never moves more
 * than a few pixels before release is indistinguishable from a hand that is merely imprecise, and
 * must still resolve to {@link Release#CLICK} — rail seek or cluster-first-transition seek, exactly
 * as before. Only a motion that clears the threshold commits to being a drag, and once it does, the
 * gesture never reverts to a click even if the cursor wanders back to the origin: a user who has
 * already crossed the threshold is dragging, and a jitter back to the start pixel is not proof they
 * meant a click all along.
 *
 * <p><b>Why a time throttle, not an immediate emit.</b> {@code EphemerisWorker.onSeek} already
 * coalesces bursts of seeks one layer down — it stores the latest date in an {@code
 * AtomicReference} and only consumes it on its own 200 ms tick, so nothing downstream is actually
 * damaged by emitting on every pixel of motion. {@link #EMIT_INTERVAL_MS} exists anyway, as a
 * deliberate bound at the source: it keeps this class correct on its own terms rather than
 * depending on that coalescing staying true, and it keeps the emission rate sane for whatever
 * future seek consumer does not happen to buffer the same way.
 *
 * <p>Free of any JME dependency on purpose, in the same spirit as {@link TimeAxis} and {@code
 * PhaseMarkerCluster}: pixel floats and millisecond timestamps only, so the state machine can be
 * unit-tested without a render loop.
 */
public final class ScrubGesture {

  /** Movement in pixels beyond which a press becomes a drag rather than a click. */
  public static final float DRAG_THRESHOLD_PX = 3f;

  /** Minimum interval between two emitted seeks while dragging. */
  public static final long EMIT_INTERVAL_MS = 100L;

  /** What the caller should do in response to a cursor motion. */
  public enum Move {
    /** Nothing: no gesture is in progress, or the threshold is not crossed yet. */
    NONE,
    /** The threshold has just been crossed: take the clock's play state, pause it, and seek. */
    BEGIN_DRAG,
    /** Still dragging and the throttle has elapsed: move the playhead and seek. */
    EMIT,
    /** Still dragging but within the throttle window: move the playhead only, do not seek. */
    TRACK_ONLY
  }

  /** What the caller should do when the button is released. */
  public enum Release {
    /** No gesture was in progress. */
    NONE,
    /** The threshold was never crossed: run the discrete click behaviour. */
    CLICK,
    /** A drag ends: seek exactly to the released position and restore the play state. */
    COMMIT
  }

  private enum State {
    IDLE,
    PRESSED,
    DRAGGING
  }

  private State state = State.IDLE;
  private float originX;
  private long lastEmitMillis;

  /**
   * Records a press origin, starting a new gesture. Restarts from the new origin even if a gesture
   * (pressed or already dragging) was already in progress — a fresh button-down always describes
   * the user's current intent better than whatever came before it.
   *
   * @param x the cursor's x at press time, in the widget's local space
   * @param nowMillis the press timestamp
   */
  public void press(float x, long nowMillis) {
    this.state = State.PRESSED;
    this.originX = x;
    this.lastEmitMillis = nowMillis;
  }

  /**
   * Feeds a cursor motion to the state machine.
   *
   * @param x the cursor's current x, in the widget's local space
   * @param nowMillis the motion timestamp
   * @return what the caller should do; see {@link Move}
   */
  public Move move(float x, long nowMillis) {
    return switch (state) {
      case IDLE -> Move.NONE;
      case PRESSED -> {
        if (Math.abs(x - originX) > DRAG_THRESHOLD_PX) {
          state = State.DRAGGING;
          lastEmitMillis = nowMillis;
          yield Move.BEGIN_DRAG;
        }
        yield Move.NONE;
      }
      case DRAGGING -> {
        if (nowMillis - lastEmitMillis >= EMIT_INTERVAL_MS) {
          lastEmitMillis = nowMillis;
          yield Move.EMIT;
        }
        yield Move.TRACK_ONLY;
      }
    };
  }

  /**
   * Resolves the gesture at button-up and returns to {@link State#IDLE}.
   *
   * @return what the caller should do; see {@link Release}
   */
  public Release release() {
    Release result =
        switch (state) {
          case IDLE -> Release.NONE;
          case PRESSED -> Release.CLICK;
          case DRAGGING -> Release.COMMIT;
        };
    state = State.IDLE;
    return result;
  }

  /**
   * Aborts the gesture in progress, if any, without resolving it into a click or a commit — used
   * when an outside event (e.g. focus loss) makes the in-progress gesture meaningless.
   *
   * @return true if a drag was in progress, so the caller knows to restore the play state
   */
  public boolean cancel() {
    boolean wasDragging = state == State.DRAGGING;
    state = State.IDLE;
    return wasDragging;
  }

  /**
   * @return true if the gesture has crossed the threshold and is currently a drag
   */
  public boolean isDragging() {
    return state == State.DRAGGING;
  }
}
