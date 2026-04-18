package com.smousseur.orbitlab.ui.clock;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
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
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.ui.AppStyles;
import java.util.Objects;

/**
 * Lemur-based "Capsule console" timeline rendered at the bottom of the HUD.
 *
 * <p>All children are placed with absolute positioning inside the capsule so every element stays
 * visually centred along the vertical axis regardless of its height.
 */
public class TimelineWidget implements AutoCloseable {
  private static final double[] ABS_SPEED = {
    1, 2, 5, 10, 30, 60, 300, 600, 1800, 3600, 21600, 86400, 432000, 864000, 1728000, 3456000,
    6912000
  };

  private static final int MIN_INDEX = -16;
  private static final int MAX_INDEX = 16;

  private static final double STEP_SECONDS = 60.0;

  private static final float CAPSULE_WIDTH = 600f;
  private static final float CAPSULE_HEIGHT = 52f;
  private static final float CAPSULE_PAD_X = 14f;

  private static final float TRACK_HEIGHT = 4f;
  private static final int TICK_COUNT = 21;
  private static final float PLAYHEAD_WIDTH = 12f;
  private static final float PLAYHEAD_HEIGHT = 16f;

  private static final float ICON_SIZE = 12f;
  private static final float BUTTON_SIZE = 24f;
  private static final float STEPPER_BTN_SIZE = 18f;
  private static final float DIVIDER_HEIGHT = CAPSULE_HEIGHT - 20f;

  private static final float BOTTOM_MARGIN_PX = AppStyles.HUD_MARGIN_PX + 10f;

  private final SimulationClock clock;

  private final Container root;

  private final Panel liveDotPanel;
  private final IconComponent liveDotIcon;
  private final Label liveLabel;
  private final Button playPauseButton;
  private final IconComponent playPauseIcon;

  private final Panel playhead;
  private final Panel fillPanel;
  private final float trackStartX;
  private final float trackEndX;

  private final Label speedLabel;

  private final Label dateLabel;

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

    this.root = new Container(TimelineStyles.STYLE);
    applyCapsuleBackground(root);
    root.setPreferredSize(new Vector3f(CAPSULE_WIDTH, CAPSULE_HEIGHT, 0f));
    timelineNode.attachChild(root);

    float cursorX = CAPSULE_PAD_X;

    // === LIVE indicator ===
    float liveDotSize = 12f;
    this.liveDotPanel = new Panel(liveDotSize, liveDotSize, TimelineStyles.STYLE);
    this.liveDotIcon = makeIcon("glyph-live-active.png", liveDotSize);
    if (liveDotIcon != null) {
      liveDotIcon.setColor(AppStyles.TL_CYAN);
      liveDotPanel.setBackground(null);
      Label holder = new Label("", TimelineStyles.STYLE);
      holder.setIcon(liveDotIcon);
      holder.setBackground(null);
      holder.setPreferredSize(new Vector3f(liveDotSize, liveDotSize, 0f));
      placeCentered(holder, cursorX, liveDotSize, 1f);
    } else {
      liveDotPanel.setBackground(new QuadBackgroundComponent(AppStyles.TL_CYAN));
      placeCentered(liveDotPanel, cursorX, liveDotSize, 1f);
    }
    cursorX += liveDotSize + 4f;

    float liveLabelWidth = 34f;
    this.liveLabel = new Label("LIVE", TimelineStyles.STYLE);
    liveLabel.setFont(TimelineStyles.mono(10));
    liveLabel.setFontSize(10f);
    liveLabel.setColor(AppStyles.TL_CYAN);
    liveLabel.setBackground(null);
    liveLabel.setTextVAlignment(VAlignment.Center);
    liveLabel.setPreferredSize(new Vector3f(liveLabelWidth, 16f, 0f));
    placeCentered(liveLabel, cursorX, 16f, 1f);
    wireLiveIndicator(liveLabel);
    cursorX += liveLabelWidth + 10f;

    // Divider 1
    placeDivider(cursorX);
    cursorX += 1f + 10f;

    // === Transport buttons ===
    Button stepBack = makeIconButton("glyph-step-bw.png");
    stepBack.addClickCommands(s -> seekBySeconds(-STEP_SECONDS));
    placeCentered(stepBack, cursorX, BUTTON_SIZE, 1f);
    cursorX += BUTTON_SIZE + 4f;

