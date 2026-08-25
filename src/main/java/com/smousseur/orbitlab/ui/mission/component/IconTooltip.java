package com.smousseur.orbitlab.ui.mission.component;

import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.form.FormStyles;

/**
 * A row label that appears on hover, positioned above (or below) an anchor.
 *
 * <p><b>The card is never laid out by the anchor.</b> It is attached via {@code attachChild}, and
 * therefore lies outside the parent's layout: this is the only way to hover over an element without
 * causing the cell containing it to resize to make room for the tooltip. The trade-off is that
 * nothing else sizes or positions it — both are handled here.
 *
 * <p>The anchor is also the card's parent, and this is not merely a matter of convenience for
 * positioning: a list rebuilt while hovering (as the mission panel does whenever its state changes)
 * detaches the anchor, and the card goes with it. If it were attached to a higher-level node, it
 * would outlive the icon it describes and remain on screen, since {@code mouseExited} would never
 * be received by a spatial that has already been detached.
 */
public final class IconTooltip {

  public enum Placement {
    ABOVE,
    BELOW
  }

  private static final float PAD_X = 8f;
  private static final float PAD_Y = 10f;

  /** Gap between the edge of the anchor and the edge of the card. */
  private static final float GAP = 6f;

  /** Minimum width: below this threshold, the card looks like an artifact rather than a label. */
  private static final float MIN_WIDTH = 44f;

  /**
   * Local z. The GUI bucket sorts by world z, and the contents of a window are stacked within a few
   * units: 10 is enough to bring it in front of its neighbors without ever conflicting with {@link
   * UiLayers}, whose levels are spaced by 10 precisely.
   */
  private static final float Z = 10f;

  private final Panel anchor;
  private final String text;
  private final Placement placement;

  /** Created on first hover: an icon that is never hovered does not pay the cost of its label. */
  private Container card;

  private IconTooltip(Panel anchor, String text, Placement placement) {
    this.anchor = anchor;
    this.text = text;
    this.placement = placement;
  }

  /**
   * Attaches a tooltip to an icon that is both the hover zone and the anchor — the common case.
   *
   * @param icon the hovered icon
   * @param text the label text, on a single line
   */
  public static void attach(Panel icon, String text) {
    attach(icon, icon, text, Placement.ABOVE);
  }

  /**
   * Attaches a tooltip whose hover zone is not the anchor — for example, an icon centered in a
   * wider cell that captures the mouse.
   *
   * <p>The listener is <em>added</em>: {@code MouseEventControl} accepts multiple listeners on the
   * same spatial, so an icon that already handles its own hover behavior keeps its existing code
   * intact while gaining its label in a single line.
   *
   * @param hoverZone the spatial whose hover opens the label
   * @param anchor the element around which the card is centered and positioned
   * @param text the label text, on a single line
   * @param placement the side of the anchor on which the card opens
   */
  public static void attach(Spatial hoverZone, Panel anchor, String text, Placement placement) {
    IconTooltip tip = new IconTooltip(anchor, text, placement);
    MouseEventControl.addListenersToSpatial(
        hoverZone,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial target, Spatial capture) {
            tip.show();
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial target, Spatial capture) {
            tip.hide();
          }
        });
  }

  private void show() {
    if (card == null) {
      card = buildCard();
    }
    Vector3f size = card.getPreferredSize();
    Vector3f anchorSize = anchor.getSize();
    float x = (anchorSize.x - size.x) * 0.2f;
    float y = placement == Placement.ABOVE ? size.y + GAP : -(anchorSize.y + GAP);
    card.setLocalTranslation(x, y, Z);
    anchor.attachChild(card);
  }

  private void hide() {
    if (card != null) {
      card.removeFromParent();
    }
  }

  private Container buildCard() {
    Container tip = new Container();
    tip.setBackground(UiKit.flat("icon-tooltip"));
    Label label = new Label(text, FormStyles.STYLE);
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(FormStyles.TEXT_PRIMARY);
    label.setBackground(null);
    label.setTextHAlignment(HAlignment.Center);
    label.setTextVAlignment(VAlignment.Top);

    Vector3f textSize = label.getPreferredSize().clone();
    float width = Math.max(MIN_WIDTH, textSize.x + 2f * PAD_X);
    float height = textSize.y + 2f * PAD_Y;

    label.setPreferredSize(new Vector3f(width - 2f * PAD_X, textSize.y, 0f));
    label.setSize(label.getPreferredSize());
    label.setLocalTranslation(PAD_X, -PAD_Y / 2, 1f);
    tip.attachChild(label);

    Vector3f size = new Vector3f(width, height, 0f);
    tip.setPreferredSize(size);
    tip.setSize(size);
    return tip;
  }
}
