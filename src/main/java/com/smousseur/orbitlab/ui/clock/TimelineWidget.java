package com.smousseur.orbitlab.ui.clock;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.ui.AppStyles;
import java.util.Objects;

/**
 * Lemur-based "Capsule console" timeline rendered at the bottom of the HUD.
 *
 * <p>The widget exposes three functional clusters inside a single glass capsule:
 *
 * <ul>
 *   <li><b>Transport</b> (left) — a LIVE jump-to-now indicator, a ±60 s step-backward button,
 *       a Play/Pause toggle, and a ±60 s step-forward button.
 *   <li><b>Scrubber + speed stepper</b> (centre) — a decorative scrubber track with ticks whose
 *       playhead position mirrors the current speed index; a {@code −} / {@code ×N} / {@code +}
 *       stepper that replaces the former speed slider (step buttons walk through the discrete
 *       speed presets of {@link #ABS_SPEED}).
 *   <li><b>Clock</b> (right) — the current simulation date/time in UTC.
 * </ul>
 *
 * <p>Visual assets live under {@code interface/timeline/} and fonts fall back to Lemur's default
 * when the bundled bitmap fonts are absent. The widget is non-interactive on the scrubber track
 * itself; only the stepper buttons modify the speed.
 */
public class TimelineWidget implements AutoCloseable {
  private static final double[] ABS_SPEED = {
    1, // 0 -> 1× (spécial-cas i==0)
    2, // 1
    5, // 2
    10, // 3
    30, // 4
    60, // 5 -> 1m/s
    300, // 6 -> 5m/s
    600, // 7 -> 10m/s
    1800, // 8 -> 30m/s
    3600, // 9 -> 1h/s
    21600, // 10 -> 6h/s
    86400, // 11 -> 1d/s
    432000, // 12 -> 5d/s
    864000, // 13 -> 10d/s
    1728000, // 14 -> 20d/s
    3456000, // 15 -> 40d/s
    6912000 // 16 -> 80d/s
  };

  private static final int MIN_INDEX = -16;
  private static final int MAX_INDEX = 16;

  private static final double STEP_SECONDS = 60.0;

  // Capsule geometry (must follow the handoff design)
  private static final float CAPSULE_WIDTH = 680f;
  private static final float CAPSULE_HEIGHT = 52f;

  // Cluster widths
  private static final float TRANSPORT_WIDTH = 188f;
  private static final float CLOCK_WIDTH = 170f;
  private static final float STEPPER_WIDTH = 96f;

  // Scrubber geometry
  private static final float TRACK_HEIGHT = 4f;
  private static final int TICK_COUNT = 21;
  private static final float PLAYHEAD_WIDTH = 12f;
  private static final float PLAYHEAD_HEIGHT = 16f;

  // Transport glyphs
  private static final float ICON_SIZE = 10f;
  private static final float BUTTON_SIZE = 24f;
  private static final float STEPPER_BTN_SIZE = 18f;

  private static final float BOTTOM_MARGIN_PX = AppStyles.HUD_MARGIN_PX + 10f;

  private final SimulationClock clock;

  private final Container root;

  // Transport cluster
  private final Container liveIndicator;
  private final IconComponent liveDot;
  private final Label liveLabel;
  private final Button playPauseButton;
  private final IconComponent playPauseIcon;

  // Scrubber
  private final Node scrubberArea;
  private final Panel playhead;
  private final Panel fillPanel;
  private final float trackStartX;
  private final float trackEndX;

  // Stepper
  private final Label speedLabel;

  // Clock
  private final Label dateLabel;
  private final Label utcLabel;

  // State
  private int speedIndex = 0;
  private Mode currentMode = Mode.LIVE;

  private enum Mode {
    LIVE,
    PAUSED,
    SEEK
  }

