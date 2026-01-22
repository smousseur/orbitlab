package com.smousseur.orbitlab.ui.clock;

import com.jme3.scene.Node;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.core.VersionedReference;
import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import java.util.Objects;

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

  public TimelineWidget(ApplicationContext context) {
    this.clock = Objects.requireNonNull(context, "context must not be null").clock();

    Node timelineNode = context.guiGraph().getTimelineNode();

    this.root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    timelineNode.attachChild(root);

    // Ligne 1: Live / PlayPause / SpeedLabel
    Container controlsRow = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.Even)));

    this.liveButton = controlsRow.addChild(new Button("Live"));
    this.playPauseButton = controlsRow.addChild(new Button("Play"));
    this.speedLabel = controlsRow.addChild(new Label("+1×"));

    // Ligne 2: slider
    this.speedSlider = root.addChild(new Slider("timeline"));
    speedSlider.setDelta(1);
    speedSlider.getModel().setMinimum(-16);
    speedSlider.getModel().setMaximum(16);
    speedSlider.getModel().setValue(0);

    // Ligne 3: date
    this.dateLabel = root.addChild(new Label("—"));

    this.speedValueRef = speedSlider.getModel().createReference();

    wireUiActions();

    // Initial sync (au cas où clock n'est pas à 1× / playing)
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

  /** À appeler périodiquement (ex: depuis un AppState) pour garder l'UI à jour. */
  public void update(float tpf) {
    // TODO: si d'autres systèmes modifient speed/playing, resync ici.
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

    // Resync slider + label depuis la vitesse courante si tu veux (optionnel).
    // Pour l’instant on garde le slider "source of truth" côté UI.
    speedLabel.setText(formatSpeedLabel((int) speedSlider.getModel().getValue()));
  }

  private void refreshPlayPauseText() {
    playPauseButton.setText(clock.isPlaying() ? "Pause" : "Play");
  }

  public void layoutBottomCenter(int screenWidth) {
    var size = root.getPreferredSize(); // largeur/hauteur du widget en pixels
    float x = (screenWidth - size.x) * 0.5f;
    float y = bottomMarginPx + size.y; // Lemur place souvent par le "coin haut-gauche" visuel
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
