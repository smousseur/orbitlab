package com.smousseur.orbitlab.ui.mission.component;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Insets3f;
import com.smousseur.orbitlab.ui.UiKit;

/**
 * The indeterminate activity indicator: {@code icon-spinner.png} turning one spoke at a time.
 *
 * <p><b>Why a geometry and not a background.</b> Every other icon in the panel is a {@code
 * QuadBackgroundComponent} laid on a container, and a background cannot be rotated; rotating the
 * container instead would turn it about its corner, since a Lemur container's origin is its
 * top-left. A pivot node holding an offset quad is what puts the axis in the middle. The precedent
 * for attaching a raw child inside a Lemur widget is {@code ProgressBar}, whose fill is placed the
 * same way and for the same reason — the layout must not manage it.
 *
 * <p><b>Why the box is as tall as the line it sits on, rather than as tall as the icon.</b> The
 * widget centres itself inside a box of the caller's height, so it can be dropped straight into the
 * row's horizontal layout as a sibling of the label, anchored exactly like it. An icon-sized box
 * padded with spacers would instead stack a second container, whose own insets would shift the
 * icon against the text for reasons nothing in the row states. Sharing the label's box removes that
 * source of drift; what it does not remove is the residual optical correction every icon in this
 * row carries, which is why the constructor still takes a lift in pixels.
 *
 * <p>The material is {@code IconMask}, not {@code Unshaded}: the icon is pure black with its shape
 * in the alpha channel, so a multiplicative tint would leave it black.
 */
public final class SpinnerIcon {

  /**
   * Side of the drawn icon in a mission row, in pixels.
   *
   * <p>Sized against the texture rather than against the box: the drawing is inscribed in a circle
   * of radius 17 px inside a 64 px canvas, so barely more than half of any box it is given is ink.
   * At 24 px the visible spinner is about 13 px across, which reads beside 11 px text; at 16 px it
   * was nine, and looked like a speck.
   */
  public static final float SIZE = 24f;

  private final Container root;
  private final Node pivot;
  private final Material material;
  private final SpinnerRotation rotation = new SpinnerRotation();

  private ColorRGBA tint;
  private boolean visible = true;

  /**
   * Builds a spinner centred in a box of the given height, initially visible.
   *
   * @param iconSize side of the drawn icon, in pixels
   * @param boxHeight height of the box it centres itself in — the same height the label beside it
   *     is given, so the two share one optical centre
   * @param tint the colour to draw the icon in
   */
  public SpinnerIcon(float iconSize, float boxHeight, ColorRGBA tint) {
    this(iconSize, boxHeight, 0f, tint);
  }

  /**
   * Builds a spinner centred in a box of the given height, raised by an explicit offset.
   *
   * @param iconSize side of the drawn icon, in pixels
   * @param boxHeight height of the box it centres itself in
   * @param liftPx pixels to raise the icon by, above the geometric centre of that box. A tuning
   *     knob and nothing more: the font metrics do not call for one — {@code ibmplexmono-11} has
   *     {@code lineHeight=15}, {@code base=12} and digits inked from {@code yoffset=3} over 10 px,
   *     so a line of digits is centred to within half a pixel — but the row's own icons carry
   *     comparable corrections ({@code MissionRow.centerVertically} subtracts 6,
   *     {@code RowActionIcons.vCenter} subtracts 5) and this one is set the same way, by eye.
   * @param tint the colour to draw the icon in
   */
  public SpinnerIcon(float iconSize, float boxHeight, float liftPx, ColorRGBA tint) {
    root = new Container();
    root.setPreferredSize(new Vector3f(iconSize, boxHeight, 0));
    root.setBackground(null);
    // Explicit, so the default style's insets cannot shift the box the pivot is measured against.
    root.setInsets(new Insets3f(0, 0, 0, 0));

    this.tint = tint.clone();
    material = UiKit.iconMaskMaterial("icon-spinner", this.tint);

    pivot = new Node("spinner-pivot");
    // Lemur lays a container's content out from its top-left corner, downwards: the box spans
    // y in [-boxHeight, 0], so its middle is half its height below the origin. z = 1 puts the icon
    // in front of whatever background the row draws, as ProgressBar does with its fill.
    pivot.setLocalTranslation(iconSize * 0.5f, -boxHeight * 0.5f + liftPx, 1f);

    if (material != null) {
      Geometry quad = new Geometry("spinner", new Quad(iconSize, iconSize));
      quad.setMaterial(material);
      // The quad grows from its own origin, so half a side of offset centres it on the pivot. The
      // icon's drawing being inscribed in a circle is what makes the rotation safe: no corner of
      // the texture ever swings outside the box.
      quad.setLocalTranslation(-iconSize * 0.5f, -iconSize * 0.5f, 0f);
      pivot.attachChild(quad);
    }
    root.attachChild(pivot);
  }

  /**
   * @return the node to place in a layout, beside the label it is centred with
   */
  public Container getNode() {
    return root;
  }

  /**
   * Recolours the icon. A no-op when the colour is already the one being drawn, so a caller may
   * push the tint every frame without touching the material.
   *
   * @param color the colour to draw the icon in
   */
  public void setTint(ColorRGBA color) {
    if (material == null || tint.equals(color)) {
      return;
    }
    tint = color.clone();
    material.setColor("Color", tint);
  }

  /**
   * Shows or hides the icon without detaching it, so its place in the row is kept either way.
   *
   * @param visible whether the icon is drawn
   */
  public void setVisible(boolean visible) {
    if (this.visible == visible) {
      return;
    }
    this.visible = visible;
    pivot.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  /**
   * Advances the rotation. Touches the scene graph only on the frames where the step index actually
   * changes — nine frames out of ten at sixty per second.
   *
   * @param tpf the frame time in seconds
   */
  public void update(float tpf) {
    if (!visible || material == null) {
      return;
    }
    if (rotation.advance(tpf)) {
      pivot.setLocalRotation(
          new Quaternion().fromAngleAxis(rotation.angleRadians(), Vector3f.UNIT_Z));
    }
  }
}
