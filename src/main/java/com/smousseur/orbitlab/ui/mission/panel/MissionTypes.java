package com.smousseur.orbitlab.ui.mission.panel;

import com.smousseur.orbitlab.simulation.mission.MissionEntry;

final class MissionTypes {

  /** Placeholder mission type displayed in the Type column until Mission exposes one. */
  private static final String DEFAULT_MISSION_TYPE = "LEO";

  private MissionTypes() {}

  static String label(MissionEntry entry) {
    // TODO: pull the actual mission type from the wizard once stored on Mission.
    return DEFAULT_MISSION_TYPE;
  }
}
