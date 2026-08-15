package com.smousseur.orbitlab.ui.timeline.mission;

import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.CursorButtonEvent;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.CursorMotionEvent;
import com.simsilica.lemur.event.DefaultCursorListener;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.PhaseRun;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * The mission track: a second capsule of 600×72 posed 8 px above the time capsule, showing the
 * followed mission on a linear time axis (spec {@code docs/navigation/02-timeline-mission.md} §6).
 *
 * <p><b>Twin capsule, not a new visual family.</b> Same 9-slice shell, same cyan chrome; the
 * mission's colour appears only in the content — dot, segments, markers. Two successive missions
 * therefore change what the widget shows, never what the widget is. The alternatives were measured
 * at real size and rejected: a bare slider is illegible over a lit limb, which is the nominal case
 * and not an edge one, and nothing about it says it is clickable; a {@code FormStyles} card would
 * put two visual grammars 8 px apart, and its layout would serve nothing since the rail stays in
 * absolute placement anyway.
 *
 * <p><b>Two Lemur mechanics everything here depends on.</b> {@code attachChild} bypasses the layout
 * while {@code addChild} submits to it — a track where {@code x = f(time)} cannot submit to one —
 * so every element is attached and translated explicitly, with its own {@code
 * setSize(getPreferredSize())}. And tinting a texture goes through {@code IconComponent.setColor},
 * not through a panel background.
 *
 * <p>The widget is dumb about <em>which</em> mission it shows: {@link #bind} is called by {@code
 * MissionTimelineAppState} once per mission-or-ephemeris identity change, and {@link #updateNow}
 * per frame moves nothing but the playhead and its pinned badge.
 */
public final class MissionTimelineWidget implements AutoCloseable {

  /** Width of the capsule, the time capsule's own. */
  public static final float WIDTH = 600f;

  /** Height of the capsule: header, rail, markers and graduations. */
  public static final float HEIGHT = 72f;

  /** Lateral margin, the time capsule's own. */
  public static final float PAD_X = 14f;

  /** Track width: {@code WIDTH - 2 * PAD_X}. */
  public static final float TRACK_WIDTH = WIDTH - 2 * PAD_X;

  /**
   * Seconds of lead-in before the mission's first sample that the "go to mission start" button
   * seeks to, so one <em>sees</em> the lift-off rather than already being past it.
   *
   * <p>A named constant of this widget, deliberately not a field of {@code SimulationConfig}: that
   * record configures the simulation — bodies, ephemerides, orbit windows — and pouring a comfort
   * setting of the interface into it would mix two lifetimes of configuration. If it ever needs
   * runtime tuning, its place is a future interface-preferences group.
   */
  public static final double PRE_ROLL_SECONDS = 10.0;

  private static final float HEADER_TOP = 3f;
  private static final float HEADER_HEIGHT = 20f;
  private static final float BAR_TOP = 28f;
  private static final float RAIL_TOP = 31f;
  private static final float MARKER_TOP = 40f;
  private static final float TICK_TOP = 50f;
  private static final float TICK_MAJOR_HEIGHT = 10f;
  private static final float TICK_MINOR_TOP = 52f;
  private static final float TICK_MINOR_HEIGHT = 6f;
  private static final float TICK_LABEL_TOP = 61f;
  private static final float TICK_LABEL_HEIGHT = 10f;
  private static final float TICK_LABEL_WIDTH = 56f;
  private static final float PLAYHEAD_TOP = 25f;
  private static final float PLAYHEAD_WIDTH = 12f;
  private static final float PLAYHEAD_HEIGHT = 16f;
  private static final float HALO_TOP = 17f;
  private static final float HALO_SIZE = 32f;
  private static final float BADGE_TOP = 25f;
  private static final float BADGE_HEIGHT = 16f;
  private static final float CAPTURE_TOP = 24f;
  private static final float CAPTURE_HEIGHT = 28f;

  private static final float DOT_SIZE = 8f;
  private static final float START_BUTTON_WIDTH = 74f;
  private static final float START_BUTTON_HEIGHT = 18f;
  private static final float CHEVRON_WIDTH = 10f;
  private static final float CHEVRON_HEIGHT = 12f;
  private static final float BADGE_PAD_X = 5f;
  private static final float BADGE_GAP = 3f;
  private static final float BADGE_CHAR_WIDTH = 5.4f;

  /** How close to the right bound a cursor must be to hit the truncated-flight terminator. */
  private static final float TERMINATOR_HIT_PX = 6f;

  private static final float TERMINATOR_WIDTH = 3f;

  private static final ColorRGBA BADGE_BACKGROUND = new ColorRGBA(0.016f, 0.039f, 0.071f, 0.88f);

  private static final float Z_RAIL = 1f;
  private static final float Z_SEGMENT = 2f;
  private static final float Z_TICK = 3f;
  private static final float Z_MARKER = 4f;
  private static final float Z_HALO = 5f;
  private static final float Z_PLAYHEAD = 6f;
  private static final float Z_BADGE = 7f;
  private static final float Z_CAPTURE = 9f;
  private static final float Z_TOOLTIP = 10f;

  private final SimulationClock clock;
  private final Node parent;
  private final Container root;

  private final Label missionName;
  private final Label missionSummary;
  private final Panel missionDot;

  private final PhaseBar phaseBar;
  private final PhaseMarkers markers;
  private final TimelineTooltip tooltip;

  private final Panel playhead;
  private final Panel halo;
  private final Panel badgeFrame;
  private final Panel badgeFill;
  private final Label badgeGlyph;
  private final Label badgeLabel;
  private final Panel capture;

  /**
   * The two chevrons of the pinned badge, built once. They are fields rather than locals of {@link
   * #showPinned} because that method runs every frame for as long as {@code now} is outside the
   * window, and a fresh {@code IconComponent} per frame would allocate a material and a texture
   * binding for a glyph that only ever alternates between two fixed shapes. Either may be {@code
   * null} when its texture is missing, the way every textured element of this package tolerates an
   * absent asset.
   */
  private final IconComponent chevronBefore;

  private final IconComponent chevronAfter;

  private final List<Spatial> ticks = new ArrayList<>();

  private Panel terminator;

  private TimeAxis axis;
  private TrajectoryPolyline trail;
  private boolean truncated;
  private boolean visible;

  /**
   * Which of the two pinned states the badge currently carries, so the chevron is only swapped on
   * an actual change of direction. {@code pinned} is false whenever the solid playhead is the
   * indicator on screen, which makes the first frame of a pinning always set the glyph.
   */
  private boolean pinned;

  private boolean pinnedPast;

  /** The click-vs-drag decision for the capture panel's three intents ({@code NAV-3}). */
  private final ScrubGesture gesture = new ScrubGesture();

  /**
   * Where the drag has put the indicator, in the widget's local track space. A plain {@code float}
   * paired with {@link ScrubGesture#isDragging()} rather than a nullable {@code Float}: the gesture
   * already owns the "is there a drag" bit, and a second, weaker encoding of it would be one more
   * thing to keep in agreement. Meaningless while no drag is in progress, and never read then.
   */
  private float dragX;

  /**
   * The clock's play state captured when the drag began, to be restored when it ends.
   *
   * <p><b>The speed is deliberately not captured with it.</b> START resets the speed to ×1 because
   * it is a "go back to the beginning and look at the launch" command; a scrub is not, and
   * resetting the speed on every drag would silently overwrite a setting the user made — many times
   * per session, since dragging is the cheapest gesture on this widget.
   */
  private boolean resumePlaying;

  /**
   * Builds the widget's shell and its content. Nothing is on screen until {@link #setVisible} is
   * called: the root is not attached to the GUI node here.
   *
   * @param context the application context, for the clock and the GUI graph
   */
  public MissionTimelineWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    this.clock = context.clock();
    this.parent = context.guiGraph().getTimelineNode();

    root = new Container(TimelineStyles.STYLE);
    TbtQuadBackgroundComponent shell = TimelineStyles.capsuleBackground();
    if (shell != null) {
      root.setBackground(shell);
    } else {
      root.setBackground(new QuadBackgroundComponent(new ColorRGBA(0.06f, 0.14f, 0.22f, 0.95f)));
    }
    Vector3f size = new Vector3f(WIDTH, HEIGHT, 0f);
    root.setPreferredSize(size);
    root.setSize(size);

    missionDot = new Panel(DOT_SIZE, DOT_SIZE, TimelineStyles.STYLE);
    missionDot.setBackground(new QuadBackgroundComponent(AppStyles.TL_CYAN));
    missionDot.setSize(missionDot.getPreferredSize());
    missionDot.setLocalTranslation(
        PAD_X, -(HEADER_TOP + (HEADER_HEIGHT - DOT_SIZE) / 2f), Z_SEGMENT);
    root.attachChild(missionDot);

    missionName = header(TimelineStyles.rajdhani(12), 12f, AppStyles.TL_TEXT_MAIN, 180f);
    missionName.setLocalTranslation(PAD_X + DOT_SIZE + 6f, -(HEADER_TOP + 2f), Z_SEGMENT);
    root.attachChild(missionName);

    missionSummary = header(TimelineStyles.mono(10), 10f, AppStyles.TL_TEXT_MUTED, 220f);
    missionSummary.setLocalTranslation(PAD_X + DOT_SIZE + 6f + 120f, -(HEADER_TOP + 4f), Z_SEGMENT);
    root.attachChild(missionSummary);

    // A local, not a field: nothing reads it back. Once attached, root owns it and close() takes
    // it down with everything else under the root.
    Button startButton = buildStartButton();
    root.attachChild(startButton);

    phaseBar = new PhaseBar(root, BAR_TOP, RAIL_TOP, Z_RAIL, Z_SEGMENT);
    markers = new PhaseMarkers(root, MARKER_TOP, Z_MARKER);
    tooltip = new TimelineTooltip(root, Z_TOOLTIP);

    halo = texturedPanel(HALO_SIZE, HALO_SIZE, "playhead-glow.png", null);
    halo.setLocalTranslation(PAD_X, -HALO_TOP, Z_HALO);
    root.attachChild(halo);

    playhead = texturedPanel(PLAYHEAD_WIDTH, PLAYHEAD_HEIGHT, "playhead.png", AppStyles.TL_CYAN);
    playhead.setLocalTranslation(PAD_X, -PLAYHEAD_TOP, Z_PLAYHEAD);
    root.attachChild(playhead);

    badgeFrame = new Panel(40f, BADGE_HEIGHT, TimelineStyles.STYLE);
    badgeFrame.setBackground(new QuadBackgroundComponent(AppStyles.TL_AMBER));
    badgeFrame.setSize(badgeFrame.getPreferredSize());
    badgeFill = new Panel(38f, BADGE_HEIGHT - 2f, TimelineStyles.STYLE);
    badgeFill.setBackground(new QuadBackgroundComponent(BADGE_BACKGROUND));
    badgeFill.setSize(badgeFill.getPreferredSize());
    chevronBefore = chevron("glyph-step-bw.png");
    chevronAfter = chevron("glyph-step-fw.png");
    badgeGlyph = new Label("", TimelineStyles.STYLE);
    badgeGlyph.setBackground(null);
    badgeGlyph.setPreferredSize(new Vector3f(CHEVRON_WIDTH, CHEVRON_HEIGHT, 0f));
    badgeGlyph.setSize(badgeGlyph.getPreferredSize());
    badgeLabel = new Label("", TimelineStyles.STYLE);
    badgeLabel.setFont(TimelineStyles.mono(10));
    badgeLabel.setFontSize(10f);
    badgeLabel.setColor(AppStyles.TL_AMBER);
    badgeLabel.setBackground(null);
    badgeLabel.setTextVAlignment(VAlignment.Center);

    capture = new Panel(TRACK_WIDTH, CAPTURE_HEIGHT, TimelineStyles.STYLE);
    capture.setBackground(new QuadBackgroundComponent(new ColorRGBA(0f, 0f, 0f, 0f)));
    capture.setSize(capture.getPreferredSize());
    capture.setLocalTranslation(PAD_X, -CAPTURE_TOP, Z_CAPTURE);
    root.attachChild(capture);
    installCursorListener();
  }

  /**
   * Rebuilds every element that depends on the mission: segments, markers, graduations, header.
   * Called once per identity change, never per frame.
   *
   * @param entry the followed mission
   * @param ephemeris its ephemeris
   */
  public void bind(MissionEntry entry, MissionEphemeris ephemeris) {
    this.trail = ephemeris.displayTrail();
    this.truncated = !ephemeris.isComplete();
    this.axis = new TimeAxis(ephemeris.startDate(), ephemeris.endDate(), PAD_X, TRACK_WIDTH);

    // getColor() is nullable until the orchestrator assigns a palette entry; the chrome tint is
    // the only sensible stand-in, and it is what the shell already uses.
    ColorRGBA color = entry.getColor() != null ? entry.getColor() : AppStyles.TL_CYAN;
    missionDot.setBackground(new QuadBackgroundComponent(color));
    missionName.setText(entry.mission().getName());
    missionSummary.setText(
        TimeAxis.formatGap(axis.durationSeconds()) + "  ·  " + trail.runs().size() + " phases");

    phaseBar.rebuild(trail, axis, color);
    markers.rebuild(trail, axis, color);
    rebuildTicks();
    rebuildTerminator();
  }

  /**
   * Moves the playhead, or pins it to the bound it has passed.
   *
   * <p><b>A drag owns the indicator outright.</b> While one is in progress the clock is not the
   * authority on where the playhead belongs: seeks go out at {@link ScrubGesture#EMIT_INTERVAL_MS},
   * so a frame landing between two emissions would read a {@code now} that is up to 100 ms of
   * cursor travel stale and pull the playhead back to the last emitted position. At 60 frames for
   * 10 emissions that is not a subtle discrepancy — it is a visible 10 Hz stutter under a cursor
   * moving smoothly. So the drag position wins, and the clock catches up on release.
   *
   * @param now the current simulation date
   */
  public void updateNow(AbsoluteDate now) {
    if (axis == null) {
      return;
    }
    if (gesture.isDragging()) {
      moveIndicator(dragX);
      return;
    }
    double beforeStart = axis.start().durationFrom(now);
    double afterEnd = now.durationFrom(axis.end());
    if (beforeStart > 0) {
      showPinned(false, beforeStart);
    } else if (afterEnd > 0) {
      showPinned(true, afterEnd);
    } else {
      moveIndicator(axis.timeToX(now));
    }
  }

  /**
   * Attaches or detaches the whole widget.
   *
   * @param value whether the track is on screen
   */
  public void setVisible(boolean value) {
    if (value == visible) {
      return;
    }
    visible = value;
    if (visible) {
      parent.attachChild(root);
    } else {
      abandonScrub();
      tooltip.hide();
      root.removeFromParent();
    }
  }

  /**
   * Anchors the widget just above the time capsule, centred like it.
   *
   * @param screenWidth the render surface width in pixels
   * @param capsuleBandTop the y of the top edge of the time capsule, in screen space
   */
  public void layoutAboveCapsule(int screenWidth, float capsuleBandTop) {
    float x = (screenWidth - WIDTH) * 0.5f;
    root.setLocalTranslation(x, capsuleBandTop + AppStyles.HUD_STACK_GAP_PX + HEIGHT, UiLayers.HUD);
  }

  /** The y of this widget's bottom edge in screen space, for aligning the toggle beside it. */
  public float bandBottom() {
    return root.getLocalTranslation().y - HEIGHT;
  }

  @Override
  public void close() {
    abandonScrub();
    tooltip.close();
    phaseBar.close();
    markers.clear();
    root.removeFromParent();
    visible = false;
  }

  // ---------------------------------------------------------------------------------------------
  // Construction helpers
  // ---------------------------------------------------------------------------------------------

  private Label header(BitmapFont font, float fontSize, ColorRGBA color, float width) {
    Label label = new Label("", TimelineStyles.STYLE);
    label.setFont(font);
    label.setFontSize(fontSize);
    label.setColor(color);
    label.setBackground(null);
    label.setTextVAlignment(VAlignment.Center);
    label.setPreferredSize(new Vector3f(width, HEADER_HEIGHT - 4f, 0f));
    label.setSize(label.getPreferredSize());
    return label;
  }

  /**
   * One of the badge's two chevrons, tinted through {@code IconComponent.setColor} the way {@code
   * LiveIndicator} tints its live dot.
   *
   * @param texture the glyph's file name under {@code interface/timeline/}
   * @return the icon, or {@code null} when the texture is missing
   */
  private static IconComponent chevron(String texture) {
    if (TimelineStyles.tex(texture) == null) {
      return null;
    }
    IconComponent icon = new IconComponent("interface/timeline/" + texture);
    icon.setIconSize(new Vector2f(CHEVRON_WIDTH, CHEVRON_HEIGHT));
    icon.setHAlignment(HAlignment.Center);
    icon.setVAlignment(VAlignment.Center);
    icon.setColor(AppStyles.TL_AMBER);
    return icon;
  }

  /**
   * The "go to mission start" button. Its three effects are ordered: seek to the pre-roll, pause,
   * then reset the speed to ×1. The reset only reaches the time capsule because that widget now
   * subscribes to {@code SpeedChanged} (spec §12.1) — before that change it would have left the
   * capsule displaying the previous speed.
   */
  private Button buildStartButton() {
    Button button = new Button("|< START", TimelineStyles.STYLE);
    button.setFont(TimelineStyles.mono(10));
    button.setFontSize(10f);
    button.setColor(AppStyles.TL_CYAN);
    button.setTextHAlignment(HAlignment.Center);
    button.setTextVAlignment(VAlignment.Center);
    button.setPreferredSize(new Vector3f(START_BUTTON_WIDTH, START_BUTTON_HEIGHT, 0f));
    TbtQuadBackgroundComponent skin = TimelineStyles.buttonBackground("btn-hover.png");
    if (skin != null) {
      button.setBackground(skin);
    }
    button.setSize(button.getPreferredSize());
    button.setLocalTranslation(
        WIDTH - PAD_X - START_BUTTON_WIDTH,
        -(HEADER_TOP + (HEADER_HEIGHT - START_BUTTON_HEIGHT) / 2f),
        Z_SEGMENT);
    button.addClickCommands(
        source -> {
          if (axis == null) {
            return;
          }
          clock.seek(axis.start().shiftedBy(-PRE_ROLL_SECONDS));
          clock.pause();
          clock.setSpeed(1.0);
        });
    return button;
  }

  private Panel texturedPanel(float width, float height, String texture, ColorRGBA fallback) {
    Panel panel = new Panel(width, height, TimelineStyles.STYLE);
    Texture2D tex = TimelineStyles.tex(texture);
    if (tex != null) {
      panel.setBackground(new QuadBackgroundComponent(tex));
    } else if (fallback != null) {
      panel.setBackground(new QuadBackgroundComponent(fallback));
    }
    panel.setSize(panel.getPreferredSize());
    return panel;
  }

  /**
   * Graduations and their captions. A minor tick is drawn at the midpoint of each interval, which
   * is what gives the band the density the mock-up shows without adding a second step table.
   */
  private void rebuildTicks() {
    for (Spatial s : ticks) {
      s.removeFromParent();
    }
    ticks.clear();

    List<TimeAxis.Tick> major = axis.majorTicks();
    for (int i = 0; i < major.size(); i++) {
      TimeAxis.Tick tick = major.get(i);
      attachTick(tick.x(), TICK_TOP, TICK_MAJOR_HEIGHT, "tick-major.png", AppStyles.TL_CYAN_SOFT);

      Label label = new Label(tick.label(), TimelineStyles.STYLE);
      // mono(10): share-tech-mono ships at 10/11/12/14 only. mono(9) would silently fall back
      // to Lemur's default font, putting a different typeface on the graduations.
      label.setFont(TimelineStyles.mono(10));
      label.setFontSize(10f);
      label.setColor(AppStyles.TL_TEXT_MUTED);
      label.setBackground(null);
      label.setTextHAlignment(HAlignment.Center);
      label.setTextVAlignment(VAlignment.Center);
      label.setPreferredSize(new Vector3f(TICK_LABEL_WIDTH, TICK_LABEL_HEIGHT, 0f));
      label.setSize(label.getPreferredSize());
      label.setLocalTranslation(tick.x() - TICK_LABEL_WIDTH / 2f, -TICK_LABEL_TOP, Z_TICK);
      root.attachChild(label);
      ticks.add(label);

      if (i + 1 < major.size()) {
        float midpoint = (tick.x() + major.get(i + 1).x()) / 2f;
        attachTick(
            midpoint, TICK_MINOR_TOP, TICK_MINOR_HEIGHT, "tick-minor.png", AppStyles.TL_TEXT_MUTED);
      }
    }
  }

  private void attachTick(float x, float top, float height, String texture, ColorRGBA fallback) {
    Panel tick = texturedPanel(1f, height, texture, fallback);
    tick.setLocalTranslation(x, -top, Z_TICK);
    root.attachChild(tick);
    ticks.add(tick);
  }

  /**
   * The distinct terminator of a truncated flight (§10.3). Without it the track would show a
   * mission running to its scheduled end, which is not what was flown. Nothing else changes: the
   * points that were collected stay drawn.
   */
  private void rebuildTerminator() {
    if (terminator != null) {
      terminator.removeFromParent();
      terminator = null;
    }
    if (!truncated) {
      return;
    }
    terminator = new Panel(TERMINATOR_WIDTH, 10f, TimelineStyles.STYLE);
    terminator.setBackground(new QuadBackgroundComponent(AppStyles.ICE_WARNING));
    terminator.setSize(terminator.getPreferredSize());
    terminator.setLocalTranslation(PAD_X + TRACK_WIDTH - TERMINATOR_WIDTH, -BAR_TOP, Z_TICK);
    root.attachChild(terminator);
  }

  // ---------------------------------------------------------------------------------------------
  // The now indicator
  // ---------------------------------------------------------------------------------------------

  /**
   * Pins the indicator to a bound with the gap it is off by.
   *
   * <p>The chevron points <em>outward</em> and is a frankly different shape from the solid
   * playhead: an indicator pinned to a bound and one sitting exactly on it must be tellable apart,
   * or the widget lies. The badge carries its own opaque plate because an amber gap laid straight
   * over a LEO's final phase — a light green covering 99.7% of the track — is unreadable; and it is
   * anchored on the bound with its width following its text, because a plate sized by eye on the
   * text length runs off the end of the capsule.
   *
   * @param past whether {@code now} is past the end (as opposed to before the start)
   * @param gapSeconds the gap to the bound
   */
  private void showPinned(boolean past, double gapSeconds) {
    playhead.removeFromParent();
    halo.removeFromParent();

    // ASCII hyphen, not U+2212 MINUS SIGN: share-tech-mono-10.fnt carries 224 glyphs and 8722 is
    // not among them (45 and 43 are), so the typographic minus the spec writes would render as a
    // hole and the badge would read "T 4.2 s". Same class of trap as an unbundled font size.
    String text = (past ? "T+" : "T-") + TimeAxis.formatGap(gapSeconds);
    badgeLabel.setText(text);
    float textWidth = text.length() * BADGE_CHAR_WIDTH;
    float badgeWidth = 2 * BADGE_PAD_X + CHEVRON_WIDTH + BADGE_GAP + textWidth;

    if (!pinned || pinnedPast != past) {
      badgeGlyph.setIcon(past ? chevronAfter : chevronBefore);
      pinnedPast = past;
    }

    float left = past ? PAD_X + TRACK_WIDTH - badgeWidth : PAD_X;
    resize(badgeFrame, badgeWidth, BADGE_HEIGHT);
    moveTo(badgeFrame, left, BADGE_TOP, Z_BADGE);
    resize(badgeFill, badgeWidth - 2f, BADGE_HEIGHT - 2f);
    moveTo(badgeFill, left + 1f, BADGE_TOP + 1f, Z_BADGE + 0.1f);
    moveTo(
        badgeGlyph,
        left + BADGE_PAD_X,
        BADGE_TOP + (BADGE_HEIGHT - CHEVRON_HEIGHT) / 2f,
        Z_BADGE + 0.2f);
    resize(badgeLabel, textWidth, BADGE_HEIGHT - 4f);
    moveTo(
        badgeLabel, left + BADGE_PAD_X + CHEVRON_WIDTH + BADGE_GAP, BADGE_TOP + 2f, Z_BADGE + 0.2f);

    attachIfNeeded(badgeFrame);
    attachIfNeeded(badgeFill);
    attachIfNeeded(badgeGlyph);
    attachIfNeeded(badgeLabel);
    pinned = true;
  }

  /**
   * Puts the solid indicator at a track position, unpinning it first if it was pinned to a bound.
   *
   * <p>The clamp is what keeps a drag that runs off the end of the capture band from carrying the
   * playhead out of the rail: {@link TimeAxis#xToTime} already clamps the date it reads, so without
   * the same clamp here the pixel and the date would disagree for as long as the cursor stays
   * outside.
   *
   * @param trackX the position in the widget's local track space, clamped into the track
   */
  private void moveIndicator(float trackX) {
    hidePinned();
    float x = Math.max(axis.x0(), Math.min(trackX, axis.x0() + axis.width()));
    moveTo(playhead, x - PLAYHEAD_WIDTH / 2f, PLAYHEAD_TOP, Z_PLAYHEAD);
    moveTo(halo, x - HALO_SIZE / 2f, HALO_TOP, Z_HALO);
  }

  private void hidePinned() {
    badgeFrame.removeFromParent();
    badgeFill.removeFromParent();
    badgeGlyph.removeFromParent();
    badgeLabel.removeFromParent();
    attachIfNeeded(halo);
    attachIfNeeded(playhead);
    pinned = false;
  }

  private void attachIfNeeded(Spatial spatial) {
    if (spatial.getParent() == null) {
      root.attachChild(spatial);
    }
  }

  private static void moveTo(Spatial spatial, float x, float top, float z) {
    spatial.setLocalTranslation(x, -top, z);
  }

  private static void resize(Panel panel, float width, float height) {
    Vector3f size = new Vector3f(width, height, 0f);
    panel.setPreferredSize(size);
    panel.setSize(size);
  }

  // ---------------------------------------------------------------------------------------------
  // Interaction
  // ---------------------------------------------------------------------------------------------

  /**
   * One transparent capture panel over the whole track carries both gestures.
   *
   * <p>Markers are hit-tested analytically against the cluster positions rather than each carrying
   * its own listener: that is what makes "a marker's content takes precedence over the bar's"
   * (§9.1) a single branch instead of a z-order argument, and it costs one pass over a handful of
   * clusters per cursor move.
   *
   * <p><b>All three intents share one press, and {@link ScrubGesture} is what tells them apart.</b>
   * A rail click, a cluster click and a drag are indistinguishable at button-down — every one of
   * them is a press at some x — so nothing can be decided there and the seek moves to the release.
   * The listener below is therefore a pure translation layer: it turns Lemur events into gesture
   * calls, and gesture verdicts into clock and playhead operations. It holds no state of its own,
   * which is exactly what makes the state machine unit-testable without a render loop.
   *
   * <p>The listener's third parameter is named {@code captured} rather than {@code capture} on
   * purpose: {@code capture} is this widget's own capture panel, and letting the callback parameter
   * shadow it would make any future reference to the panel from inside the listener silently read
   * Lemur's capture spatial instead.
   */
  private void installCursorListener() {
    CursorEventControl.addListenersToSpatial(
        capture,
        new DefaultCursorListener() {
          @Override
          public void cursorMoved(CursorMotionEvent event, Spatial target, Spatial captured) {
            if (axis == null) {
              return;
            }
            float trackX = toTrackX(event.getX());
            switch (gesture.move(trackX, System.currentTimeMillis())) {
              case BEGIN_DRAG -> {
                // Taking the play state here, and only here, is what makes the capture a single
                // event: move() promotes the gesture to a drag exactly once.
                resumePlaying = clock.isPlaying();
                clock.pause();
                dragTo(trackX);
                clock.seek(axis.xToTime(trackX));
                event.setConsumed();
              }
              case EMIT -> {
                dragTo(trackX);
                clock.seek(axis.xToTime(trackX));
                event.setConsumed();
              }
              case TRACK_ONLY -> {
                // Between two emissions the playhead still follows the cursor at frame rate: the
                // throttle bounds the seeks, not the feedback.
                dragTo(trackX);
                event.setConsumed();
              }
              case NONE -> {
                // Plain hover, or a press that has not cleared the drag threshold yet.
              }
            }
            // Anchored on 0 — the capsule's own top edge — so TimelineTooltip clears the WHOLE
            // widget upward and the card sits entirely over the 3D scene.
            //
            // The gap this leaves is not slack, so do not tighten it back down onto the band.
            // Hanging the card downward would put it over the time capsule 8 px below: two HUD
            // surfaces overlapping, each showing different information. Clearing only the capture
            // band would be nearer the cursor but would cover this widget's own header — mission
            // name, duration, phase count, START — which is the identity of the very thing the
            // tooltip is describing, and the context a reader wants precisely while reading a
            // phase name and a date off it. The extra distance costs nothing: the card is
            // anchored to a fixed band, not chasing the pointer vertically.
            //
            // Shown on every branch, drag included: a scrub is the moment one most wants to read
            // the phase and the date being scrubbed to, and the content rules are the hover ones.
            tooltip.show(tooltipLines(trackX), event.getX() - root.getWorldTranslation().x, 0f);
          }

          /**
           * Leaving the 28 px capture band is a normal part of a drag — the pointer is being swept
           * along a rail, not kept inside a box — and Lemur keeps delivering motion to the capture
           * spatial for the whole press, which is what {@code ScrubberTrack}'s own drag already
           * relies on. So the exit only takes the tooltip down when nothing is being dragged;
           * otherwise it would blink off the moment the gesture became interesting.
           */
          @Override
          public void cursorExited(CursorMotionEvent event, Spatial target, Spatial captured) {
            if (!gesture.isDragging()) {
              tooltip.hide();
            }
          }

          @Override
          public void cursorButtonEvent(CursorButtonEvent event, Spatial target, Spatial captured) {
            if (axis == null || event.getButtonIndex() != 0) {
              return;
            }
            float trackX = toTrackX(event.getX());
            if (event.isPressed()) {
              // ScrubGesture.press() documents that it restarts from a drag as readily as from
              // idle, which means a press can legitimately arrive while a drag is still on the
              // books — a button-up dropped because the window lost focus mid-sweep, say. The
              // state machine drops that drag silently; the pause it took is this class's to
              // undo, and undoing it here is what keeps "paused" and "dragging" from ever coming
              // apart. A no-op in the nominal case, where no gesture is in progress.
              abandonScrub();
              gesture.press(trackX, System.currentTimeMillis());
              event.setConsumed();
              return;
            }
            switch (gesture.release()) {
              case CLICK -> {
                seekToClick(trackX);
                event.setConsumed();
              }
              case COMMIT -> {
                // The released x, not the last emitted one: the landing must be where the user let
                // go, to the pixel, however recently the throttle happened to fire.
                clock.seek(axis.xToTime(trackX));
                clock.setPlaying(resumePlaying);
                event.setConsumed();
              }
              case NONE -> {
                // A release with no gesture behind it — a press that started elsewhere, or one
                // this widget has already abandoned.
              }
            }
          }
        });
  }

  /**
   * The discrete {@code NAV-2} click, unchanged by {@code NAV-3}: a cluster under the cursor seeks
   * to its <em>first</em> transition rather than to the clicked pixel (§8), because the point of
   * clicking a group of markers is to land on the event, and the group's own x is an artefact of
   * declustering. Elsewhere, the pixel is the date.
   *
   * @param trackX the released position in the widget's local track space
   */
  private void seekToClick(float trackX) {
    PhaseMarkerCluster cluster = markers.clusterAt(trackX);
    clock.seek(
        cluster != null
            ? trail.timeAt(trail.runs().get(cluster.firstRunIndex()).firstVertex())
            : axis.xToTime(trackX));
  }

  /**
   * Records the drag position and moves the indicator onto it, so {@link #updateNow} keeps drawing
   * it there on the frames that carry no cursor motion.
   *
   * @param trackX the cursor position in the widget's local track space
   */
  private void dragTo(float trackX) {
    dragX = trackX;
    moveIndicator(trackX);
  }

  /**
   * Drops any gesture in progress and undoes the one side effect a drag has on the application: the
   * pause it took to scrub.
   *
   * <p>This is the only exit from a drag that is not a release, and it is not a nicety. The widget
   * leaves the screen whenever the followed mission stops being available — a recompute clears the
   * ephemeris, the focus disarms — and if that lands mid-drag, a clock left paused would be paused
   * by a widget that is no longer on screen to explain it. The user would be left with a frozen
   * simulation and nothing to click to unfreeze it but the transport controls of another capsule.
   */
  private void abandonScrub() {
    if (gesture.cancel()) {
      clock.setPlaying(resumePlaying);
    }
  }

  /** Converts a screen x into the widget's local track space. */
  private float toTrackX(float screenX) {
    return PAD_X + (screenX - capture.getWorldTranslation().x);
  }

  /**
   * What the tooltip says at a given track position: the group's whole list over a cluster, the
   * stage under the cursor otherwise, plus a truncation notice at the right bound of a partial
   * flight.
   */
  private List<String> tooltipLines(float trackX) {
    List<String> lines = new ArrayList<>();
    PhaseMarkerCluster cluster = markers.clusterAt(trackX);
    if (cluster != null) {
      lines.add(cluster.isGroup() ? cluster.size() + " transitions" : "Transition");
      for (int runIndex : cluster.runIndices()) {
        PhaseRun run = trail.runs().get(runIndex);
        AbsoluteDate date = trail.timeAt(run.firstVertex());
        lines.add(
            run.stageName()
                + "  "
                + TimeConverter.formatDate(date)
                + "  "
                + TimeAxis.formatTickLabel(date.durationFrom(axis.start())));
      }
      return lines;
    }

    AbsoluteDate date = axis.xToTime(trackX);
    PhaseRun run = trail.runs().get(trail.runOf(trail.indexUpTo(date)));
    lines.add(run.stageName());
    lines.add(TimeConverter.formatDate(date));
    lines.add(TimeAxis.formatTickLabel(date.durationFrom(axis.start())));
    if (truncated && trackX >= PAD_X + TRACK_WIDTH - TERMINATOR_HIT_PX) {
      lines.add("Flight truncated before the scheduled end");
    }
    return lines;
  }
}
