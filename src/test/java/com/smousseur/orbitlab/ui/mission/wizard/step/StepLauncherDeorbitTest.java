package com.smousseur.orbitlab.ui.mission.wizard.step;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.ui.mission.wizard.MissionProfile;
import org.junit.jupiter.api.Test;

/**
 * {@link StepLauncher#publishesDeorbit(MissionProfile, boolean)} in isolation: the pure predicate
 * {@link StepLauncher#getValues()} defers to, kept free of the Lemur widgets so it is verifiable
 * without a JME runtime.
 */
class StepLauncherDeorbitTest {

  @Test
  void noProfileYetPublishesNothing() {
    assertFalse(StepLauncher.publishesDeorbit(null, true));
  }

  @Test
  void checkedOnAnOfferingProfilePublishes() {
    assertTrue(StepLauncher.publishesDeorbit(MissionProfile.LEO, true));
  }

  @Test
  void uncheckedOnAnOfferingProfilePublishesNothing() {
    assertFalse(StepLauncher.publishesDeorbit(MissionProfile.LEO, false));
  }

  @Test
  void checkedOnANonOfferingProfilePublishesNothing() {
    assertFalse(StepLauncher.publishesDeorbit(MissionProfile.MEO, true));
  }

  @Test
  void checkedOnGeoPublishesNothing() {
    assertFalse(StepLauncher.publishesDeorbit(MissionProfile.GEO, true));
  }
}
