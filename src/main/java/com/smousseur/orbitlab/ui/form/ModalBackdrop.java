package com.smousseur.orbitlab.ui.form;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;

/**
 * Semi-transparent overlay rendered behind a modal dialog. Consumes mouse events so they never
 * reach the world below. Optionally invokes a click callback (e.g. to close the modal).
 */
public class ModalBackdrop {

  private final Container backdrop;
  private int lastWidth;
  private int lastHeight;
  private Runnable onClick;

  public ModalBackdrop() {
    backdrop = new Container();
    backdrop.setBackground(UiKit.gradientBackground(FormStyles.BACKDROP));
    backdrop.setLocalTranslation(0, 0, 100);

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
      backdrop.setLocalTranslation(0, h, 100);
    }
  }
}
