package com.smousseur.orbitlab.simulation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpaceTrackServiceTest {
  @Test
  void testConnection() throws Exception {
    SpaceTrackService spaceTrackService = SpaceTrackService.get();
    spaceTrackService.login();
    assertTrue(spaceTrackService.isConnected());
    spaceTrackService.logout();
  }
}
