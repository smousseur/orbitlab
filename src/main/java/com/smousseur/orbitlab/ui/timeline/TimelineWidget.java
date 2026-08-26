package com.smousseur.orbitlab.ui.timeline;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.HudSurfaces;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.timeline.components.*;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Lemur-based "Capsule console" timeline rendered at the bottom of the HUD.
 *
 * <p>This class is the orchestrator: it owns the root container, positions dividers, and
 * coordinates the five sub-components that own their own Lemur elements.
 *
 * <ul>
 *   <li>{@link LiveIndicator} — animated dot + LIVE/PAUSED label
 *   <li>{@link TransportControls} — step-back, play/pause, step-forward
 *   <li>{@link ClockDisplay} — UTC date label, editable to seek to a typed date
 *   <li>{@link SpeedStepper} — speed ± buttons and label
 *   <li>{@link ScrubberTrack} — timeline scrubber with playhead
 * </ul>
 */
public class TimelineWidget implements AutoCloseable {

  private static final Logger logger = LogManager.getLogger(TimelineWidget.class);

  /**
   * Geometry of the time capsule, public because it is the anchor every widget stacked above it
   * measures from — the mission timeline sits {@code BOTTOM_MARGIN_PX + CAPSULE_HEIGHT +
   * HUD_STACK_GAP_PX} up and shares this width and centring. Re-deriving these numbers at the other
   * end would let a resize of the capsule silently desynchronise whatever is stacked on it, with no
   * signal from the compiler.
   */
  public static final float CAPSULE_WIDTH = 600f;

  public static final float CAPSULE_HEIGHT = 52f;

  public static final float BOTTOM_MARGIN_PX = AppStyles.HUD_MARGIN_PX + 10f;

  private static final float CAPSULE_PAD_X = 14f;
  private static final float DIVIDER_HEIGHT = CAPSULE_HEIGHT - 20f;

  private final SimulationClock clock;
  private final Container root;

  private final LiveIndicator liveIndicator;
  private final TransportControls transportControls;
  private final ClockDisplay clockDisplay;
  private final SpeedStepper speedStepper;
  private final ScrubberTrack scrubberTrack;
  private final AutoCloseable speedSubscription;

  private int speedIndex = 0;
  private Mode currentMode = Mode.LIVE;

  /**
   * Creates and attaches the capsule timeline widget to the GUI scene graph.
   *
   * @param context the application context providing the simulation clock and GUI scene graph
   */
  public TimelineWidget(ApplicationContext context) {
    this.clock = Objects.requireNonNull(context, "context must not be null").clock();

    Node timelineNode = context.guiGraph().getTimelineNode();

    root = new Container(TimelineStyles.STYLE);
    applyCapsuleBackground(root);
    Vector3f capsuleSize = new Vector3f(CAPSULE_WIDTH, CAPSULE_HEIGHT, 0f);
    root.setPreferredSize(capsuleSize);
    root.setSize(capsuleSize);
    timelineNode.attachChild(root);

    // Live indicator — leftmost cluster
    liveIndicator =
        new LiveIndicator(root, CAPSULE_HEIGHT, CAPSULE_PAD_X, clock, this::onLiveReset);

    // Divider 1 (after live indicator)
    float divider1X = liveIndicator.rightEdge();
    placeDivider(divider1X);

    // Transport controls (step-back, play/pause, step-forward)
    float transportStart = divider1X + 1f + 10f;
    transportControls = new TransportControls(root, CAPSULE_HEIGHT, transportStart, clock);

    // Divider 2 (after transport controls)
    float divider2X = transportControls.rightEdge();
    placeDivider(divider2X);

    // Clock display — anchored to the right edge
    float rightEnd = CAPSULE_WIDTH - CAPSULE_PAD_X;
    clockDisplay = new ClockDisplay(root, CAPSULE_HEIGHT, rightEnd, clock);

    // Divider 3 (left of clock display)
    float divider3X = clockDisplay.leftEdge() - 10f - 1f;
    placeDivider(divider3X);

    // Speed stepper — right of the scrubber
    float stepperRight = divider3X - 10f;
    speedStepper = new SpeedStepper(root, CAPSULE_HEIGHT, stepperRight, this::onSpeedDelta);

    // Scrubber — fills the middle space between transport and stepper
    float scrubberStart = divider2X + 1f + 10f;
    float scrubberEnd = speedStepper.leftEdge() - 10f;
    scrubberTrack =
        new ScrubberTrack(root, CAPSULE_HEIGHT, scrubberStart, scrubberEnd, this::applySpeedIndex);

    speedIndex = SpeedStepper.speedToIndex(clock.speed());
    speedStepper.refresh(speedIndex);
    refreshMode();
    scrubberTrack.refresh(speedIndex);

    // The clock owns the speed; this widget only displays it. Subscribing is what keeps the
    // stepper and the scrubber honest when something other than this widget calls setSpeed —
    // the mission timeline's "go to mission start" button being the first such caller (spec
    // §12.1). Routing that reset through an EventBus message instead would have worked, but it
    // would have left the next external caller desynchronised and added an event to work around
    // a defect rather than fixing it.
    //
    // Events are published on the thread that called setSpeed, and every caller in this
    // application is on the render thread, so touching Lemur elements from here is safe.
    speedSubscription =
        clock.subscribe(
            event -> {
              if (event instanceof SimulationClock.SpeedChanged changed) {
                syncSpeedIndex(changed.newSpeed());
              }
            });
  }

