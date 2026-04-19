package com.smousseur.orbitlab.ui.clock;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.smousseur.orbitlab.ui.AppStyles;
import java.util.function.IntConsumer;

/**
 * Speed stepper cluster: minus button, speed label, plus button.
 *
 * <p>Anchored to a right edge and laid out right-to-left. Speed constants and conversion methods
 * are {@code static} so {@link ScrubberTrack} and {@link TimelineWidget} can share them.
 */
class SpeedStepper {

  static final int MIN_INDEX = -16;
  static final int MAX_INDEX = 16;

  private static final double[] ABS_SPEED = {
    1, 2, 5, 10, 30, 60, 300, 600, 1800, 3600, 21600, 86400, 432000, 864000, 1728000, 3456000,
    6912000
  };

  private static final float BTN_SIZE = 18f;
  private static final float LABEL_WIDTH = 44f;
  private static final float LABEL_HEIGHT = 16f;
  private static final float GAP = 4f;

  private final Label speedLabel;
  private final float leftEdgeX;

  SpeedStepper(Container root, float capsuleHeight, float rightEnd, IntConsumer onChange) {
    float stepperWidth = BTN_SIZE + GAP + LABEL_WIDTH + GAP + BTN_SIZE;
    float stepperStart = rightEnd - stepperWidth;
    this.leftEdgeX = stepperStart;

    Button minusBtn = makeButton("-");
    minusBtn.addClickCommands(s -> onChange.accept(-1));
    place(minusBtn, root, stepperStart, BTN_SIZE, capsuleHeight, 1f);

    speedLabel = new Label(formatSpeedLabel(0), TimelineStyles.STYLE);
    speedLabel.setFont(TimelineStyles.mono(11));
    speedLabel.setFontSize(11f);
    speedLabel.setColor(AppStyles.TL_CYAN);
    speedLabel.setBackground(null);
    speedLabel.setTextHAlignment(HAlignment.Center);
    speedLabel.setTextVAlignment(VAlignment.Center);
    speedLabel.setPreferredSize(new Vector3f(LABEL_WIDTH, LABEL_HEIGHT, 0f));
    speedLabel.setSize(speedLabel.getPreferredSize());
    place(speedLabel, root, stepperStart + BTN_SIZE + GAP, LABEL_HEIGHT, capsuleHeight, 1f);

    Button plusBtn = makeButton("+");
    plusBtn.addClickCommands(s -> onChange.accept(+1));
    place(plusBtn, root, stepperStart + BTN_SIZE + GAP + LABEL_WIDTH + GAP, BTN_SIZE, capsuleHeight, 1f);
  }

  /** Updates the speed label to reflect the given speed index. */
  void refresh(int speedIndex) {
    speedLabel.setText(formatSpeedLabel(speedIndex));
  }

  /** X coordinate of the stepper's left edge, used to bound the scrubber track on the right. */
  float leftEdge() {
    return leftEdgeX;
  }

  /** Maps a speed index to an absolute speed value with sign. */
  static double mapIndexToSpeed(int i) {
    if (i == 0) return 1.0;
    int a = Math.abs(i);
    return Math.copySign(ABS_SPEED[a], i);
  }

  /** Returns a human-readable label for the given speed index, e.g. "+1×", "+5m/s", "-1h/s". */
  static String formatSpeedLabel(int i) {
    if (i == 0) return "+1×";
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

  private static Button makeButton(String text) {
    Button btn = new Button(text, TimelineStyles.STYLE);
    btn.setPreferredSize(new Vector3f(BTN_SIZE, BTN_SIZE, 0f));
    btn.setFont(TimelineStyles.rajdhani(14));
    btn.setFontSize(14f);
    btn.setInsets(new Insets3f(0, 0, 0, 0));
    btn.setTextHAlignment(HAlignment.Center);
    btn.setTextVAlignment(VAlignment.Center);
    btn.setColor(AppStyles.TL_TEXT_DIM);
    TbtQuadBackgroundComponent bg = TimelineStyles.buttonBackground("btn-hover.png");
    if (bg == null) {
      btn.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.12f)));
    } else {
      btn.setBackground(bg);
    }
    btn.setSize(btn.getPreferredSize());
    return btn;
  }

  private static void place(
      Spatial s, Container root, float x, float height, float capsuleHeight, float z) {
    float y = -(capsuleHeight - height) * 0.5f;
    s.setLocalTranslation(x, y, z);
    root.attachChild(s);
  }

  private static ColorRGBA withAlpha(ColorRGBA c, float alpha) {
    return new ColorRGBA(c.r, c.g, c.b, alpha);
  }
}
