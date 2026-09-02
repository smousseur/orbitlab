package com.smousseur.orbitlab.engine.scene.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/** The numbers L2's instrument puts on screen, taken on real bodies at a real date. */
class CalibrationReadingTest {

  private static AbsoluteDate date;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    date = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /**
   * The one number that needs no eye. It is zero for every committed body or the composition is
   * wrong somewhere, and nothing seen on the globe could be believed.
   */
  @Test
  void theChainOffsetIsZeroOnEveryCommittedBody() {
    for (SolarSystemBody body : SolarSystemBody.values()) {
      // The Sun has no sub-solar point of its own, and the application never poses it either.
      if (body != SolarSystemBody.SUN && PlanetMeshCorrection.calibrationFor(body).isPresent()) {
        assertEquals(0.0, read(body).chainOffsetDeg(), 0.05, body.displayName());
      }
    }
  }

  /**
   * The column reported as sub-solar has to be the one the chain paints at the sub-solar longitude:
   * that is what makes it something an observer can go and look at on the map.
   */
  @Test
  void theSubSolarColumnIsTheOneTheChainPaintsThere() {
    CalibrationReading reading = read(SolarSystemBody.MARS);

    assertEquals(
        0.0,
        TexturePainting.wrap(
            reading.painting().longitudeOfColumn(reading.subSolarColumn())
                - reading.subSolarLongitudeDeg()),
        0.05);
  }

  /** The line reaches a bitmap font, which drops any glyph it does not carry in silence. */
  @Test
  void theFormattedLineIsPlainAscii() {
    String line = read(SolarSystemBody.JUPITER).format();

    assertEquals(line, line.replaceAll("[^\\x20-\\x7E]", "?"), "non-ASCII in: " + line);
  }

  private static CalibrationReading read(SolarSystemBody body) {
    PlanetMeshCalibration calibration = PlanetMeshCorrection.calibrationFor(body).orElseThrow();
    Frame icrf = OrekitService.get().icrf();
    Rotation rotationIcrf =
        icrf.getTransformTo(OrekitService.get().body(body).getBodyOrientedFrame(), date)
            .getRotation();
    Vector3D sunDirection =
        OrekitService.get()
            .body(SolarSystemBody.SUN)
            .getPosition(date, icrf)
            .subtract(OrekitService.get().body(body).getPosition(date, icrf))
            .normalize();
    double days = date.durationFrom(AbsoluteDate.J2000_EPOCH) / 86400.0;
    return CalibrationReading.take(
        body,
        calibration.measured(),
        calibration.lambda0DegAt(days),
        RenderTransform.toRenderQuaternion(
            rotationIcrf, PlanetMeshCorrection.correctionFor(body, date)),
        rotationIcrf,
        sunDirection);
  }
}
