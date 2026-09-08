package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import org.junit.jupiter.api.Test;

class AscentProfileTest {

  @Test
  void validProfile_accepted() {
    AscentProfile profile = new AscentProfile(7.0, 3.0, 2.0);
    assertEquals(7.0, profile.verticalAscentDuration(), 1e-9);
    assertEquals(3.0, profile.pitchKickAngleDeg(), 1e-9);
    assertEquals(2.0, profile.interstageCoastDuration(), 1e-9);
  }

  @Test
  void zeroInterstageCoast_accepted() {
    assertEquals(0.0, new AscentProfile(7.0, 3.0, 0.0).interstageCoastDuration(), 1e-9);
  }

  @Test
  void nonPositiveVerticalAscent_rejected() {
    assertThrows(IllegalArgumentException.class, () -> new AscentProfile(0.0, 3.0, 2.0));
    assertThrows(IllegalArgumentException.class, () -> new AscentProfile(-1.0, 3.0, 2.0));
  }

  @Test
  void pitchKickOutOfRange_rejected() {
    assertThrows(IllegalArgumentException.class, () -> new AscentProfile(7.0, 0.0, 2.0));
    assertThrows(IllegalArgumentException.class, () -> new AscentProfile(7.0, 90.0, 2.0));
  }

  @Test
  void negativeInterstageCoast_rejected() {
    assertThrows(IllegalArgumentException.class, () -> new AscentProfile(7.0, 3.0, -0.1));
  }

  @Test
  void coreThrottle_defaultsToFullThrust() {
    assertEquals(1.0, new AscentProfile(7.0, 3.0, 2.0).coreThrottle(), 1e-9);
  }

  @Test
  void coreThrottle_declaredValueIsKept() {
    assertEquals(0.81, new AscentProfile(7.0, 3.0, 2.0, 0.81).coreThrottle(), 1e-9);
  }

  @Test
  void coreThrottle_outOfRange_rejected() {
    assertThrows(IllegalArgumentException.class, () -> new AscentProfile(7.0, 3.0, 2.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new AscentProfile(7.0, 3.0, 2.0, -0.1));
    assertThrows(IllegalArgumentException.class, () -> new AscentProfile(7.0, 3.0, 2.0, 1.01));
  }

  @Test
  void coreThrottle_throttled_isReportedAsSuch() {
    assertFalse(new AscentProfile(7.0, 3.0, 2.0).throttlesCore());
    assertTrue(new AscentProfile(7.0, 3.0, 2.0, 0.81).throttlesCore());
  }
}
