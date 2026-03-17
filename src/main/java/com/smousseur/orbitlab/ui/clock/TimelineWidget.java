package com.smousseur.orbitlab.ui.clock;

import com.jme3.scene.Node;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Slider;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.core.VersionedReference;
import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import java.util.Objects;

/**
 * A Lemur-based GUI widget that provides playback controls and a speed slider for the simulation clock.
 *
 * <p>The widget displays a "Live" button to jump to the current real time, a play/pause toggle,
 * a speed label, a bidirectional speed slider (supporting both forward and reverse playback),
 * and the current simulation date. It is designed to be positioned at the bottom center of the screen.
 *
 * <p>Implements {@link AutoCloseable} to detach itself from the scene graph when no longer needed.
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

  // Simple tuning knob (pixels)
  private static final float bottomMarginPx = 16f;

  private final SimulationClock clock;

  private final Container root;
  private final Button liveButton;
  private final Button playPauseButton;
  private final Label speedLabel;
  private final Slider speedSlider;
  private final Label dateLabel;

  private final VersionedReference<Double> speedValueRef;

  /**
   * Creates and attaches the timeline widget to the GUI scene graph.
   *
   * <p>Builds the control row (Live, Play/Pause, speed label), the speed slider with
   * a range of -16 to +16 discrete steps, and the date label. Wires up button actions
   * and performs an initial synchronization with the simulation clock.
   *
   * @param context the application context providing the simulation clock and GUI scene graph
   */
  public TimelineWidget(ApplicationContext context) {
    this.clock = Objects.requireNonNull(context, "context must not be null").clock();

    Node timelineNode = context.guiGraph().getTimelineNode();

    this.root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    timelineNode.attachChild(root);

    // Row 1: Live / PlayPause / SpeedLabel
    Container controlsRow = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.Even)));

    this.liveButton = controlsRow.addChild(new Button("Live"));
    this.playPauseButton = controlsRow.addChild(new Button("Play"));
    this.speedLabel = controlsRow.addChild(new Label("+1×"));

    // Row 2: slider
    this.speedSlider = root.addChild(new Slider("timeline"));
    speedSlider.setDelta(1);
    speedSlider.getModel().setMinimum(-16);
    speedSlider.getModel().setMaximum(16);
    speedSlider.getModel().setValue(0);

    // Row 3: date
    this.dateLabel = root.addChild(new Label("—"));

    this.speedValueRef = speedSlider.getModel().createReference();

    wireUiActions();

    // Initial sync in case clock is not at 1× / playing
    refreshUiFromClock();
  }

  private void wireUiActions() {
    liveButton.addClickCommands(
        source -> {
          clock.seek(OrekitTime.utcNow());
          clock.setSpeed(1.0);
          clock.setPlaying(true);
          speedSlider.getModel().setValue(0);
          refreshUiFromClock();
        });

    playPauseButton.addClickCommands(
        source -> {
          clock.setPlaying(!clock.isPlaying());
          refreshUiFromClock();
        });
  }

  /**
   * Updates the widget state, typically called once per frame from an AppState.
   *
   * <p>Refreshes the displayed date from the simulation clock and, if the speed slider
   * has been moved, applies the new speed to the clock and updates the speed label.
   *
   * @param tpf time per frame in seconds (unused, but follows JME3 update convention)
   */
  public void update(float tpf) {
    // TODO: if other systems modify speed/playing, resync here.
    dateLabel.setText(TimeConverter.formatDate(clock.now()));
    if (speedValueRef.update()) {
      int index = (int) Math.rint(speedSlider.getModel().getValue());
      double speed = mapIndexToSpeed(index);
      clock.setSpeed(speed);
      speedLabel.setText(formatSpeedLabel(index));
    }
    refreshPlayPauseText();
  }

  private void refreshUiFromClock() {
    refreshPlayPauseText();

    // The slider is the source of truth on the UI side; resync label from it.
    speedLabel.setText(formatSpeedLabel((int) speedSlider.getModel().getValue()));
  }

  private void refreshPlayPauseText() {
    playPauseButton.setText(clock.isPlaying() ? "Pause" : "Play");
  }

  /**
   * Positions the widget at the bottom center of the screen.
   *
   * @param screenWidth the current screen width in pixels, used to center the widget horizontally
   */
  public void layoutBottomCenter(int screenWidth) {
    var size = root.getPreferredSize(); // widget width/height in pixels
    float x = (screenWidth - size.x) * 0.5f;
    float y = bottomMarginPx + size.y; // Lemur positions by the visual top-left corner
    root.setLocalTranslation(x, y, 0f);
  }

  @Override
  public void close() {
    root.removeFromParent();
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