  /**
   * Creates and attaches the capsule timeline widget to the GUI scene graph.
   *
   * @param context the application context providing the simulation clock and GUI scene graph
   */
  public TimelineWidget(ApplicationContext context) {
    this.clock = Objects.requireNonNull(context, "context must not be null").clock();

    Node timelineNode = context.guiGraph().getTimelineNode();

    this.root = new Container(new BoxLayout(Axis.X, FillMode.None), TimelineStyles.STYLE);
    applyCapsuleBackground(root);
    root.setPreferredSize(new Vector3f(CAPSULE_WIDTH, CAPSULE_HEIGHT, 0f));
    timelineNode.attachChild(root);

    // === LEFT: transport cluster ===
    Container transport =
        new Container(new BoxLayout(Axis.X, FillMode.None), TimelineStyles.STYLE);
    transport.setBackground(null);
    transport.setPreferredSize(new Vector3f(TRANSPORT_WIDTH, CAPSULE_HEIGHT, 0f));
    root.addChild(transport);

    transport.addChild(hSpacer(14f));

    this.liveIndicator =
        new Container(new BoxLayout(Axis.X, FillMode.None), TimelineStyles.STYLE);
    this.liveIndicator.setBackground(null);
    transport.addChild(liveIndicator);

    Label liveDotHost = new Label("", TimelineStyles.STYLE);
    liveDotHost.setBackground(null);
    this.liveDot = makeIcon("glyph-live-active.png", 12f);
    if (liveDot != null) {
      liveDot.setColor(AppStyles.TL_CYAN);
      liveDotHost.setIcon(liveDot);
    }
    liveDotHost.setPreferredSize(new Vector3f(16f, 16f, 0f));
    liveIndicator.addChild(liveDotHost);

    liveIndicator.addChild(hSpacer(4f));

    this.liveLabel = new Label("LIVE", TimelineStyles.STYLE);
    liveLabel.setFont(TimelineStyles.mono(10));
    liveLabel.setFontSize(10f);
    liveLabel.setColor(AppStyles.TL_CYAN);
    liveLabel.setBackground(null);
    liveLabel.setTextVAlignment(VAlignment.Center);
    liveIndicator.addChild(liveLabel);

    transport.addChild(hSpacer(14f));

    Button stepBack = makeIconButton("glyph-step-bw.png");
    stepBack.addClickCommands(s -> seekBySeconds(-STEP_SECONDS));
    transport.addChild(stepBack);

    transport.addChild(hSpacer(4f));

    this.playPauseButton = makeIconButton("glyph-play.png");
    this.playPauseIcon = (IconComponent) playPauseButton.getIcon();
    playPauseButton.addClickCommands(
        s -> {
          clock.setPlaying(!clock.isPlaying());
          refreshMode();
        });
    transport.addChild(playPauseButton);

    transport.addChild(hSpacer(4f));

    Button stepForward = makeIconButton("glyph-step-fw.png");
    stepForward.addClickCommands(s -> seekBySeconds(STEP_SECONDS));
    transport.addChild(stepForward);

    // Divider
    root.addChild(divider());

    // === CENTER: scrubber + speed stepper ===
    Container middle =
        new Container(new BoxLayout(Axis.X, FillMode.None), TimelineStyles.STYLE);
    middle.setBackground(null);
    float middleWidth = CAPSULE_WIDTH - TRANSPORT_WIDTH - CLOCK_WIDTH - 4f;
    middle.setPreferredSize(new Vector3f(middleWidth, CAPSULE_HEIGHT, 0f));
    root.addChild(middle);

    middle.addChild(hSpacer(14f));

    float trackWidth = middleWidth - STEPPER_WIDTH - 14f - 14f - 12f;

    Container scrubberContainer =
        new Container(new BoxLayout(Axis.X, FillMode.None), TimelineStyles.STYLE);
    scrubberContainer.setBackground(null);
    scrubberContainer.setPreferredSize(new Vector3f(trackWidth, CAPSULE_HEIGHT, 0f));
    middle.addChild(scrubberContainer);

    this.scrubberArea = new Node("scrubberOverlay");
    scrubberContainer.attachChild(scrubberArea);
    float centerY = -(CAPSULE_HEIGHT - TRACK_HEIGHT) * 0.5f;

    // Track
    Panel track = new Panel(trackWidth, TRACK_HEIGHT, TimelineStyles.STYLE);
    applyBackground(track, TimelineStyles.tex("scrubber-track.png"), 2);
    if (track.getBackground() == null) {
      track.setBackground(new QuadBackgroundComponent(withAlpha(ColorRGBA.Black, 0.50f)));
    }
    track.setLocalTranslation(0f, centerY, 1f);
    scrubberArea.attachChild(track);

    // Fill (elapsed) — sized dynamically in update()
    this.fillPanel = new Panel(1f, TRACK_HEIGHT, TimelineStyles.STYLE);
    Texture2D fillTex = TimelineStyles.tex("scrubber-fill.png");
    if (fillTex != null) {
      fillPanel.setBackground(new QuadBackgroundComponent(fillTex));
    } else {
      fillPanel.setBackground(new QuadBackgroundComponent(AppStyles.TL_CYAN_SOFT));
    }
    fillPanel.setLocalTranslation(0f, centerY, 2f);
    scrubberArea.attachChild(fillPanel);

    // Ticks
    Texture2D tickMajor = TimelineStyles.tex("tick-major.png");
    Texture2D tickMinor = TimelineStyles.tex("tick-minor.png");
    for (int i = 0; i < TICK_COUNT; i++) {
      boolean major = (i % 5) == 0;
      float x = (trackWidth - 1f) * (i / (float) (TICK_COUNT - 1));
      float tickH = major ? 10f : 6f;
      float tickY = centerY + (TRACK_HEIGHT - tickH) * 0.5f;
      Panel tick = new Panel(1f, tickH, TimelineStyles.STYLE);
      Texture2D tTex = major ? tickMajor : tickMinor;
      if (tTex != null) {
        tick.setBackground(new QuadBackgroundComponent(tTex));
      } else {
        tick.setBackground(
            new QuadBackgroundComponent(major ? AppStyles.TL_CYAN_SOFT : AppStyles.TL_TEXT_MUTED));
      }
      tick.setLocalTranslation(x, tickY, 3f);
      scrubberArea.attachChild(tick);
    }

    // Playhead
    this.playhead = new Panel(PLAYHEAD_WIDTH, PLAYHEAD_HEIGHT, TimelineStyles.STYLE);
    Texture2D playheadTex = TimelineStyles.tex("playhead.png");
    if (playheadTex != null) {
      playhead.setBackground(new QuadBackgroundComponent(playheadTex));
    } else {
      playhead.setBackground(new QuadBackgroundComponent(AppStyles.TL_CYAN));
    }
    float playheadY = centerY + (TRACK_HEIGHT - PLAYHEAD_HEIGHT) * 0.5f;
    playhead.setLocalTranslation(0f, playheadY, 4f);
    scrubberArea.attachChild(playhead);

    this.trackStartX = 0f;
    this.trackEndX = trackWidth;

    middle.addChild(hSpacer(12f));

    // Speed stepper
    Container stepper =
        new Container(new BoxLayout(Axis.X, FillMode.None), TimelineStyles.STYLE);
    stepper.setBackground(null);
    stepper.setPreferredSize(new Vector3f(STEPPER_WIDTH, CAPSULE_HEIGHT, 0f));
    middle.addChild(stepper);

    Button minusBtn = makeStepperButton("-");
    minusBtn.addClickCommands(s -> changeSpeedIndex(-1));
    stepper.addChild(minusBtn);

    stepper.addChild(hSpacer(4f));

    this.speedLabel = new Label(formatSpeedLabel(0), TimelineStyles.STYLE);
    speedLabel.setFont(TimelineStyles.mono(11));
    speedLabel.setFontSize(11f);
    speedLabel.setColor(AppStyles.TL_CYAN);
    speedLabel.setBackground(null);
    speedLabel.setTextHAlignment(HAlignment.Center);
    speedLabel.setTextVAlignment(VAlignment.Center);
    speedLabel.setPreferredSize(new Vector3f(44f, CAPSULE_HEIGHT, 0f));
    stepper.addChild(speedLabel);

    stepper.addChild(hSpacer(4f));

    Button plusBtn = makeStepperButton("+");
    plusBtn.addClickCommands(s -> changeSpeedIndex(+1));
    stepper.addChild(plusBtn);

    middle.addChild(hSpacer(14f));

    // Divider
    root.addChild(divider());

    // === RIGHT: clock ===
    Container clockBox =
        new Container(new BoxLayout(Axis.X, FillMode.None), TimelineStyles.STYLE);
    clockBox.setBackground(null);
    clockBox.setPreferredSize(new Vector3f(CLOCK_WIDTH, CAPSULE_HEIGHT, 0f));
    root.addChild(clockBox);

    clockBox.addChild(hSpacer(16f));

    this.dateLabel = new Label(TimeConverter.formatDate(clock.now()), TimelineStyles.STYLE);
    dateLabel.setFont(TimelineStyles.mono(12));
    dateLabel.setFontSize(12f);
    dateLabel.setColor(AppStyles.TL_CYAN);
    dateLabel.setBackground(null);
    dateLabel.setTextVAlignment(VAlignment.Center);
    clockBox.addChild(dateLabel);

    clockBox.addChild(hSpacer(8f));

    this.utcLabel = new Label("UTC", TimelineStyles.STYLE);
    utcLabel.setFont(TimelineStyles.mono(10));
    utcLabel.setFontSize(10f);
    utcLabel.setColor(AppStyles.TL_TEXT_MUTED);
    utcLabel.setBackground(null);
    utcLabel.setTextVAlignment(VAlignment.Center);
    clockBox.addChild(utcLabel);

    // Initial sync
    wireLiveIndicator();
    refreshMode();
    refreshScrubberFromSpeed();
  }