    this.playPauseButton = makeIconButton("glyph-play.png");
    this.playPauseIcon =
        (playPauseButton.getIcon() instanceof IconComponent ic) ? ic : null;
    playPauseButton.addClickCommands(
        s -> {
          clock.setPlaying(!clock.isPlaying());
          refreshMode();
        });
    placeCentered(playPauseButton, cursorX, BUTTON_SIZE, 1f);
    cursorX += BUTTON_SIZE + 4f;

    Button stepForward = makeIconButton("glyph-step-fw.png");
    stepForward.addClickCommands(s -> seekBySeconds(STEP_SECONDS));
    placeCentered(stepForward, cursorX, BUTTON_SIZE, 1f);
    cursorX += BUTTON_SIZE + 10f;

    // Divider 2
    placeDivider(cursorX);
    cursorX += 1f + 10f;

    // === Right-hand cluster (clock) — reserve space from the right edge ===
    float clockLabelWidth = 86f;
    float utcLabelWidth = 24f;
    float rightEnd = CAPSULE_WIDTH - CAPSULE_PAD_X;
    float utcX = rightEnd - utcLabelWidth;
    float dateX = utcX - 4f - clockLabelWidth;

    Label utcLabel = new Label("UTC", TimelineStyles.STYLE);
    utcLabel.setFont(TimelineStyles.mono(10));
    utcLabel.setFontSize(10f);
    utcLabel.setColor(AppStyles.TL_TEXT_MUTED);
    utcLabel.setBackground(null);
    utcLabel.setTextVAlignment(VAlignment.Center);
    utcLabel.setPreferredSize(new Vector3f(utcLabelWidth, 14f, 0f));
    placeCentered(utcLabel, utcX, 14f, 1f);

    this.dateLabel = new Label(TimeConverter.formatDate(clock.now()), TimelineStyles.STYLE);
    dateLabel.setFont(TimelineStyles.mono(12));
    dateLabel.setFontSize(12f);
    dateLabel.setColor(AppStyles.TL_CYAN);
    dateLabel.setBackground(null);
    dateLabel.setTextHAlignment(HAlignment.Right);
    dateLabel.setTextVAlignment(VAlignment.Center);
    dateLabel.setPreferredSize(new Vector3f(clockLabelWidth, 16f, 0f));
    placeCentered(dateLabel, dateX, 16f, 1f);

    // Divider 3 (right of the scrubber)
    float divider3X = dateX - 10f - 1f;
    placeDivider(divider3X);

    // === Stepper (right side of middle region) ===
    float stepperRight = divider3X - 10f;
    float speedLabelWidth = 44f;
    float stepperWidth = STEPPER_BTN_SIZE + 4f + speedLabelWidth + 4f + STEPPER_BTN_SIZE;
    float stepperStart = stepperRight - stepperWidth;

    Button minusBtn = makeStepperButton("-");
    minusBtn.addClickCommands(s -> changeSpeedIndex(-1));
    placeCentered(minusBtn, stepperStart, STEPPER_BTN_SIZE, 1f);

    this.speedLabel = new Label(formatSpeedLabel(0), TimelineStyles.STYLE);
    speedLabel.setFont(TimelineStyles.mono(11));
    speedLabel.setFontSize(11f);
    speedLabel.setColor(AppStyles.TL_CYAN);
    speedLabel.setBackground(null);
    speedLabel.setTextHAlignment(HAlignment.Center);
    speedLabel.setTextVAlignment(VAlignment.Center);
    speedLabel.setPreferredSize(new Vector3f(speedLabelWidth, 16f, 0f));
    placeCentered(speedLabel, stepperStart + STEPPER_BTN_SIZE + 4f, 16f, 1f);

    Button plusBtn = makeStepperButton("+");
    plusBtn.addClickCommands(s -> changeSpeedIndex(+1));
    placeCentered(
        plusBtn,
        stepperStart + STEPPER_BTN_SIZE + 4f + speedLabelWidth + 4f,
        STEPPER_BTN_SIZE,
        1f);

    // === Scrubber (fills remaining middle space) ===
    float scrubberStart = cursorX;
    float scrubberEnd = stepperStart - 10f;
    float trackWidth = Math.max(40f, scrubberEnd - scrubberStart);
    float centerY = CAPSULE_HEIGHT * 0.5f;

