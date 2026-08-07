package com.smousseur.orbitlab.ui.mission.panel;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;

/** Resolves the Type label shown for a mission in the roster row and the panel footer. */
final class MissionTypes {

  /**
   * Shown for legacy entries wrapping a pre-built mission: they carry no {@link MissionSpec}, so
   * their type is genuinely unknown here — better a dash than a type we would be guessing. ASCII
   * hyphen, not an em dash: the bundled bitmap fonts only carry glyphs 32-127.
   */
  private static final String UNKNOWN_MISSION_TYPE = "-";

  private MissionTypes() {}

  static String label(MissionEntry entry) {
    return entry
        .spec()
        .map(MissionSpec::type)
        .map(MissionType::displayName)
        .orElse(UNKNOWN_MISSION_TYPE);
  }
}
