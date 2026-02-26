package com.smousseur.orbitlab.simulation.mission.attitude;

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

    // Horizontal (tangential) component of velocity — this is the "orbit plane" direction
    double vRadial = Vector3D.dotProduct(vel, zenith);
    Vector3D vTangential = vel.subtract(new Vector3D(vRadial, zenith));
    Vector3D horizDir;
    if (vTangential.getNormSq() > 1e-6) {
      horizDir = vTangential.normalize();
    } else {
      // Fallback: use velocity direction if no tangential component yet
      horizDir = vel.normalize();
    }

    double dt = date.durationFrom(kickDate);
    double alpha = FastMath.min(1.0, FastMath.max(0.0, dt / transitionTime));
    alpha = Math.pow(alpha, exponent);

    // Interpolate between zenith (vertical) and horizontal (tangential) direction
    // alpha=0 → pure zenith, alpha=1 → pure horizontal (prograde orbit direction)
    Vector3D thrustDir = new Vector3D(1.0 - alpha, zenith, alpha, horizDir).normalize();

    // Build rotation: map spacecraft +X to thrustDir
    Vector3D secondaryHint = Vector3D.crossProduct(thrustDir, zenith);
    if (secondaryHint.getNormSq() < 1e-10) {
      secondaryHint = Vector3D.crossProduct(thrustDir, horizDir);
    }
    Vector3D yDir = secondaryHint.normalize();

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

    // Horizontal (tangential) component of velocity
    T vRadial = FieldVector3D.dotProduct(vel, zenith);
    FieldVector3D<T> vTangential = vel.subtract(new FieldVector3D<>(vRadial, zenith));
    FieldVector3D<T> horizDir;
    if (vTangential.getNormSq().getReal() > 1e-6) {
      horizDir = vTangential.normalize();
    } else {
      horizDir = vel.normalize();
    }

    T dt = date.durationFrom(kickDate);
    T zero = dt.getField().getZero();
    T one = dt.getField().getOne();
    T alphaRaw = FastMath.min(one, FastMath.max(zero, dt.divide(transitionTime)));
    T alpha = FastMath.pow(alphaRaw, exponent);
    T oneMinusAlpha = one.subtract(alpha);

    // Interpolate between zenith and horizontal direction
    FieldVector3D<T> thrustDir =
        new FieldVector3D<>(oneMinusAlpha, zenith, alpha, horizDir).normalize();

    // Secondary axis
    FieldVector3D<T> secondaryHint = FieldVector3D.crossProduct(thrustDir, zenith);
    if (secondaryHint.getNormSq().getReal() < 1e-10) {
      secondaryHint = FieldVector3D.crossProduct(thrustDir, horizDir);
    }
    FieldVector3D<T> yDir = secondaryHint.normalize();

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