    Panel track = new Panel(trackWidth, TRACK_HEIGHT, TimelineStyles.STYLE);
    applyBackground(track, TimelineStyles.tex("scrubber-track.png"), 2);
    if (track.getBackground() == null) {
      track.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.20f)));
    }
    track.setLocalTranslation(scrubberStart, -(centerY - TRACK_HEIGHT * 0.5f), 1f);
    root.attachChild(track);

    this.fillPanel = new Panel(1f, TRACK_HEIGHT, TimelineStyles.STYLE);
    Texture2D fillTex = TimelineStyles.tex("scrubber-fill.png");
    if (fillTex != null) {
      fillPanel.setBackground(new QuadBackgroundComponent(fillTex));
    } else {
      fillPanel.setBackground(new QuadBackgroundComponent(AppStyles.TL_CYAN_SOFT));
    }
    fillPanel.setLocalTranslation(scrubberStart, -(centerY - TRACK_HEIGHT * 0.5f), 2f);
    root.attachChild(fillPanel);

    Texture2D tickMajor = TimelineStyles.tex("tick-major.png");
    Texture2D tickMinor = TimelineStyles.tex("tick-minor.png");
    for (int i = 0; i < TICK_COUNT; i++) {
      boolean major = (i % 5) == 0;
      float xLocal = (trackWidth - 1f) * (i / (float) (TICK_COUNT - 1));
      float tickH = major ? 10f : 6f;
      Panel tick = new Panel(1f, tickH, TimelineStyles.STYLE);
      Texture2D tTex = major ? tickMajor : tickMinor;
      if (tTex != null) {
        tick.setBackground(new QuadBackgroundComponent(tTex));
      } else {
        tick.setBackground(
            new QuadBackgroundComponent(major ? AppStyles.TL_CYAN_SOFT : AppStyles.TL_TEXT_MUTED));
      }
      tick.setLocalTranslation(scrubberStart + xLocal, -(centerY - tickH * 0.5f), 3f);
      root.attachChild(tick);
    }

    this.playhead = new Panel(PLAYHEAD_WIDTH, PLAYHEAD_HEIGHT, TimelineStyles.STYLE);
    Texture2D playheadTex = TimelineStyles.tex("playhead.png");
    if (playheadTex != null) {
      playhead.setBackground(new QuadBackgroundComponent(playheadTex));
    } else {
      playhead.setBackground(new QuadBackgroundComponent(AppStyles.TL_CYAN));
    }
    playhead.setLocalTranslation(scrubberStart, -(centerY - PLAYHEAD_HEIGHT * 0.5f), 4f);
    root.attachChild(playhead);

    this.trackStartX = scrubberStart;
    this.trackEndX = scrubberStart + trackWidth;

    refreshMode();
    refreshScrubberFromSpeed();
  }

  private void placeCentered(com.jme3.scene.Spatial spatial, float x, float height, float z) {
    float y = -(CAPSULE_HEIGHT - height) * 0.5f;
    spatial.setLocalTranslation(x, y, z);
    root.attachChild(spatial);
  }

  private void placeDivider(float x) {
    Panel d = new Panel(1f, DIVIDER_HEIGHT, TimelineStyles.STYLE);
    Texture2D tex = TimelineStyles.tex("divider.png");
    if (tex != null) {
      d.setBackground(new QuadBackgroundComponent(tex));
    } else {
      d.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.40f)));
    }
    float y = -(CAPSULE_HEIGHT - DIVIDER_HEIGHT) * 0.5f;
    d.setLocalTranslation(x, y, 1f);
    root.attachChild(d);
  }

  private void wireLiveIndicator(Label liveClickTarget) {
    liveClickTarget.addMouseListener(
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
      if (liveDotIcon != null) {
        liveDotIcon.setColor(color);
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
    float playheadCenterX = trackStartX + trackSpan * normalized;
    Vector3f p = playhead.getLocalTranslation();
    playhead.setLocalTranslation(playheadCenterX - PLAYHEAD_WIDTH * 0.5f, p.y, p.z);

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

  private void applyCapsuleBackground(Container c) {
    TbtQuadBackgroundComponent capsule = TimelineStyles.capsuleBackground();
    if (capsule != null) {
      c.setBackground(capsule);
    } else {
      c.setBackground(
          new QuadBackgroundComponent(new ColorRGBA(0.05f, 0.10f, 0.16f, 0.88f)));
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
      btn.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.12f)));
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
      btn.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.12f)));
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
