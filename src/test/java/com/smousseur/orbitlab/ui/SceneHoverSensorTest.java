package com.smousseur.orbitlab.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.input.MouseInput;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.simsilica.lemur.event.MouseListener;
import com.smousseur.orbitlab.app.HoverState;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The scene sensor tells the hover whether the cursor is over the scene, and must never take a
 * button from the camera behind it.
 */
class SceneHoverSensorTest {

  private static final int[] BUTTONS = {
    MouseInput.BUTTON_LEFT, MouseInput.BUTTON_RIGHT, MouseInput.BUTTON_MIDDLE
  };

  private final HoverState state = new HoverState();
  private final AtomicInteger leftPresses = new AtomicInteger();
  private final MouseListener listener =
      SceneHoverSensor.listener(state, leftPresses::incrementAndGet);

  @Test
  void consumesNoButton() {
    for (int button : BUTTONS) {
      for (boolean pressed : new boolean[] {true, false}) {
        MouseButtonEvent event = new MouseButtonEvent(button, pressed, 100, 100);

        listener.mouseButtonEvent(event, null, null);

        assertFalse(event.isConsumed(), "button " + button + (pressed ? " pressed" : " released"));
      }
    }
  }

  @Test
  void forwardsTheLeftPressOnly() {
    listener.mouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, 0, 0), null, null);
    listener.mouseButtonEvent(
        new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, 0, 0), null, null);
    listener.mouseButtonEvent(
        new MouseButtonEvent(MouseInput.BUTTON_RIGHT, true, 0, 0), null, null);

    assertEquals(1, leftPresses.get());
  }

  @Test
  void tracksWhetherTheSceneIsUnderTheCursor() {
    MouseMotionEvent motion = new MouseMotionEvent(10, 10, 0, 0, 0, 0);

    listener.mouseEntered(motion, null, null);
    assertTrue(state.isSceneUnderCursor());

    listener.mouseExited(motion, null, null);
    assertFalse(state.isSceneUnderCursor());
  }
}
