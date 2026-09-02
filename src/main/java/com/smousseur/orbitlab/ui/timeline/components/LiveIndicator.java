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
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;
import org.orekit.time.AbsoluteDate;

/**
 * Live indicator cluster: a dot lit only while the clock shows real time, a constant {@code LIVE}
 * label, and a click-to-resynchronise target covering both.
 *
 * <p>The cluster answers one question — is the displayed time the real time now? — and nothing
 * else. Whether the clock is advancing is the play/pause button's answer to give; an indicator that
 * reported the pause as well left the capsule with two readings of the same state and no way to
 * tell which one spoke about the live feed.
 *
 * <p>Clicking anywhere on the cluster seeks the clock to real UTC now, sets speed to 1× and starts
 * playback. The {@code onLiveReset} callback is invoked so the parent widget can synchronise
 * derived state (speed index, scrubber position).
 */
public class LiveIndicator {

  private static final float DOT_SIZE = 12f;
  private static final float DOT_LABEL_GAP = 4f;
  private static final float LABEL_WIDTH = 34f;
  private static final float LABEL_HEIGHT = 16f;
  private static final float TRAILING_GAP = 10f;

  private static final String LABEL_TEXT = "LIVE";

  /**
   * Drift tolerated between simulation time and wall-clock UTC before the dot goes dark, in
   * seconds. The clock never resynchronises on the wall clock — {@link
   * SimulationClock#update(double)} only shifts by {@code tpf × speed} — so a frame the renderer
   * misses is lost for good and the two times separate for ever. Two seconds absorbs the frame-time
   * accumulation of an ordinary session while still catching the stall that genuinely leaves the
   * display behind.
   */
  private static final double LIVE_TOLERANCE_SECONDS = 2.0;

  private final IconComponent liveDotIcon;
  private final QuadBackgroundComponent liveDotQuad;
  private final Label liveLabel;
  private final float rightEdgeX;

  public LiveIndicator(
      Container root,
      float capsuleHeight,
      float startX,
      SimulationClock clock,
      Runnable onLiveReset) {

    float labelX = startX + DOT_SIZE + DOT_LABEL_GAP;
    float clusterWidth = DOT_SIZE + DOT_LABEL_GAP + LABEL_WIDTH;
    this.rightEdgeX = labelX + LABEL_WIDTH + TRAILING_GAP;

    Texture2D dotTex = TimelineStyles.tex("glyph-live-active.png");
    if (dotTex != null) {
      liveDotIcon = new IconComponent("interface/timeline/glyph-live-active.png");
      liveDotIcon.setIconSize(new Vector2f(DOT_SIZE, DOT_SIZE));
      liveDotIcon.setHAlignment(HAlignment.Center);
      liveDotIcon.setVAlignment(VAlignment.Center);
      liveDotIcon.setColor(AppStyles.TL_CYAN);
      liveDotQuad = null;
      Label holder = new Label("", TimelineStyles.STYLE);
      holder.setIcon(liveDotIcon);
      holder.setBackground(null);
      holder.setPreferredSize(new Vector3f(DOT_SIZE, DOT_SIZE, 0f));
      holder.setSize(holder.getPreferredSize());
      place(holder, root, startX, DOT_SIZE, capsuleHeight, 1f);
    } else {
      liveDotIcon = null;
      liveDotQuad = new QuadBackgroundComponent(AppStyles.TL_CYAN);
      Panel dot = new Panel(DOT_SIZE, DOT_SIZE, TimelineStyles.STYLE);
      dot.setBackground(liveDotQuad);
      dot.setSize(dot.getPreferredSize());
      place(dot, root, startX, DOT_SIZE, capsuleHeight, 1f);
    }

    liveLabel = new Label(LABEL_TEXT, TimelineStyles.STYLE);
    liveLabel.setFont(TimelineStyles.mono(10));
    liveLabel.setFontSize(10f);
    liveLabel.setColor(AppStyles.TL_CYAN);
    liveLabel.setBackground(null);
    liveLabel.setTextVAlignment(VAlignment.Center);
    liveLabel.setPreferredSize(new Vector3f(LABEL_WIDTH, LABEL_HEIGHT, 0f));
    liveLabel.setSize(liveLabel.getPreferredSize());
    place(liveLabel, root, labelX, LABEL_HEIGHT, capsuleHeight, 1f);

    // Transparent pick target over dot and label: the glyphs alone are unreliable to hit, and the
    // dot has to answer the click too. Same device as ClockDisplay's date box.
    Panel hitTarget = new Panel(clusterWidth, LABEL_HEIGHT, TimelineStyles.STYLE);
    hitTarget.setBackground(new QuadBackgroundComponent(new ColorRGBA(0f, 0f, 0f, 0f)));
    hitTarget.setSize(hitTarget.getPreferredSize());
    place(hitTarget, root, startX, LABEL_HEIGHT, capsuleHeight, 3f);

    hitTarget.addMouseListener(
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

  /**
   * Whether the clock is showing real time now: playing forward at 1× and still within {@link
   * #LIVE_TOLERANCE_SECONDS} of the wall clock.
   *
   * <p>Playing at 1× is not enough on its own, which is why the drift is measured: replaying a
   * scrubbed-to date at 1× advances the clock exactly like the live feed while showing a time that
   * is not now. The speed test is subsumed by the drift test in the long run — at 60× the tolerance
   * is left behind in 34 ms — but it keeps the frames right after a speed change honest rather than
   * merely quick to correct.
   *
   * @param playing whether the clock advances on update
   * @param speed the clock speed, in simulated seconds per application second
   * @param simTime the simulation time
   * @param wallTime real UTC now
   * @return {@code true} when the dot should be lit
   */
  public static boolean isLive(
      boolean playing, double speed, AbsoluteDate simTime, AbsoluteDate wallTime) {
    return playing
        && Double.compare(speed, 1.0) == 0
        && Math.abs(simTime.durationFrom(wallTime)) <= LIVE_TOLERANCE_SECONDS;
  }

  /**
   * Lights or dims the cluster.
   *
   * @param live whether the clock is showing real time now, as decided by {@link #isLive}
   */
  public void refresh(boolean live) {
    ColorRGBA color = live ? AppStyles.TL_CYAN : AppStyles.TL_TEXT_MUTED;
    liveLabel.setColor(color);
    if (liveDotIcon != null) {
      liveDotIcon.setColor(color);
    } else {
      liveDotQuad.setColor(color);
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
