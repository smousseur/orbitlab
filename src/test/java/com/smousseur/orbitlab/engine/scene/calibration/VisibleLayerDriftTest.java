package com.smousseur.orbitlab.engine.scene.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.AxisConvention;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * L4 of {@code docs/orientation-planetes/01-decoupage.md}: what the texture shows is a cloud deck,
 * and a cloud deck does not turn at the rate Orekit turns the body frame at.
 *
 * <p>These tests never look at the drift constants. They measure how fast the rendered texture
 * turns <em>in inertial space</em> and compare that against the published rotation of the layer the
 * map actually depicts — the only check that can tell a right constant from a plausible one.
 */
class VisibleLayerDriftTest {

  /** Orekit's own rate for the Earth's IAU frame; the control body carries no drift at all. */
  private static final double EARTH_ROTATION_DEG_PER_DAY = 360.9856235;

  /**
   * Jupiter's System II, the frame its belts and the Great Red Spot are catalogued in. Orekit turns
   * the body in System III (870.536), which is why the difference has to be put back.
   */
  private static final double JUPITER_SYSTEM_II_DEG_PER_DAY = 870.270;

  /** Saturn's System I, the equatorial cloud rate its map depicts: one turn in 10 h 14 min. */
  private static final double SATURN_SYSTEM_I_DEG_PER_DAY = 360.0 * 86400.0 / 36840.0;

  /** Venus's cloud tops, retrograde, one lap in about 4.2 days. */
  private static final double VENUS_CLOUD_TOP_DEG_PER_DAY = -360.0 / 4.2;

  private static AbsoluteDate start;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    start = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /**
   * The control. A body whose map depicts its own solid surface must turn at exactly the rate
   * Orekit turns it, so any drift here would be a defect in the apparatus rather than a property of
   * an atmosphere.
   */
  @Test
  void theEarthsSurfaceTurnsAtOrekitsOwnRate() {
    assertEquals(
        EARTH_ROTATION_DEG_PER_DAY, inertialRateDegPerDay(SolarSystemBody.EARTH, 10.0), 0.001);
  }

  /** The Great Red Spot is a System II feature and has to keep System II time. */
  @Test
  void jupitersCloudDeckTurnsInSystemTwo() {
    assertEquals(
        JUPITER_SYSTEM_II_DEG_PER_DAY, inertialRateDegPerDay(SolarSystemBody.JUPITER, 10.0), 0.001);
  }

  /** Saturn's map is its equatorial deck, a full 25 minutes a turn faster than its radio period. */
  @Test
  void saturnsCloudDeckTurnsInSystemOne() {
    assertEquals(
        SATURN_SYSTEM_I_DEG_PER_DAY, inertialRateDegPerDay(SolarSystemBody.SATURN, 10.0), 0.001);
  }

  /**
   * Venus's model carries its cloud deck as a shell of its own, and that shell has to super-rotate:
   * fifty-eight times the ground's rate, and the other way from the map underneath it. The rest of
   * this test class checks a rotation applied to a whole model; this one checks the surplus applied
   * inside one.
   */
  @Test
  void venusCloudShellSuperRotatesOverItsOwnGround() {
    assertEquals(
        VENUS_CLOUD_TOP_DEG_PER_DAY, cloudShellRateDegPerDay(SolarSystemBody.VENUS, 10.0), 0.001);
  }

  /**
   * Same measurement as {@link #inertialRateDegPerDay}, on the shell rather than on the globe: the
   * shell's own spin is composed onto the model's rotation exactly as the scene graph composes it,
   * a turn about the measured pole applied to the model before the correction.
   */
  private static double cloudShellRateDegPerDay(SolarSystemBody body, double days) {
    MeshFrame frame = PlanetMeshCorrection.calibrationFor(body).orElseThrow().measured();
    AbsoluteDate end = start.shiftedBy(days * 86400.0);

    Rotation atStart = rotationIcrf(body, start);
    Vector3D pole = atStart.applyInverseTo(Vector3D.PLUS_K);
    Vector3D from = shellColumnZeroIcrf(body, frame, start, atStart);
    Vector3D to = shellColumnZeroIcrf(body, frame, end, rotationIcrf(body, end));

    double swept = signedAngleAboutDeg(from, to, pole);
    double expected = VENUS_CLOUD_TOP_DEG_PER_DAY * days;
    return (expected + TexturePainting.wrap(swept - expected)) / days;
  }

