package com.smousseur.orbitlab.ui.timeline.components;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;

/**
 * Scrubber track cluster: background track, fill bar, tick marks, and playhead.
 *
 * <p>Call {@link #refresh(int)} whenever the speed index changes to move the playhead and resize
 * the fill bar. The playhead position is normalised over {@link SpeedStepper#MIN_INDEX} to {@link
 * SpeedStepper#MAX_INDEX}.
 */
public class ScrubberTrack {

  private static final float TRACK_HEIGHT = 4f;
  private static final float PLAYHEAD_WIDTH = 12f;
  private static final float PLAYHEAD_HEIGHT = 16f;
  private static final int TICK_COUNT = 21;

  private final Panel playhead;
  private final Panel fillPanel;
  private final float trackStartX;
  private final float trackSpan;

  public ScrubberTrack(Container root, float capsuleHeight, float startX, float endX) {
    float trackWidth = Math.max(40f, endX - startX);
    float centerY = capsuleHeight * 0.5f;
    this.trackStartX = startX;
    this.trackSpan = trackWidth;

    // Background track
    Panel track = new Panel(trackWidth, TRACK_HEIGHT, TimelineStyles.STYLE);
    applyBackground(track, TimelineStyles.tex("scrubber-track.png"), 2);
    if (track.getBackground() == null) {
      track.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.20f)));
    }
    track.setSize(track.getPreferredSize());
    track.setLocalTranslation(startX, -(centerY - TRACK_HEIGHT * 0.5f), 1f);
    root.attachChild(track);

    // Fill panel (grows with speed index)
    fillPanel = new Panel(1f, TRACK_HEIGHT, TimelineStyles.STYLE);
    Texture2D fillTex = TimelineStyles.tex("scrubber-fill.png");
    if (fillTex != null) {
      fillPanel.setBackground(new QuadBackgroundComponent(fillTex));
    } else {
      fillPanel.setBackground(new QuadBackgroundComponent(AppStyles.TL_CYAN_SOFT));
    }
    fillPanel.setSize(fillPanel.getPreferredSize());
    fillPanel.setLocalTranslation(startX, -(centerY - TRACK_HEIGHT * 0.5f), 2f);
    root.attachChild(fillPanel);

    // Tick marks
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
      tick.setSize(tick.getPreferredSize());
      tick.setLocalTranslation(startX + xLocal, -(centerY - tickH * 0.5f), 3f);
      root.attachChild(tick);
    }

    // Playhead
    playhead = new Panel(PLAYHEAD_WIDTH, PLAYHEAD_HEIGHT, TimelineStyles.STYLE);
    Texture2D playheadTex = TimelineStyles.tex("playhead.png");
    if (playheadTex != null) {
      playhead.setBackground(new QuadBackgroundComponent(playheadTex));
    } else {
      playhead.setBackground(new QuadBackgroundComponent(AppStyles.TL_CYAN));
    }
    playhead.setSize(playhead.getPreferredSize());
    playhead.setLocalTranslation(startX, -(centerY - PLAYHEAD_HEIGHT * 0.5f), 4f);
    root.attachChild(playhead);
  }

  /** Moves the playhead and resizes the fill bar to match the given speed index. */
  public void refresh(int speedIndex) {
    float normalized =
        (speedIndex - (float) SpeedStepper.MIN_INDEX)
            / (float) (SpeedStepper.MAX_INDEX - SpeedStepper.MIN_INDEX);

    float playheadCenterX = trackStartX + trackSpan * normalized;
    Vector3f p = playhead.getLocalTranslation();
    playhead.setLocalTranslation(playheadCenterX - PLAYHEAD_WIDTH * 0.5f, p.y, p.z);

    float fillWidth = Math.max(1f, trackSpan * normalized);
    Vector3f newFillSize = new Vector3f(fillWidth, TRACK_HEIGHT, 0f);
    fillPanel.setPreferredSize(newFillSize);
    fillPanel.setSize(newFillSize);
    Vector3f f = fillPanel.getLocalTranslation();
    fillPanel.setLocalTranslation(trackStartX, f.y, f.z);
  }

  private static void applyBackground(Panel panel, Texture2D tex, int inset) {
    if (tex == null) return;
    TbtQuadBackgroundComponent bg =
        TbtQuadBackgroundComponent.create(tex, 1f, inset, inset, inset, inset, 1f, false);
    panel.setBackground(bg);
  }

  private static ColorRGBA withAlpha(ColorRGBA c, float alpha) {
    return new ColorRGBA(c.r, c.g, c.b, alpha);
  }
}
