package com.smousseur.orbitlab.simulation.mission.attitude;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.Field;
import org.hipparchus.geometry.euclidean.threed.FieldRotation;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FieldAttitude;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.AngularCoordinates;
import org.orekit.utils.FieldAngularCoordinates;
import org.orekit.utils.FieldPVCoordinatesProvider;
import org.orekit.utils.PVCoordinatesProvider;

/**
 * Attitude provider that orients the spacecraft thrust axis along the local zenith direction
 * (radially outward from the central body). Used during vertical ascent phases where the vehicle
 * must thrust straight up from the launch site.
 */
public class ZenithThrustAttitudeProvider implements AttitudeProvider {

  private final Frame inertialFrame;

  /**
   * Creates a zenith thrust attitude provider.
   *
   * @param inertialFrame the inertial reference frame used for computing the zenith direction
   */
  public ZenithThrustAttitudeProvider(Frame inertialFrame) {
    this.inertialFrame = inertialFrame;
  }

  @Override
  public Attitude getAttitude(PVCoordinatesProvider pvProv, AbsoluteDate date, Frame frame) {
    Vector3D pos = pvProv.getPVCoordinates(date, inertialFrame).getPosition();
    Rotation rot = buildRotation(pos);
    return new Attitude(date, inertialFrame, new AngularCoordinates(rot));
  }

  @Override
  public <T extends CalculusFieldElement<T>> FieldAttitude<T> getAttitude(
      FieldPVCoordinatesProvider<T> pvProv, FieldAbsoluteDate<T> date, Frame frame) {

    FieldVector3D<T> pos = pvProv.getPVCoordinates(date, inertialFrame).getPosition();
    FieldRotation<T> rot = buildFieldRotation(pos);
    return new FieldAttitude<>(
        date,
        inertialFrame,
        new FieldAngularCoordinates<>(
            rot, FieldVector3D.getZero(date.getField()), FieldVector3D.getZero(date.getField())));
  }

  private Rotation buildRotation(Vector3D pos) {
    Vector3D xAxis = pos.normalize();
    Vector3D yAxis = Vector3D.crossProduct(Vector3D.PLUS_K, xAxis);
    if (yAxis.getNorm() < 1e-10) {
      yAxis = Vector3D.crossProduct(Vector3D.PLUS_I, xAxis);
    }
    yAxis = yAxis.normalize();
    return new Rotation(xAxis, yAxis, Vector3D.PLUS_I, Vector3D.PLUS_J);
  }

  private <T extends CalculusFieldElement<T>> FieldRotation<T> buildFieldRotation(
      FieldVector3D<T> pos) {
    FieldVector3D<T> xAxis = pos.normalize();
    Field<T> field = pos.getX().getField();
    FieldVector3D<T> plusK = new FieldVector3D<>(field.getZero(), field.getZero(), field.getOne());
    FieldVector3D<T> plusI = new FieldVector3D<>(field.getOne(), field.getZero(), field.getZero());
    FieldVector3D<T> yAxis = FieldVector3D.crossProduct(plusK, xAxis);
    if (yAxis.getNorm().getReal() < 1e-10) {
      yAxis = FieldVector3D.crossProduct(plusI, xAxis);
    }
    yAxis = yAxis.normalize();
    return new FieldRotation<>(
        xAxis,
        yAxis,
        new FieldVector3D<>(field.getOne(), field.getZero(), field.getZero()),
        new FieldVector3D<>(field.getZero(), field.getOne(), field.getZero()));
  }
}
