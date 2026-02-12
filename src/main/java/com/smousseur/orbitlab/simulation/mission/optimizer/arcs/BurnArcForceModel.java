package com.smousseur.orbitlab.simulation.mission.optimizer.arcs;

import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import java.util.List;
import org.hipparchus.CalculusFieldElement;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.forces.ForceModel;
import org.orekit.propagation.FieldSpacecraftState;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.ParameterDriver;
import org.orekit.utils.PVCoordinates;

/**
 * ForceModel that applies a single thrust arc.
 *
 * <p>The direction is defined in the LVLH (Local Vertical Local Horizontal) frame:
 *
 * <ul>
 *   <li>X = prograde (velocity direction)
 *   <li>Y = normal (r × v)
 *   <li>Z = outward radial (completes the triad)
 * </ul>
 *
 * <p>The angles (alpha, delta) parameterize the direction:
 *
 * <ul>
 *   <li>alpha = 0, delta = 0 → purely prograde thrust
 *   <li>alpha = π/2 → normal thrust
 *   <li>delta = π/2 → radial thrust
 * </ul>
 */
public class BurnArcForceModel implements ForceModel {

  private final PropulsionSystem propulsion;
  private final AbsoluteDate epoch;
  private final double tStart;
  private final double duration;
  private final double alpha;
  private final double delta;
  private final double throttle;

  public BurnArcForceModel(
      PropulsionSystem propulsion,
      AbsoluteDate epoch,
      double tStart,
      double duration,
      double alpha,
      double delta,
      double throttle) {
    this.propulsion = propulsion;
    this.epoch = epoch;
    this.tStart = tStart;
    this.duration = duration;
    this.alpha = alpha;
    this.delta = delta;
    this.throttle = throttle;
  }

  @Override
  public boolean dependsOnPositionOnly() {
    return false;
  }

  @Override
  public Vector3D acceleration(SpacecraftState s, double[] parameters) {
    double t = s.getDate().durationFrom(epoch);

    // Outside the burn arc → no thrust
    // Use a sigmoid smoothing to avoid discontinuity
    double k = 5.0; // raideur du lissage (en 1/s)
    double activationStart = 1.0 / (1.0 + FastMath.exp(-k * (t - tStart)));
    double activationEnd = 1.0 / (1.0 + FastMath.exp(-k * (t - (tStart + duration))));
    double activation = activationStart - activationEnd;

    if (activation < 1e-10) {
      return Vector3D.ZERO;
    }

    // Build the LVLH frame from the current state
    PVCoordinates pv = s.getPVCoordinates();
    Vector3D vel = pv.getVelocity();
    Vector3D pos = pv.getPosition();

    Vector3D prograde = vel.normalize(); // X : prograde
    Vector3D normal = pos.crossProduct(vel).normalize(); // Y : normal orbital
    Vector3D radial = prograde.crossProduct(normal).normalize(); // Z : complète le trièdre

    // Direction in the LVLH frame → inertial frame
    double cosAlpha = FastMath.cos(alpha);
    double sinAlpha = FastMath.sin(alpha);
    double cosDelta = FastMath.cos(delta);
    double sinDelta = FastMath.sin(delta);

    // direction = cosDelta*cosAlpha * prograde + cosDelta*sinAlpha * normal + sinDelta * radial
    Vector3D direction =
        prograde
            .scalarMultiply(cosDelta * cosAlpha)
            .add(normal.scalarMultiply(cosDelta * sinAlpha))
            .add(radial.scalarMultiply(sinDelta));

    double mass = s.getMass();
    double effectiveThrust = propulsion.thrust() * throttle * activation;
    return direction.scalarMultiply(effectiveThrust / mass);
  }

  @Override
  public <T extends CalculusFieldElement<T>> FieldVector3D<T> acceleration(
      FieldSpacecraftState<T> s, T[] parameters) {
    return FieldVector3D.getZero(s.getDate().getField());
  }

  @Override
  public List<ParameterDriver> getParametersDrivers() {
    return List.of();
  }
}
