package com.smousseur.orbitlab.ui.clock;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.ui.AppStyles;

/**
 * Transport controls cluster: step-back, play/pause, step-forward buttons.
 *
 * <p>Step buttons seek the clock by {@value #STEP_SECONDS} seconds. Play/pause toggles the clock
 * playing state.
 */
class TransportControls {

  private static final float BUTTON_SIZE = 24f;
  private static final float ICON_SIZE = 12f;
  private static final float BUTTON_GAP = 4f;
  private static final float TRAILING_GAP = 10f;
  private static final double STEP_SECONDS = 60.0;

  private final Button playPauseButton;
  private final IconComponent playPauseIcon;
  private final float rightEdgeX;

  TransportControls(
      Container root, float capsuleHeight, float startX, SimulationClock clock) {

    this.rightEdgeX = startX + (BUTTON_SIZE + BUTTON_GAP) * 2 + BUTTON_SIZE + TRAILING_GAP;

    Button stepBack = makeButton("glyph-step-bw.png", "<<");
    stepBack.addClickCommands(s -> clock.seek(clock.now().shiftedBy(-STEP_SECONDS)));
    place(stepBack, root, startX, BUTTON_SIZE, capsuleHeight, 1f);

    playPauseButton = makeButton("glyph-play.png", ">");
    playPauseButton.addClickCommands(s -> clock.setPlaying(!clock.isPlaying()));
    place(playPauseButton, root, startX + BUTTON_SIZE + BUTTON_GAP, BUTTON_SIZE, capsuleHeight, 1f);
    playPauseIcon = (playPauseButton.getIcon() instanceof IconComponent ic) ? ic : null;

    Button stepForward = makeButton("glyph-step-fw.png", ">>");
    stepForward.addClickCommands(s -> clock.seek(clock.now().shiftedBy(STEP_SECONDS)));
    place(
        stepForward,
        root,
        startX + (BUTTON_SIZE + BUTTON_GAP) * 2,
        BUTTON_SIZE,
        capsuleHeight,
        1f);
  }

  /** Swaps the play/pause icon to match the current playing state. */
  void refresh(boolean isPlaying) {
    String glyph = isPlaying ? "glyph-pause.png" : "glyph-play.png";
    Texture2D tex = TimelineStyles.tex(glyph);
    if (tex != null && playPauseIcon != null) {
      playPauseIcon.setImageTexture(tex);
    } else if (playPauseIcon == null) {
      playPauseButton.setText(isPlaying ? "||" : ">");
    }
  }

  /** X coordinate of the first free pixel after this cluster (position for the next divider). */
  float rightEdge() {
    return rightEdgeX;
  }

  private static Button makeButton(String iconName, String fallbackText) {
    IconComponent icon = makeIcon(iconName);
    String text = (icon == null) ? fallbackText : "";
    Button btn = new Button(text, TimelineStyles.STYLE);
    btn.setPreferredSize(new Vector3f(BUTTON_SIZE, BUTTON_SIZE, 0f));
    btn.setFont(TimelineStyles.rajdhani(12));
    btn.setFontSize(12f);
    btn.setColor(AppStyles.TL_TEXT_MAIN);
    btn.setTextHAlignment(HAlignment.Center);
    btn.setTextVAlignment(VAlignment.Center);
    TbtQuadBackgroundComponent bg = TimelineStyles.buttonBackground("btn-hover.png");
    if (bg != null) {
      btn.setBackground(bg);
    } else {
      btn.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.22f)));
    }
    if (icon != null) {
      icon.setColor(AppStyles.TL_TEXT_DIM);
      btn.setIcon(icon);
    }
    btn.setSize(btn.getPreferredSize());
    return btn;
  }

  private static IconComponent makeIcon(String textureName) {
    if (TimelineStyles.tex(textureName) == null) {
      return null;
    }
    IconComponent icon = new IconComponent("interface/timeline/" + textureName);
    icon.setIconSize(new Vector2f(ICON_SIZE, ICON_SIZE));
    icon.setHAlignment(HAlignment.Center);
    icon.setVAlignment(VAlignment.Center);
    return icon;
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
