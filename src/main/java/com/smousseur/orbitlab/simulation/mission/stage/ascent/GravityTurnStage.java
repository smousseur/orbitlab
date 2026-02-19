package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.stage.MissionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

public class GravityTurnStage extends MissionStage {
  private final double targetInclination;
  private final double ascensionDuration;
  private final double pitchKickAngle;
  private final double launchLatitude;
  private double maxBurnTime;

  public GravityTurnStage(String name, double ascensionDuration, double pitchKickAngle) {
    this(name, ascensionDuration, pitchKickAngle, 0.0, 0.0);
  }

  public GravityTurnStage(
      String name,
      double ascensionDuration,
      double pitchKickAngle,
      double launchLatitude,
      double targetInclination) {
    super(name);
    this.ascensionDuration = ascensionDuration;
    this.targetInclination = targetInclination;
    this.pitchKickAngle = pitchKickAngle;
    this.launchLatitude = launchLatitude;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    Vehicle vehicle = mission.getVehicle();
    SpacecraftState state = applyPitchKick(previousState, pitchKickAngle, getLaunchAzimuth());
    PropulsionSystem propulsion = vehicle.getPropulsion();
    double mDot = propulsion.thrust() / (propulsion.isp() * Constants.G0_STANDARD_GRAVITY);
    double propellantBurned = ascensionDuration * mDot;
    double propellantRemaining = vehicle.getPropellantMass() - propellantBurned;
    this.maxBurnTime = propellantRemaining / mDot; // 114
    return state;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    PropulsionSystem propulsion = mission.getVehicle().getPropulsion();
    AbsoluteDate kickDate = state.getDate().shiftedBy(1.0e-3);
    ConstantThrustManeuver burn =
        new ConstantThrustManeuver(
            kickDate,
            this.maxBurnTime,
            propulsion.thrust(),
            propulsion.isp(),
            new GravityTurnAttitudeProvider(kickDate, 110.0),
            Vector3D.PLUS_I);
    propagator.addForceModel(burn);

    AbsoluteDate mecoDate = state.getDate().shiftedBy(this.maxBurnTime);
    DateDetector mecoDetector =
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                });

    propagator.addEventDetector(mecoDetector);
  }

  /**
   * Apply instantaneous pitch kick: rotate the velocity vector by pitchKickAngle away from zenith,
   * toward the launch azimuth.
   *
   * @param state spacecraft state at end of vertical phase
   * @param pitchKickAngle kick angle from vertical (rad)
   * @param launchAzimuth azimuth direction for the kick (rad from North, clockwise) 90° = East
   *     (prograde equatorial)
   * @return new state with rotated velocity, same position and mass
   */
  private SpacecraftState applyPitchKick(
      SpacecraftState state, double pitchKickAngle, double launchAzimuth) {
    Vector3D pos = state.getPVCoordinates().getPosition();
    Vector3D vel = state.getPVCoordinates().getVelocity();

    // Local topocentric frame
    Vector3D zenith = pos.normalize();
    Vector3D northPole = Vector3D.PLUS_K;
    Vector3D north =
        northPole
            .subtract(new Vector3D(Vector3D.dotProduct(northPole, zenith), zenith))
            .normalize();
    Vector3D east = Vector3D.crossProduct(zenith, north).normalize();

    // Kick direction in horizontal plane
    Vector3D azimuthDir =
        new Vector3D(
            FastMath.cos(launchAzimuth), north,
            FastMath.sin(launchAzimuth), east);

    // Instead of rotating velocity, compute the NEW thrust direction
    // and apply an instantaneous delta-v in that direction.
    // The kick "redirects" the vertical burn component, not the whole velocity.

    // Decompose velocity into:
    //  - radial (along zenith) = the part from the vertical burn
    //  - tangential (horizontal) = mostly Earth rotation
    double vRadial = Vector3D.dotProduct(vel, zenith);
    Vector3D vTangential = vel.subtract(new Vector3D(vRadial, zenith));

    // Rotate ONLY the radial component by pitchKickAngle
    // from zenith toward azimuthDir
    Vector3D newRadialDir =
        new Vector3D(
            FastMath.cos(pitchKickAngle), zenith, FastMath.sin(pitchKickAngle), azimuthDir);

    // Reconstruct velocity
    Vector3D newVel = new Vector3D(vRadial, newRadialDir).add(vTangential);

    PVCoordinates newPV = new PVCoordinates(pos, newVel);
    CartesianOrbit newOrbit =
        new CartesianOrbit(newPV, state.getFrame(), state.getDate(), state.getOrbit().getMu());

    return new SpacecraftState(newOrbit).withMass(state.getMass());
  }

  private double getLaunchAzimuth() {
    double result = FastMath.PI / 2; // 90° = due east
    if (launchLatitude != 0 && targetInclination != 0) {
      result = FastMath.asin(FastMath.cos(targetInclination) / FastMath.cos(launchLatitude));
    }
    return result;
  }
}
