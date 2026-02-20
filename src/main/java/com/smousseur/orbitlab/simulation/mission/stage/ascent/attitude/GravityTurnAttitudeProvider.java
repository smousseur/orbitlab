package com.smousseur.orbitlab.simulation.mission.stage.ascent.attitude;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.geometry.euclidean.threed.FieldRotation;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FieldAttitude;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.*;

public class GravityTurnAttitudeProvider implements AttitudeProvider {

  private final AbsoluteDate kickDate;
  private final double transitionTime;
  private final double exponent;

  public GravityTurnAttitudeProvider(
      AbsoluteDate kickDate, double transitionTime, double exponent) {
    this.kickDate = kickDate;
    this.transitionTime = transitionTime;
    this.exponent = exponent;
  }

  @Override
  public Attitude getAttitude(PVCoordinatesProvider pvProv, AbsoluteDate date, Frame frame) {

    PVCoordinates pv = pvProv.getPVCoordinates(date, frame);
    Vector3D pos = pv.getPosition();
    Vector3D vel = pv.getVelocity();

    Vector3D zenith = pos.normalize();
    Vector3D prograde = vel.normalize();

    double dt = date.durationFrom(kickDate);
    double alpha = FastMath.min(1.0, FastMath.max(0.0, dt / transitionTime));
    alpha = Math.pow(alpha, exponent);

    Vector3D thrustDir = new Vector3D(1.0 - alpha, zenith, alpha, prograde).normalize();

    // Build rotation: map spacecraft +X to thrustDir
    // Use zenith as hint for the secondary axis
    Vector3D secondaryHint = Vector3D.crossProduct(thrustDir, zenith);
    if (secondaryHint.getNormSq() < 1e-10) {
      // thrustDir ≈ zenith at start, use prograde as fallback
      secondaryHint = Vector3D.crossProduct(thrustDir, prograde);
    }
    Vector3D yDir = secondaryHint.normalize();
    Vector3D zDir = Vector3D.crossProduct(thrustDir, yDir).normalize();

    Rotation rot = new Rotation(thrustDir, yDir, Vector3D.PLUS_I, Vector3D.PLUS_J);

    return new Attitude(date, frame, new AngularCoordinates(rot, Vector3D.ZERO));
  }

  @Override
  public <T extends CalculusFieldElement<T>> FieldAttitude<T> getAttitude(
      FieldPVCoordinatesProvider<T> pvProv, FieldAbsoluteDate<T> date, Frame frame) {

    FieldPVCoordinates<T> pv = pvProv.getPVCoordinates(date, frame);
    FieldVector3D<T> pos = pv.getPosition();
    FieldVector3D<T> vel = pv.getVelocity();

    FieldVector3D<T> zenith = pos.normalize();
    FieldVector3D<T> prograde = vel.normalize();

    T dt = date.durationFrom(kickDate);
    T zero = dt.getField().getZero();
    T one = dt.getField().getOne();
    T alphaRaw = FastMath.min(one, FastMath.max(zero, dt.divide(transitionTime)));
    T alpha = FastMath.pow(alphaRaw, exponent);
    T oneMinusAlpha = one.subtract(alpha);

    FieldVector3D<T> thrustDir =
        new FieldVector3D<>(oneMinusAlpha, zenith, alpha, prograde).normalize();

    // Secondary axis
    FieldVector3D<T> secondaryHint = FieldVector3D.crossProduct(thrustDir, zenith);
    if (secondaryHint.getNormSq().getReal() < 1e-10) {
      secondaryHint = FieldVector3D.crossProduct(thrustDir, prograde);
    }
    FieldVector3D<T> yDir = secondaryHint.normalize();
    FieldVector3D<T> zDir = FieldVector3D.crossProduct(thrustDir, yDir).normalize();

    FieldRotation<T> rot =
        new FieldRotation<>(
            thrustDir,
            yDir,
            new FieldVector3D<>(dt.getField(), Vector3D.PLUS_I),
            new FieldVector3D<>(dt.getField(), Vector3D.PLUS_J));

    return new FieldAttitude<>(
        date,
        frame,
        new FieldAngularCoordinates<>(
            rot,
            new FieldVector3D<>(dt.getField(), Vector3D.ZERO),
            new FieldVector3D<>(dt.getField(), Vector3D.ZERO)));
  }
}
