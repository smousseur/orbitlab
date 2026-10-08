package com.smousseur.orbitlab.ui;

import com.jme3.input.MouseInput;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.simsilica.lemur.event.MouseListener;
import com.smousseur.orbitlab.app.HoverState;
import java.util.Objects;

/**
 * A full-screen, invisible GUI surface behind every other one, which tells the hover whether the
 * cursor is over the scene.
 *
 * <p>Lemur decides who has the cursor, by depth: the surface in front wins, whether a panel, a
 * planet icon, the open menu's catcher or a modal's backdrop. Sitting on {@link UiLayers#SCENE},
 * below all of them, this sensor is entered exactly when nothing in front of the scene took the
 * cursor — and that is when the orbits may be hovered. Panels with gaps in their background carry a
 * shield ({@link UiKit#shield}) so that their gaps do not count as the scene.
 *
 * <p><b>It takes no button.</b> The camera's rotation and zoom are input mappings that only see an
 * event Lemur did not consume, and a listener that left {@code mouseButtonEvent} to its default
 * would consume every button over the whole scene. The left press is forwarded, for a click on a
 * lit orbit, and still not consumed.
 */
public final class SceneHoverSensor {

  private final Container node;
  private int lastWidth;
  private int lastHeight;

  /**
   * Creates the sensor. It is sized on the first {@link #update}.
   *
   * @param state the hover state the sensor reports to
   * @param onLeftPress invoked on a left press over the scene
   */
  public SceneHoverSensor(HoverState state, Runnable onLeftPress) {
    node = new Container();
    node.setBackground(UiKit.gradientBackground(new ColorRGBA(0f, 0f, 0f, 0f)));
    node.setLocalTranslation(0f, 0f, UiLayers.SCENE);
    MouseEventControl.addListenersToSpatial(node, listener(state, onLeftPress));
  }

  /**
   * @return the sensor's node, to attach to the GUI graph
   */
  public Container getNode() {
    return node;
  }

  /**
   * Stretches the sensor over the whole screen when the screen's size changes.
   *
   * @param cam the camera the GUI is drawn with
   */
  public void update(Camera cam) {
    int w = cam.getWidth();
    int h = cam.getHeight();
    if (w != lastWidth || h != lastHeight) {
      lastWidth = w;
      lastHeight = h;
      node.setPreferredSize(new Vector3f(w, h, 0f));
      node.setLocalTranslation(0f, h, UiLayers.SCENE);
    }
  }

  static MouseListener listener(HoverState state, Runnable onLeftPress) {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(onLeftPress, "onLeftPress");
    return new DefaultMouseListener() {
      @Override
      public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture) {
        if (event.isPressed() && event.getButtonIndex() == MouseInput.BUTTON_LEFT) {
          onLeftPress.run();
        }
      }

      @Override
      public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
        state.setSceneUnderCursor(true);
      }

      @Override
      public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
        state.setSceneUnderCursor(false);
      }
    };
  }
}
