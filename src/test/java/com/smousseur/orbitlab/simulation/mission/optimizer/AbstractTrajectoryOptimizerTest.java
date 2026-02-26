package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.orbit.OrbitTarget;
import com.smousseur.orbitlab.simulation.mission.objective.orbit.OrbitalObjective;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.TopocentricFrame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

public class AbstractTrajectoryOptimizerTest {
  protected static void propagateMission(Mission mission, AbsoluteDate start) {
    mission.start(start);

    double stepS = 0.016; // finer step for better event timing
    AbsoluteDate end = start.shiftedBy(3, TimeUnit.HOURS);

    AbsoluteDate t = start;
    int i = 0;
    int maxIters = (int) FastMath.ceil(end.durationFrom(start) / stepS) + 10;

    while (mission.isOnGoing() && t.compareTo(end) < 0) {
      t = t.shiftedBy(stepS);
      mission.update(t);

      if (++i > maxIters) {
        throw new IllegalStateException("Too many iterations, stuck? t=" + t);
      }
    }
  }

  public static class TestMission extends Mission {

    final double latitude;
    final double longitude;
    final double altitude;
    final double targetAltitude;

    public TestMission(
        String missionName,
        List<MissionStage> stages,
        Vehicle vehicule,
        double latitude,
        double longitude,
        double altitude,
        double targetAltitude) {
      super(missionName, vehicule, stages, getMissionObjective(targetAltitude));
      this.latitude = latitude;
      this.longitude = longitude;
      this.altitude = altitude;
      this.targetAltitude = targetAltitude;
    }

    @Override
    public SpacecraftState getInitialState(AbsoluteDate initialDate) {
      OneAxisEllipsoid earth = OrekitService.get().getEarthEllipsoid();
      Frame itrf = OrekitService.get().itrf();
      Frame gcrf = OrekitService.get().gcrf();
      GeodeticPoint launchPad =
          new GeodeticPoint(FastMath.toRadians(latitude), FastMath.toRadians(longitude), altitude);
      TopocentricFrame launchFrame = new TopocentricFrame(earth, launchPad, "Launch Pad");
      PVCoordinates initialPVInGCRF =
          itrf.getTransformTo(gcrf, initialDate)
              .transformPVCoordinates(
                  new PVCoordinates(launchFrame.getCartesianPoint(), Vector3D.ZERO));
      Orbit initialOrbit =
          new CartesianOrbit(initialPVInGCRF, gcrf, initialDate, Constants.WGS84_EARTH_MU);

      return new SpacecraftState(initialOrbit).withMass(this.getVehicle().getMass());
    }
  }

  private static MissionObjective getMissionObjective(double targetAltitude) {
    return new OrbitalObjective(new OrbitTarget(SolarSystemBody.EARTH, targetAltitude, 0));
  }
}
