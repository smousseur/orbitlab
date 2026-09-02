package com.smousseur.orbitlab.engine.scene.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.AxisConvention;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * L2's ruler, checked against the property that makes it a ruler at all: the body-fixed longitude
 * at which the chain paints a texture column is a constant of the asset, so it must not move when
 * anything else does (see {@code docs/orientation-planetes/01-decoupage.md}, L2).
 */
class TexturePaintingTest {

  private static AbsoluteDate early;
  private static AbsoluteDate late;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    early = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
    late = early.shiftedBy(100 * 86400.0);
  }

  /**
   * The instrument's whole claim. A camera azimuth is a free parameter and so is a date; the
   * reading has to be indifferent to both, or it measures the observation rather than the asset.
   * The date is the harder of the two — the Earth has turned a hundred times between these samples
   * — and the camera does not enter the computation at all.
   */
  @Test
  void readsTheSameLongitudeWhateverTheBodyHasTurnedTo() {
    TexturePainting atEarly = paintingOf(SolarSystemBody.EARTH, early);
    TexturePainting atLate = paintingOf(SolarSystemBody.EARTH, late);

    assertEquals(
        atEarly.columnZeroLongitudeDeg(), atLate.columnZeroLongitudeDeg(), 0.05, "column 0");
    assertEquals(atEarly.degreesPerColumn(), atLate.degreesPerColumn(), 0.05, "scale");
  }

  /**
   * λ0 says what longitude the texture's column 0 carries — at this date, drift included — so the
   * chain has to paint that column at that longitude — for every body, with no exception and no
   * per-body fudge. This is the closure of the whole composition: measured frame, alignment, λ0 and
   * the fixed conversions of {@code RenderTransform} all have to be right at once for a single body
   * to pass, and the table has to be transcribed correctly for all ten to.
   */
  @Test
  void everyBodyPaintsColumnZeroWhereItsCalibrationSaysItShould() {
    double days = early.durationFrom(AbsoluteDate.J2000_EPOCH) / 86400.0;
    for (SolarSystemBody body : SolarSystemBody.values()) {
      PlanetMeshCalibration calibration = PlanetMeshCorrection.calibrationFor(body).orElse(null);
      if (calibration == null) {
        continue;
      }
      double error =
          TexturePainting.wrap(
              paintingOf(body, early).columnZeroLongitudeDeg() - calibration.lambda0DegAt(days));
      assertEquals(0.0, error, 0.05, body.displayName());
    }
  }

  /** Texture longitude runs east with {@code u}, a full turn over the map. */
  @Test
  void aFullMapIsOneTurnOfLongitude() {
    assertEquals(360.0, paintingOf(SolarSystemBody.EARTH, early).degreesPerColumn(), 0.05);
  }

  /**
   * The fact that decides the sign of every longitude in this chantier, and the one a reader is
   * most likely to get backwards: {@link MeshFrame#pole()} is the {@code v = 0} edge of the map,
   * and the chain paints it at the body's <em>south</em> pole. It is not an error — the reference
   * textures are stored south-row-first, measured on {@code earth}'s own map, whose band at {@code
   * v = 0.2} is 96 % ocean and can therefore only be 54° south. It does mean a turn about that
   * direction runs longitude backwards, which is why {@code PlanetMeshCorrection} negates λ0.
   */
  @Test
  void theVZeroEdgeIsPaintedAtTheBodySouthPole() {
    MeshFrame frame =
        PlanetMeshCorrection.calibrationFor(SolarSystemBody.EARTH).orElseThrow().measured();
    Rotation rotationIcrf = rotationIcrf(SolarSystemBody.EARTH, early);
    Quaternion render =
        RenderTransform.toRenderQuaternion(
            rotationIcrf, PlanetMeshCorrection.correctionFor(SolarSystemBody.EARTH, early));

    Vector3f world = render.mult(frame.pole().normalize());
    Vector3D bodyFixed =
        rotationIcrf.applyTo(
            AxisConvention.ICRF_TO_JME_Y_UP.jmeToIcrf(new Vector3D(world.x, world.y, world.z)));
    double latitudeDeg = Math.toDegrees(Math.asin(bodyFixed.getZ() / bodyFixed.getNorm()));

    assertEquals(-90.0, latitudeDeg, 0.05);
  }

  private static TexturePainting paintingOf(SolarSystemBody body, AbsoluteDate date) {
    MeshFrame frame = PlanetMeshCorrection.calibrationFor(body).orElseThrow().measured();
    Rotation rotationIcrf = rotationIcrf(body, date);
    return TexturePainting.measure(
        frame,
        RenderTransform.toRenderQuaternion(
            rotationIcrf, PlanetMeshCorrection.correctionFor(body, date)),
        rotationIcrf);
  }

  private static Rotation rotationIcrf(SolarSystemBody body, AbsoluteDate date) {
    Frame icrf = OrekitService.get().icrf();
    Frame bodyFrame = OrekitService.get().body(body).getBodyOrientedFrame();
    return icrf.getTransformTo(bodyFrame, date).getRotation();
  }
}
