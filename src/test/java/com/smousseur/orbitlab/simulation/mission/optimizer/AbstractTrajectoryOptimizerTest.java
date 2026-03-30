package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import java.util.concurrent.TimeUnit;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

public class AbstractTrajectoryOptimizerTest {
  protected static class PropagationResults {
    double minCoastAltitude = Double.MAX_VALUE;
    double maxCoastAltitude = Double.MIN_VALUE;

    void update(Mission mission, String coastPhaseName) {
      SpacecraftState state = mission.getCurrentState();
      double altitude = mission.computeAltitudeMeters(state);
      if (mission.isOnGoing()) {
        MissionStage currentStage = mission.getCurrentStage();
        if (coastPhaseName.equals(currentStage.getName())) {
          if (altitude < minCoastAltitude) {
            minCoastAltitude = altitude;
          }
          if (altitude > maxCoastAltitude) {
            maxCoastAltitude = altitude;
          }
        }
      }
    }
  }

  protected static PropagationResults propagateMission(
      Mission mission, String coastPhaseName, AbsoluteDate start) {
    PropagationResults results = new PropagationResults();
    mission.start(start);

    double stepS = 0.016; // finer step for better event timing
    AbsoluteDate end = start.shiftedBy(2, TimeUnit.HOURS);

    AbsoluteDate t = start;
    int i = 0;
    int maxIters = (int) FastMath.ceil(end.durationFrom(start) / stepS) + 10;

    while (mission.isOnGoing() && t.compareTo(end) < 0) {
      t = t.shiftedBy(stepS);
      mission.update(t);
      results.update(mission, coastPhaseName);
      if (++i > maxIters) {
        throw new IllegalStateException("Too many iterations, stuck? t=" + t);
      }
    }
    return results;
  }
}