  private void wireLiveIndicator() {
    liveIndicator.addMouseListener(
        new com.simsilica.lemur.event.DefaultMouseListener() {
          @Override
          public void mouseButtonEvent(
              com.jme3.input.event.MouseButtonEvent event,
              com.jme3.scene.Spatial target,
              com.jme3.scene.Spatial capture) {
            if (event.isPressed()
                && event.getButtonIndex() == com.jme3.input.MouseInput.BUTTON_LEFT) {
              clock.seek(OrekitTime.utcNow());
              clock.setSpeed(1.0);
              clock.setPlaying(true);
              speedIndex = 0;
              speedLabel.setText(formatSpeedLabel(0));
              refreshScrubberFromSpeed();
              refreshMode();
              event.setConsumed();
            }
          }
        });
  }

  /**
   * Updates the widget state, typically called once per frame from an AppState.
   *
   * @param tpf time per frame in seconds (unused, but follows JME3 update convention)
   */
  public void update(float tpf) {
    dateLabel.setText(TimeConverter.formatDate(clock.now()));
    refreshMode();
  }

  private void refreshMode() {
    Mode next = clock.isPlaying() ? Mode.LIVE : Mode.PAUSED;
    if (next != currentMode) {
      currentMode = next;
      liveLabel.setText(currentMode.name());
      ColorRGBA color =
          switch (currentMode) {
            case LIVE -> AppStyles.TL_CYAN;
            case SEEK -> AppStyles.TL_AMBER;
            case PAUSED -> AppStyles.TL_TEXT_MUTED;
          };
      liveLabel.setColor(color);
      if (liveDot != null) {
        liveDot.setColor(color);
      }
    }
    String glyph = clock.isPlaying() ? "glyph-pause.png" : "glyph-play.png";
    Texture2D tex = TimelineStyles.tex(glyph);
    if (tex != null && playPauseIcon != null) {
      playPauseIcon.setImageTexture(tex);
    }
  }

