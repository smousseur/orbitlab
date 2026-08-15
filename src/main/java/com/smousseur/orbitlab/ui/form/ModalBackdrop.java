package com.smousseur.orbitlab.ui.form;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.UiLayers;

/**
 * Semi-transparent overlay rendered behind a modal dialog. Consumes mouse events so they never
 * reach the world below. Optionally invokes a click callback (e.g. to close the modal).
 */
public class ModalBackdrop {

  /** Depth of a first-level modal's backdrop; its window sits one unit in front. */
  private static final float DEFAULT_Z = UiLayers.MODAL_BACKDROP;

  private final Container backdrop;
  private final float z;
  private int lastWidth;
  private int lastHeight;
  private Runnable onClick;

  public ModalBackdrop() {
    this(DEFAULT_Z);
  }

  /**
   * @param z depth of the backdrop, used to stack a modal on top of another one (the GUI bucket
   *     picks and renders higher z first)
   */
  public ModalBackdrop(float z) {
    this(z, FormStyles.BACKDROP);
  }

  /**
   * @param z depth of the backdrop, used to stack a modal on top of another one (the GUI bucket
   *     picks and renders higher z first)
   * @param tint colour of the overlay; a fully transparent one turns the backdrop into a pure click
   *     catcher, which is what a dropdown menu needs — it must swallow the click that dismisses it
   *     (including one aimed at the 3D scene, which must not select a body) without dimming the
   *     screen the way a modal does
   */
  public ModalBackdrop(float z, ColorRGBA tint) {
    this.z = z;
    backdrop = new Container();
    backdrop.setBackground(UiKit.gradientBackground(tint));
    backdrop.setLocalTranslation(0, 0, z);

    MouseEventControl.addListenersToSpatial(
        backdrop,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
            if (onClick != null) {
              onClick.run();
            }
          }

          @Override
          public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
          }

          @Override
          public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
          }

          @Override
          public void mouseMoved(MouseMotionEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
          }
        });
  }

  public Container getNode() {
    return backdrop;
  }

  /** Callback invoked when the user clicks the backdrop. Pass {@code null} to disable. */
  public void setOnClick(Runnable onClick) {
    this.onClick = onClick;
  }

  public void update(Camera cam) {
    int w = cam.getWidth();
    int h = cam.getHeight();
    if (w != lastWidth || h != lastHeight) {
      lastWidth = w;
      lastHeight = h;
      backdrop.setPreferredSize(new Vector3f(w, h, 0));
      backdrop.setLocalTranslation(0, h, z);
    }
  }
}
