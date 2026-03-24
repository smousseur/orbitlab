package com.smousseur.orbitlab.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SolarSystemBodyTest {

  @Test
  void sun_hasNoParent() {
    assertNull(SolarSystemBody.SUN.parent());
  }

  @Test
  void planets_haveParentSun() {
    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (body == SolarSystemBody.SUN || body.isSatellite()) continue;
      assertEquals(
          SolarSystemBody.SUN,
          body.parent(),
          body + " should orbit the Sun");
    }
  }

  @Test
  void moon_hasParentEarth() {
    assertEquals(SolarSystemBody.EARTH, SolarSystemBody.MOON.parent());
  }

  @Test
  void moon_isSatellite() {
    assertTrue(SolarSystemBody.MOON.isSatellite());
  }

  @Test
  void planets_areNotSatellites() {
    assertFalse(SolarSystemBody.EARTH.isSatellite());
    assertFalse(SolarSystemBody.MARS.isSatellite());
    assertFalse(SolarSystemBody.JUPITER.isSatellite());
  }

  @Test
  void sun_isNotSatellite() {
    assertFalse(SolarSystemBody.SUN.isSatellite());
  }

  @Test
  void allBodies_haveParentExceptSun() {
    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (body == SolarSystemBody.SUN) {
        assertNull(body.parent(), "SUN should have no parent");
      } else {
        assertNotNull(body.parent(), body + " should have a parent");
      }
    }
  }
}