  private void refreshScrubberFromSpeed() {
    float normalized =
        (speedIndex - (float) MIN_INDEX) / (float) (MAX_INDEX - MIN_INDEX); // 0..1
    float trackSpan = trackEndX - trackStartX;
    float playheadX = trackStartX + trackSpan * normalized - PLAYHEAD_WIDTH * 0.5f;
    Vector3f p = playhead.getLocalTranslation();
    playhead.setLocalTranslation(playheadX, p.y, p.z);

    float fillWidth = Math.max(1f, trackSpan * normalized);
    fillPanel.setPreferredSize(new Vector3f(fillWidth, TRACK_HEIGHT, 0f));
    Vector3f f = fillPanel.getLocalTranslation();
    fillPanel.setLocalTranslation(trackStartX, f.y, f.z);
  }

  private void changeSpeedIndex(int delta) {
    int next = clamp(speedIndex + delta, MIN_INDEX, MAX_INDEX);
    if (next == speedIndex) {
      return;
    }
    speedIndex = next;
    clock.setSpeed(mapIndexToSpeed(speedIndex));
    speedLabel.setText(formatSpeedLabel(speedIndex));
    refreshScrubberFromSpeed();
  }

  private void seekBySeconds(double deltaSeconds) {
    clock.seek(clock.now().shiftedBy(deltaSeconds));
  }

  /**
   * Positions the widget at the bottom center of the screen.
   *
   * @param screenWidth the current screen width in pixels, used to center the widget horizontally
   */
  public void layoutBottomCenter(int screenWidth) {
    Vector3f size = root.getPreferredSize();
    float x = (screenWidth - size.x) * 0.5f;
    float y = BOTTOM_MARGIN_PX + size.y;
    root.setLocalTranslation(x, y, 0f);
  }

