package com.smousseur.orbitlab.ui.timeline.components;

import com.jme3.input.MouseInput;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.timeline.Mode;
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;

/**
 * Live indicator cluster: animated dot + LIVE/PAUSED label + click-to-reset handler.
 *
 * <p>Clicking the label seeks the clock to real UTC now, sets speed to 1× and starts playback. The
 * {@code onLiveReset} callback is invoked so the parent widget can synchronise derived state (speed
 * index, scrubber position).
 */
public class LiveIndicator {

  private static final float DOT_SIZE = 12f;
  private static final float LABEL_WIDTH = 34f;
  private static final float LABEL_HEIGHT = 16f;
  private static final float TRAILING_GAP = 10f;

  private final IconComponent liveDotIcon;
  private final Label liveLabel;
  private final float rightEdgeX;

  public LiveIndicator(
      Container root,
      float capsuleHeight,
      float startX,
      SimulationClock clock,
      Runnable onLiveReset) {

    this.rightEdgeX = startX + DOT_SIZE + 4f + LABEL_WIDTH + TRAILING_GAP;

    Texture2D dotTex = TimelineStyles.tex("glyph-live-active.png");
    if (dotTex != null) {
      liveDotIcon = new IconComponent("interface/timeline/glyph-live-active.png");
      liveDotIcon.setIconSize(new Vector2f(DOT_SIZE, DOT_SIZE));
      liveDotIcon.setHAlignment(HAlignment.Center);
      liveDotIcon.setVAlignment(VAlignment.Center);
      liveDotIcon.setColor(AppStyles.TL_CYAN);
      Label holder = new Label("", TimelineStyles.STYLE);
      holder.setIcon(liveDotIcon);
      holder.setBackground(null);
      holder.setPreferredSize(new Vector3f(DOT_SIZE, DOT_SIZE, 0f));
      holder.setSize(holder.getPreferredSize());
      place(holder, root, startX, DOT_SIZE, capsuleHeight, 1f);
    } else {
      liveDotIcon = null;
      Panel dot = new Panel(DOT_SIZE, DOT_SIZE, TimelineStyles.STYLE);
      dot.setBackground(new QuadBackgroundComponent(AppStyles.TL_CYAN));
      dot.setSize(dot.getPreferredSize());
      place(dot, root, startX, DOT_SIZE, capsuleHeight, 1f);
    }

    liveLabel = new Label("LIVE", TimelineStyles.STYLE);
    liveLabel.setFont(TimelineStyles.mono(10));
    liveLabel.setFontSize(10f);
    liveLabel.setColor(AppStyles.TL_CYAN);
    liveLabel.setBackground(null);
    liveLabel.setTextVAlignment(VAlignment.Center);
    liveLabel.setPreferredSize(new Vector3f(LABEL_WIDTH, LABEL_HEIGHT, 0f));
    liveLabel.setSize(liveLabel.getPreferredSize());
    place(liveLabel, root, startX + DOT_SIZE + 4f, LABEL_HEIGHT, capsuleHeight, 1f);

    liveLabel.addMouseListener(
        new DefaultMouseListener() {
          @Override
          public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture) {
            if (event.isPressed() && event.getButtonIndex() == MouseInput.BUTTON_LEFT) {
              clock.seek(OrekitTime.utcNow());
              clock.setSpeed(1.0);
              clock.setPlaying(true);
              onLiveReset.run();
              event.setConsumed();
            }
          }
        });
  }

  /** Updates label text and colour to reflect the current playback mode. */
  public void refresh(Mode mode) {
    liveLabel.setText(mode.name());
    ColorRGBA color =
        switch (mode) {
          case LIVE -> AppStyles.TL_CYAN;
          case SEEK -> AppStyles.TL_AMBER;
          case PAUSED -> AppStyles.TL_TEXT_MUTED;
        };
    liveLabel.setColor(color);
    if (liveDotIcon != null) {
      liveDotIcon.setColor(color);
    }
  }

  /** X coordinate of the first free pixel after this cluster (position for the next divider). */
  public float rightEdge() {
    return rightEdgeX;
  }

  private static void place(
      Spatial s, Container root, float x, float height, float capsuleHeight, float z) {
    float y = -(capsuleHeight - height) * 0.5f;
    s.setLocalTranslation(x, y, z);
    root.attachChild(s);
  }
}