  private static Vector3D shellColumnZeroIcrf(
      SolarSystemBody body, MeshFrame frame, AbsoluteDate date, Rotation rotationIcrf) {
    Quaternion shell =
        new Quaternion()
            .fromAngleAxis(
                PlanetMeshCorrection.atmosphereShellSpinRad(body, date), frame.pole().normalize());
    Quaternion render =
        RenderTransform.toRenderQuaternion(
            rotationIcrf, PlanetMeshCorrection.correctionFor(body, date).mult(shell));
    Vector3f world = render.mult(TexturePainting.directionOfColumn(frame, 0.0));
    return AxisConvention.ICRF_TO_JME_Y_UP.jmeToIcrf(new Vector3D(world.x, world.y, world.z));
  }

  /**
   * How fast the direction carrying the texture's column 0 turns about the body's pole, in inertial
   * space, in degrees per day.
   *
   * <p>The turn count over ten days is far more than one, so the wrapped measurement is lifted onto
   * the right revolution using the rate the caller is testing for. That is not begging the
   * question: the two candidate rates for each body here differ by less than three degrees over the
   * whole baseline, so both land on the same revolution and only the residual — the thing under
   * test — can tell them apart.
   */
  private static double inertialRateDegPerDay(SolarSystemBody body, double days) {
    MeshFrame frame = PlanetMeshCorrection.calibrationFor(body).orElseThrow().measured();
    AbsoluteDate end = start.shiftedBy(days * 86400.0);

    Rotation atStart = rotationIcrf(body, start);
    Vector3D pole = atStart.applyInverseTo(Vector3D.PLUS_K);
    Vector3D from = columnZeroIcrf(body, frame, start, atStart);
    Vector3D to = columnZeroIcrf(body, frame, end, rotationIcrf(body, end));

    double swept = signedAngleAboutDeg(from, to, pole);
    double expected = expectedRateDegPerDay(body) * days;
    return (expected + TexturePainting.wrap(swept - expected)) / days;
  }

  /** The published rate the body is being tested against, used only to pick the revolution. */
  private static double expectedRateDegPerDay(SolarSystemBody body) {
    return switch (body) {
      case EARTH -> EARTH_ROTATION_DEG_PER_DAY;
      case JUPITER -> JUPITER_SYSTEM_II_DEG_PER_DAY;
      case SATURN -> SATURN_SYSTEM_I_DEG_PER_DAY;
      default -> throw new IllegalArgumentException("No published rate wired for " + body);
    };
  }

  private static Vector3D columnZeroIcrf(
      SolarSystemBody body, MeshFrame frame, AbsoluteDate date, Rotation rotationIcrf) {
    Quaternion render =
        RenderTransform.toRenderQuaternion(
            rotationIcrf, PlanetMeshCorrection.correctionFor(body, date));
    Vector3f world = render.mult(TexturePainting.directionOfColumn(frame, 0.0));
    return AxisConvention.ICRF_TO_JME_Y_UP.jmeToIcrf(new Vector3D(world.x, world.y, world.z));
  }

  private static double signedAngleAboutDeg(Vector3D from, Vector3D to, Vector3D axis) {
    Vector3D unit = axis.normalize();
    Vector3D a = from.subtract(unit.scalarMultiply(from.dotProduct(unit))).normalize();
    Vector3D b = to.subtract(unit.scalarMultiply(to.dotProduct(unit))).normalize();
    return Math.toDegrees(
        Math.atan2(Vector3D.crossProduct(a, b).dotProduct(unit), a.dotProduct(b)));
  }

  private static Rotation rotationIcrf(SolarSystemBody body, AbsoluteDate date) {
    Frame icrf = OrekitService.get().icrf();
    Frame bodyFrame = OrekitService.get().body(body).getBodyOrientedFrame();
    return icrf.getTransformTo(bodyFrame, date).getRotation();
  }
}
