package com.smousseur.orbitlab.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisplaySettingsTest {

  @Test
  void debrisAreHiddenByDefault() {
    assertFalse(new DisplaySettings().isDebrisVisible(), "debris are decluttered by default (L7)");
  }

  @Test
  void debrisVisibilityToggles() {
    DisplaySettings settings = new DisplaySettings();
    settings.setDebrisVisible(true);
    assertTrue(settings.isDebrisVisible());
    settings.setDebrisVisible(false);
    assertFalse(settings.isDebrisVisible());
  }
}
