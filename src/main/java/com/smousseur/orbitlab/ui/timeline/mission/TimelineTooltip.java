package com.smousseur.orbitlab.ui.timeline.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;
import java.util.List;

/**
 * The hover tooltip of the mission track: a small opaque card that follows the cursor and lists the
 * stage name, the absolute UTC date and the {@code T+} offset under it (spec §9.1) — or, over a
 * group of markers, every transition the group stands for (§8).
 *
 * <p>Rebuilt only when its text actually changes. A cursor sweeping the track changes the date on
 * every motion event, so the lines are compared before any Lemur element is touched; without that,
 * a hover would allocate a handful of labels per frame.
 */
final class TimelineTooltip {

  private static final float PAD_X = 8f;
  private static final float PAD_Y = 5f;
  private static final float LINE_HEIGHT = 12f;
  private static final float CHAR_WIDTH = 5.4f;
  private static final float CURSOR_OFFSET_X = 12f;
  private static final float CURSOR_OFFSET_Y = 10f;

  private static final ColorRGBA BACKGROUND = new ColorRGBA(0.016f, 0.039f, 0.071f, 0.94f);

  private final Node parent;
  private final float z;
  private final Container root;

  private List<String> current = List.of();
  private boolean shown;

  /**
   * @param parent the widget root the tooltip is attached to while visible
   * @param z local z of the tooltip, above everything else in the widget
   */
  TimelineTooltip(Node parent, float z) {
    this.parent = parent;
    this.z = z;
    this.root = new Container(new BoxLayout(Axis.Y, FillMode.None), TimelineStyles.STYLE);
    root.setBackground(new QuadBackgroundComponent(BACKGROUND));
    root.setInsets(new Insets3f(PAD_Y, PAD_X, PAD_Y, PAD_X));
  }

  /**
   * Shows the tooltip with the given lines, its bottom edge just above {@code anchorY}.
   *
   * <p><b>The card opens upward, and that is not a preference.</b> The mission track is the
   * bottom-most HUD surface but one: the time capsule sits 8 px below it, and three lines of
   * tooltip are already 46 px tall. A card hung downward therefore runs past the track's own bottom
   * edge and lands on the capsule — a different widget showing different information. Above the
   * track there is only the 3D scene, which is where a tooltip anchored to the bottom of the screen
   * belongs.
   *
   * <p>The placement is computed here rather than by the caller because the card's height follows
   * its line count, and that count is only resolved by {@link #rebuild()} below — a caller would
   * have to position the card after showing it, or predict the height it is about to get.
   *
   * @param lines the lines to display, first one being the title; never empty
   * @param localX cursor x in the widget's local space
   * @param anchorY the y the card's bottom edge clears, in the widget's local space, negative
   *     downward
   */
  void show(List<String> lines, float localX, float anchorY) {
    if (lines.isEmpty()) {
      hide();
      return;
    }
    if (!lines.equals(current)) {
      current = List.copyOf(lines);
      rebuild();
    }
    if (!shown) {
      parent.attachChild(root);
      shown = true;
    }
    // A Lemur panel's translation is its top edge, so clearing the anchor by its own height is
    // what puts the bottom edge above it.
    float top = anchorY + CURSOR_OFFSET_Y + root.getPreferredSize().y;
    root.setLocalTranslation(localX + CURSOR_OFFSET_X, top, z);
  }

  /** Sends the tooltip away. */
  void hide() {
    if (shown) {
      root.removeFromParent();
      shown = false;
    }
  }

  /** Detaches the tooltip for good. */
  void close() {
    hide();
  }

  private void rebuild() {
    root.clearChildren();
    int widest = 0;
    for (String line : current) {
      widest = Math.max(widest, line.length());
    }
    for (int i = 0; i < current.size(); i++) {
      Label label = new Label(current.get(i), TimelineStyles.STYLE);
      label.setFont(TimelineStyles.mono(10));
      label.setFontSize(10f);
      label.setColor(i == 0 ? AppStyles.TL_TEXT_MAIN : AppStyles.TL_TEXT_DIM);
      label.setBackground(null);
      root.addChild(label);
    }
    // Sized from the character count rather than from Lemur's measurement: the bitmap font is
    // monospaced, so the estimate is exact to within a pixel, and it avoids a layout pass per
    // cursor move.
    root.setPreferredSize(
        new Vector3f(
            widest * CHAR_WIDTH + 2 * PAD_X, current.size() * LINE_HEIGHT + 2 * PAD_Y, 0f));
    root.setSize(root.getPreferredSize());
  }
}
