package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;

public class ModalBackdrop {

  private final Container backdrop;
  private int lastWidth;
  private int lastHeight;

  public ModalBackdrop() {
    backdrop = new Container();
    backdrop.setBackground(new QuadBackgroundComponent(MissionWizardStyles.WIZARD_BACKDROP));
    backdrop.setLocalTranslation(0, 0, 0);

    MouseEventControl.addListenersToSpatial(
        backdrop,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
          }

          @Override
          public void mouseButtonEvent(
              MouseButtonEvent event, Spatial target, Spatial capture) {
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

  public void update(Camera cam) {
    int w = cam.getWidth();
    int h = cam.getHeight();
    if (w != lastWidth || h != lastHeight) {
      lastWidth = w;
      lastHeight = h;
      backdrop.setPreferredSize(new Vector3f(w, h, 0));
      backdrop.setLocalTranslation(0, h, 0);
    }
  }
}
