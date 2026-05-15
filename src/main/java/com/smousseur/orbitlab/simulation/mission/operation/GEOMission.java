package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticHohmannTransferStage;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticParkingInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticTrimBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
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

import java.util.ArrayList;
import java.util.List;

public class GEOMission extends Mission {
  private static final int ASCENSION_DURATION = 10;
  private static final double DEFAULT_LATITUDE = 5.23;
  private static final double DEFAULT_LONGITUDE = -52.77;
  private static final double DEFAULT_ALTITUDE = 0.0;

  private final double latitude;
  private final double longitude;
  private final double altitude;

  public GEOMission(String name, double parkingAltitude, double targetAltitude) {
    this(name, parkingAltitude, targetAltitude, 0.0);
  }

  public GEOMission(
      String name, double parkingAltitude, double targetAltitude, double finalInclination) {
    this(
        name,
        parkingAltitude,
        targetAltitude,
        DEFAULT_LATITUDE,
        DEFAULT_LONGITUDE,
        DEFAULT_ALTITUDE,
        finalInclination);
  }

  public GEOMission(
      String name,
      double parkingAltitude,
      double targetAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(name, parkingAltitude, targetAltitude, latitude, longitude, altitude, 0.0);
  }

  public GEOMission(
      String name,
      double parkingAltitude,
      double targetAltitude,
      double latitude,
      double longitude,
      double altitude,
      double finalInclination) {
    super(
        name,
        buildVehicle(),
        buildStages(parkingAltitude, targetAltitude, finalInclination),
        new OrbitInsertionObjective(
            SolarSystemBody.EARTH, parkingAltitude, targetAltitude, FastMath.toRadians(latitude)));
    this.latitude = latitude;
    this.longitude = longitude;
    this.altitude = altitude;
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

  private static VehicleStack buildVehicle() {
    return new VehicleStack(
        new ArrayList<>(
            List.of(
                LaunchVehicle.getLauncherStage1Vehicle(),
                LaunchVehicle.getLauncherStage2Vehicle(),
                Spacecraft.getSpacecraft())));
  }

  private static List<MissionStage> buildStages(
      double parkingAltitude, double targetAltitude, double finalInclination) {
    return List.of(
        new VerticalAscentStage("Vertical Ascent", ASCENSION_DURATION),
        new GravityTurnStage(
            "Gravity turn",
            ASCENSION_DURATION,
            3.0,
            GravityTurnConstraints.forTarget(parkingAltitude)),
        new AnalyticParkingInsertionStage("Parking", parkingAltitude),
        new CoastingStage("Coasting parking", true),
        new AnalyticHohmannTransferStage(
            "Transfert", targetAltitude, targetAltitude, FastMath.toRadians(finalInclination)),
        new AnalyticTrimBurnStage("Trim", targetAltitude, FastMath.toRadians(finalInclination)),
        new CoastingStage("Coasting", null));
  }
}
