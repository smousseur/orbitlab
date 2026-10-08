package com.smousseur.orbitlab.engine.scene.body.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Node;
import com.simsilica.lemur.event.MouseEventControl;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A hidden icon must neither be hovered nor clicked, although Lemur still picks it: its listener is
 * switched off while it is hidden, and it reports its own exit.
 */
class IconHoverGuardTest {

  private final List<Boolean> reports = new ArrayList<>();
  private Node icon;
  private MouseEventControl control;
  private IconHoverGuard guard;

  @BeforeEach
  void setUp() {
    icon = new Node("icon");
    control = new MouseEventControl();
    icon.addControl(control);
    guard = new IconHoverGuard(icon, reports::add);
  }

  @Test
  void hidingSwitchesTheListenerOffAndShowingSwitchesItBackOn() {
    guard.setShown(false);
    assertFalse(control.isEnabled());

    guard.setShown(true);
    assertTrue(control.isEnabled());
  }

  @Test
  void hidingAHoveredIconReportsItsExitOnce() {
    guard.entered();
    guard.setShown(false);
    guard.exited();
    guard.setShown(false);

    assertEquals(List.of(true, false), reports);
  }

  @Test
  void hidingAnIconNobodyHoversReportsNothing() {
    guard.setShown(false);
    guard.setShown(true);

    assertTrue(reports.isEmpty());
  }

  @Test
  void anEntryWhileHiddenIsIgnored() {
    guard.setShown(false);
    guard.entered();

    assertTrue(reports.isEmpty());
  }

  @Test
  void reportsEachEntryAndExitOfAShownIcon() {
    guard.entered();
    guard.exited();
    guard.exited();

    assertEquals(List.of(true, false), reports);
  }
}
