package com.smousseur.orbitlab.simulation.mission.context;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.vehicle.LauncherType;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

class MissionEntryModelPathTest {

  @Test
  void defaultModelPathIsFalconHeavy() {
    MissionEntry entry = new MissionEntry(new StubMission("test"));
    assertEquals(LauncherType.FALCON_HEAVY.modelPath(), entry.getModelPath());
  }

  @Test
  void setModelPathOverridesDefault() {
    MissionEntry entry = new MissionEntry(new StubMission("test"));
    String custom = LauncherType.ARIANE_5_ECA.modelPath();
    entry.setModelPath(custom);
    assertEquals(custom, entry.getModelPath());
  }

  @Test
  void setModelPathRejectsNull() {
    MissionEntry entry = new MissionEntry(new StubMission("test"));
    assertThrows(NullPointerException.class, () -> entry.setModelPath(null));
  }

  private static final class StubMission extends Mission {
    StubMission(String name) {
      super(name, (Vehicle) null, (List<MissionStage>) null, (MissionObjective) null);
    }

    @Override
    public SpacecraftState getInitialState(AbsoluteDate initialDate) {
      return null;
    }
  }
}
