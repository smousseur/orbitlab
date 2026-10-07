package com.smousseur.orbitlab.simulation.mission.scenario;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioMission;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioSite;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioVehicle;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Probe: does the codec of this build read a lunar mission carrying a field it does not know? */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class L2UnknownFieldProbe {
  private static final Logger logger = LogManager.getLogger(L2UnknownFieldProbe.class);

  @Test
  void unknownField() {
    ScenarioMission.LunarOrbit mission =
        new ScenarioMission.LunarOrbit(
            MissionType.LUNAR_ORBIT,
            "Lunar",
            "2026-10-06T08:00:00Z",
            new ScenarioSite("Kourou", 5.236, -52.775, 0.0),
            new ScenarioVehicle("FALCON_HEAVY", "LUNAR_ORBITER", 2_000.0),
            null,
            "NRLMSISE",
            "FAST",
            "#4FC3F7",
            true,
            null,
            100.0,
            null,
            null);
    String json =
        ScenarioCodec.write(
            new ScenarioFile(
                ScenarioFile.CURRENT_FORMAT_VERSION,
                "2026-10-06T08:00:00Z",
                "2026-10-06T08:00:00Z",
                List.of(mission)));
    String withUnknown =
        json.replace(
            "\"orbitAltitudeKm\"", "\"fieldOfALaterBuild\" : 9.0,\n    \"orbitAltitudeKm\"");
    logger.info("L2 CODEC json with unknown field:\n{}", withUnknown);
    try {
      ScenarioFile read = ScenarioCodec.read(withUnknown);
      logger.info("L2 CODEC unknown field TOLERATED: {}", read.missions().getFirst());
    } catch (RuntimeException e) {
      logger.info("L2 CODEC unknown field REFUSED: {}", e.getMessage());
    }
  }
}
