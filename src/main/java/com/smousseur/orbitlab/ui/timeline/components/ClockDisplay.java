package com.smousseur.orbitlab.ui.timeline.components;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;
import org.orekit.time.AbsoluteDate;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.ui.AppStyles;

/**
 * Clock display cluster: date label (right-aligned) + "UTC" suffix label.
 *
 * <p>Both elements are anchored to the right edge of the capsule. Call {@link
 * #update(AbsoluteDate)} each frame to refresh the date text.
 */
public class ClockDisplay {

  private static final float CLOCK_LABEL_WIDTH = 86f;
  private static final float CLOCK_LABEL_HEIGHT = 16f;
  private static final float UTC_LABEL_WIDTH = 24f;
  private static final float UTC_LABEL_HEIGHT = 14f;
  private static final float DATE_UTC_GAP = 4f;

  private final Label dateLabel;
  private final float leftEdgeX;

  public ClockDisplay(Container root, float capsuleHeight, float rightEnd, SimulationClock clock) {
    float utcX = rightEnd - UTC_LABEL_WIDTH;
    float dateX = utcX - DATE_UTC_GAP - CLOCK_LABEL_WIDTH;
    this.leftEdgeX = dateX;

    Label utcLabel = new Label("UTC", TimelineStyles.STYLE);
    utcLabel.setFont(TimelineStyles.mono(10));
    utcLabel.setFontSize(10f);
    utcLabel.setColor(AppStyles.TL_TEXT_MUTED);
    utcLabel.setBackground(null);
    utcLabel.setTextVAlignment(VAlignment.Center);
    utcLabel.setPreferredSize(new Vector3f(UTC_LABEL_WIDTH, UTC_LABEL_HEIGHT, 0f));
    utcLabel.setSize(utcLabel.getPreferredSize());
    place(utcLabel, root, utcX, UTC_LABEL_HEIGHT, capsuleHeight, 1f);

    dateLabel = new Label(TimeConverter.formatDate(clock.now()), TimelineStyles.STYLE);
    dateLabel.setFont(TimelineStyles.mono(12));
    dateLabel.setFontSize(12f);
    dateLabel.setColor(AppStyles.TL_CYAN);
    dateLabel.setBackground(null);
    dateLabel.setTextHAlignment(HAlignment.Right);
    dateLabel.setTextVAlignment(VAlignment.Center);
    dateLabel.setPreferredSize(new Vector3f(CLOCK_LABEL_WIDTH, CLOCK_LABEL_HEIGHT, 0f));
    dateLabel.setSize(dateLabel.getPreferredSize());
    place(dateLabel, root, dateX, CLOCK_LABEL_HEIGHT, capsuleHeight, 1f);
  }

  /** Updates the date label text. Call once per frame. */
  public void update(AbsoluteDate now) {
    dateLabel.setText(TimeConverter.formatDate(now));
  }

  /** X coordinate of the date label's left edge, used to position divider 3 to its left. */
  public float leftEdge() {
    return leftEdgeX;
  }

  private static void place(
      Spatial s, Container root, float x, float height, float capsuleHeight, float z) {
    float y = -(capsuleHeight - height) * 0.5f;
    s.setLocalTranslation(x, y, z);
    root.attachChild(s);
  }
}
