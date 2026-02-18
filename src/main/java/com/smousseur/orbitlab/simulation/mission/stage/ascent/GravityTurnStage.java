package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.stage.MissionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
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
    this.maxBurnTime = 80; // propellantRemaining / mDot;
    return state;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    PropulsionSystem propulsion = mission.getVehicle().getPropulsion();
    ConstantThrustManeuver burn =
        new ConstantThrustManeuver(
            state.getDate().shiftedBy(1.0e-3),
            this.maxBurnTime,
            propulsion.thrust(),
            propulsion.isp(),
            new LofOffset(state.getFrame(), LOFType.TNW),
            Vector3D.PLUS_I);
    propagator.addForceModel(burn);
    RadialVelocityDetector radialDetector =
        new RadialVelocityDetector(100)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                });

    propagator.addEventDetector(radialDetector);
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

    // Local topocentric frame at current position
    Vector3D zenith = pos.normalize();

    // Local north: project Earth's rotation axis (PLUS_K in EME2000)
    // onto the horizontal plane and normalize
    Vector3D northPole = Vector3D.PLUS_K;
    Vector3D north =
        northPole.subtract(new Vector3D(Vector3D.dotProduct(northPole, zenith), zenith));
    north = north.normalize();

    // Local east completes the right-handed topocentric frame
    Vector3D east = Vector3D.crossProduct(zenith, north).normalize();

    // Kick direction in the local horizontal plane
    Vector3D azimuthDir =
        new Vector3D(
            FastMath.cos(launchAzimuth), north,
            FastMath.sin(launchAzimuth), east);

    // Rotation axis: perpendicular to the plane (zenith, azimuthDir)
    Vector3D rotationAxis = Vector3D.crossProduct(zenith, azimuthDir).normalize();

    // Rotate the actual velocity vector by pitchKickAngle around this axis.
    // Using Rotation preserves the velocity norm and the existing
    // horizontal component from Earth's rotation (~400 m/s at equator).
    Rotation kick = new Rotation(rotationAxis, pitchKickAngle, RotationConvention.VECTOR_OPERATOR);
    Vector3D newVel = kick.applyTo(vel);

    // Rebuild the spacecraft state with modified velocity
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