  /**
   * Re-derives the displayed speed index from the clock. A no-op when the widget is the origin of
   * the change: {@link #applySpeedIndex(int)} writes the field before calling the clock.
   *
   * @param speed the clock's new speed
   */
  private void syncSpeedIndex(double speed) {
    int next = SpeedStepper.speedToIndex(speed);
    if (next == speedIndex) {
      return;
    }
    speedIndex = next;
    speedStepper.refresh(next);
    scrubberTrack.refresh(next);
  }

  /**
   * Updates the widget state. Called once per frame from {@link
   * com.smousseur.orbitlab.states.time.TimelineWidgetAppState}.
   *
   * @param tpf time per frame in seconds (unused, follows JME3 convention)
   */
  public void update(float tpf) {
    clockDisplay.update(clock.now());
    refreshMode();
  }

  /**
   * Positions the widget at the bottom centre of the screen.
   *
   * @param screenWidth the current screen width in pixels
   */
  public void layoutBottomCenter(int screenWidth) {
    Vector3f size = root.getPreferredSize();
    float x = (screenWidth - size.x) * 0.5f;
    float y = BOTTOM_MARGIN_PX + size.y;
    root.setLocalTranslation(x, y, UiLayers.HUD);
  }

  @Override
  public void close() {
    HudSurfaces.closeQuietly(speedSubscription, logger);
    root.removeFromParent();
  }

  private void refreshMode() {
    Mode next = clock.isPlaying() ? Mode.LIVE : Mode.PAUSED;
    if (next != currentMode) {
      currentMode = next;
      liveIndicator.refresh(currentMode);
      transportControls.refresh(clock.isPlaying());
    }
  }

  private void onLiveReset() {
    speedIndex = 0;
    speedStepper.refresh(0);
    scrubberTrack.refresh(0);
    refreshMode();
  }

  private void onSpeedDelta(int delta) {
    applySpeedIndex(speedIndex + delta);
  }

  private void applySpeedIndex(int next) {
    next = clamp(next, SpeedStepper.MIN_INDEX, SpeedStepper.MAX_INDEX);
    if (next == speedIndex) return;
    speedIndex = next;
    clock.setSpeed(SpeedStepper.mapIndexToSpeed(speedIndex));
    speedStepper.refresh(speedIndex);
    scrubberTrack.refresh(speedIndex);
  }

  private void placeDivider(float x) {
    Panel d = new Panel(1f, DIVIDER_HEIGHT, TimelineStyles.STYLE);
    Texture2D tex = TimelineStyles.tex("divider.png");
    if (tex != null) {
      d.setBackground(new QuadBackgroundComponent(tex));
    } else {
      d.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN, 0.60f)));
    }
    d.setSize(d.getPreferredSize());
    float y = -(CAPSULE_HEIGHT - DIVIDER_HEIGHT) * 0.5f;
    d.setLocalTranslation(x, y, 1f);
    root.attachChild(d);
  }

  private void applyCapsuleBackground(Container c) {
    TbtQuadBackgroundComponent capsule = TimelineStyles.capsuleBackground();
    if (capsule != null) {
      c.setBackground(capsule);
    } else {
      c.setBackground(new QuadBackgroundComponent(new ColorRGBA(0.06f, 0.14f, 0.22f, 0.95f)));
    }
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private static ColorRGBA withAlpha(ColorRGBA c, float alpha) {
    return new ColorRGBA(c.r, c.g, c.b, alpha);
  }
}