  @Override
  public void close() {
    root.removeFromParent();
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private static Container hSpacer(float width) {
    Container c = new Container();
    c.setBackground(null);
    c.setPreferredSize(new Vector3f(width, 1f, 0f));
    return c;
  }

  private Panel divider() {
    Panel d = new Panel(1f, CAPSULE_HEIGHT - 16f, TimelineStyles.STYLE);
    Texture2D tex = TimelineStyles.tex("divider.png");
    if (tex != null) {
      d.setBackground(new QuadBackgroundComponent(tex));
    } else {
      d.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.30f)));
    }
    return d;
  }

  private void applyCapsuleBackground(Container c) {
    TbtQuadBackgroundComponent capsule = TimelineStyles.capsuleBackground();
    if (capsule != null) {
      c.setBackground(capsule);
    } else {
      c.setBackground(new QuadBackgroundComponent(withAlpha(ColorRGBA.Black, 0.70f)));
    }
  }

  private static void applyBackground(Panel panel, Texture2D tex, int inset) {
    if (tex == null) {
      return;
    }
    TbtQuadBackgroundComponent bg =
        TbtQuadBackgroundComponent.create(tex, 1f, inset, inset, inset, inset, 1f, true);
    panel.setBackground(bg);
  }

  private IconComponent makeIcon(String textureName, float size) {
    // Pre-check the texture so we don't crash when the interface/ folder is absent.
    Texture2D preloaded = TimelineStyles.tex(textureName);
    if (preloaded == null) {
      return null;
    }
    IconComponent icon = new IconComponent("interface/timeline/" + textureName);
    icon.setIconSize(new Vector2f(size, size));
    icon.setHAlignment(HAlignment.Center);
    icon.setVAlignment(VAlignment.Center);
    return icon;
  }

  private Button makeIconButton(String iconName) {
    Button btn = new Button("", TimelineStyles.STYLE);
    btn.setPreferredSize(new Vector3f(BUTTON_SIZE, BUTTON_SIZE, 0f));
    btn.setTextHAlignment(HAlignment.Center);
    btn.setTextVAlignment(VAlignment.Center);
    TbtQuadBackgroundComponent bg = TimelineStyles.buttonBackground("btn-hover.png");
    if (bg != null) {
      btn.setBackground(bg);
    } else {
      btn.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.08f)));
    }
    IconComponent icon = makeIcon(iconName, ICON_SIZE);
    if (icon != null) {
      icon.setColor(AppStyles.TL_TEXT_DIM);
      btn.setIcon(icon);
    }
    return btn;
  }

  private Button makeStepperButton(String text) {
    Button btn = new Button(text, TimelineStyles.STYLE);
    btn.setPreferredSize(new Vector3f(STEPPER_BTN_SIZE, STEPPER_BTN_SIZE, 0f));
    btn.setFont(TimelineStyles.rajdhani(14));
    btn.setFontSize(14f);
    btn.setTextHAlignment(HAlignment.Center);
    btn.setTextVAlignment(VAlignment.Center);
    btn.setColor(AppStyles.TL_TEXT_DIM);
    TbtQuadBackgroundComponent bg = TimelineStyles.buttonBackground("btn-hover.png");
    if (bg == null) {
      btn.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.08f)));
    } else {
      btn.setBackground(bg);
    }
    return btn;
  }

  private static ColorRGBA withAlpha(ColorRGBA c, float alpha) {
    return new ColorRGBA(c.r, c.g, c.b, alpha);
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private static double mapIndexToSpeed(int i) {
    if (i == 0) {
      return 1.0;
    }
    int a = Math.abs(i);
    double abs = ABS_SPEED[a];
    return Math.copySign(abs, i);
  }

  private static String formatSpeedLabel(int i) {
    if (i == 0) {
      return "+1×";
    }
    int a = Math.abs(i);
    String sign = i < 0 ? "-" : "+";
    return sign
        + switch (a) {
          case 1 -> "2×";
          case 2 -> "5×";
          case 3 -> "10×";
          case 4 -> "30×";
          case 5 -> "1m/s";
          case 6 -> "5m/s";
          case 7 -> "10m/s";
          case 8 -> "30m/s";
          case 9 -> "1h/s";
          case 10 -> "6h/s";
          case 11 -> "1d/s";
          case 12 -> "5d/s";
          case 13 -> "10d/s";
          case 14 -> "20d/s";
          case 15 -> "40d/s";
          case 16 -> "80d/s";
          default -> "?";
        };
  }
}
