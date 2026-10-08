package com.smousseur.orbitlab.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.input.MouseInput;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.scene.Node;
import com.simsilica.lemur.event.MouseEventControl;
import org.junit.jupiter.api.Test;

/**
 * A shield makes a panel's root the hover target over its own background, so the scene sensor
 * behind it is not entered — and takes no button, so a camera rotation started there still turns.
 */
class UiKitShieldTest {

  @Test
  void installsAnEnabledListenerThatConsumesNoButton() {
    Node root = new Node("panel");

    UiKit.shield(root);

    MouseEventControl control = root.getControl(MouseEventControl.class);
    assertNotNull(control);
    assertTrue(control.isEnabled());
    for (int button :
        new int[] {MouseInput.BUTTON_LEFT, MouseInput.BUTTON_RIGHT, MouseInput.BUTTON_MIDDLE}) {
      for (boolean pressed : new boolean[] {true, false}) {
        MouseButtonEvent event = new MouseButtonEvent(button, pressed, 100, 100);

        control.mouseButtonEvent(event, root, null);

        assertFalse(event.isConsumed(), "button " + button + (pressed ? " pressed" : " released"));
      }
    }
  }

  /** A culled panel is still picked, so its shield has to be switched off with it. */
  @Test
  void followsThePanelsVisibility() {
    Node root = new Node("panel");
    UiKit.shield(root);
    MouseEventControl control = root.getControl(MouseEventControl.class);

    UiKit.setShieldEnabled(root, false);
    assertFalse(control.isEnabled());

    UiKit.setShieldEnabled(root, true);
    assertTrue(control.isEnabled());
  }
}
