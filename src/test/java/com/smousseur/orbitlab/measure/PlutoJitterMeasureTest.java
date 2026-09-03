package com.smousseur.orbitlab.measure;

import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * BUG-1 — the measurement the fiche asks for before any code is written: <i>"loguer sur quelques
 * frames la position monde de l'ancre de Pluton et celle d'un sommet de son orbite, et confirmer
 * que l'amplitude du tremblement est bien de l'ordre de 500 km et non de plusieurs milliers"</i>.
 *
 * <p>Nothing here runs the application. The trembling is a property of the {@code float}
 * conversions {@code PlanetPresenter.updatePose} and {@code OrbitLineFactory.putRenderUnits}
 * perform, and of the {@code float} addition {@code OrbitCameraAppState.applyCameraPose} performs
 * on top of them, so it can be reproduced exactly from real ephemeris positions.
 */
class PlutoJitterMeasureTest {

  private static final Logger logger = LogManager.getLogger(PlutoJitterMeasureTest.class);

  /** Arbitrary but fixed epoch, so every number below is reproducible. */
  private static AbsoluteDate t0;

  private static Frame icrf;

  private static final List<SolarSystemBody> LADDER =
      List.of(
          SolarSystemBody.EARTH,
          SolarSystemBody.JUPITER,
          SolarSystemBody.NEPTUNE,
          SolarSystemBody.PLUTO);

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    icrf = OrekitService.get().icrf();
    t0 = new AbsoluteDate(2026, 9, 3, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /**
   * How much of a body's heliocentric position survives the conversion to {@code float}, and what
   * that quantum is worth against the framing the camera settles at when the body is focused.
   */
  @Test
  void quantisationOfTheFarFrame() {
    logger.info("=== BUG-1 / A — quantum of the far frame, per body ===");
    logger.info(
        "body     r_helio(AU)  ulp(km)  anchor err(km)  focus dist(km)  quantum/dist  px @focus");
    for (SolarSystemBody body : LADDER) {
      Vector3D helio = helio(body, t0);
      Vector3D exactUnits = exactUnits(helio);
      Vector3f anchor = anchorFloat(helio);

      double errKm = errorKm(anchor, exactUnits);
      double ulpKm = Math.ulp((float) maxAbs(exactUnits)) * MeasureSupport.KM_PER_UNIT;

      double radiusUnits = PlanetRadius.radiusFor(body) * RenderContext.solar().unitsPerMeter();
      double focusDistUnits = MeasureSupport.PLANET_FOCUS_RADII * radiusUnits;
      double focusDistKm = focusDistUnits * MeasureSupport.KM_PER_UNIT;
      double px =
          ulpKm
              / focusDistKm
              * MeasureSupport.pixelsPerRadian(
                  MeasureSupport.adaptiveFovRad((float) focusDistUnits));

      logger.info(
          String.format(
              Locale.ROOT,
              "%-8s %11.2f %8.1f %15.1f %15.0f %12.2f%% %10.1f",
              body.displayName(),
              helio.getNorm() / 1.495978707e11,
              ulpKm,
              errKm,
              focusDistKm,
              100.0 * ulpKm / focusDistKm,
              px));
    }
  }

  /**
   * The term the fiche does not name: {@code applyCameraPose} builds the camera position as {@code
   * pivotWorld.add(offset)} in {@code float}, and in solar view the pivot carries the body's own
   * heliocentric magnitude. The offset is the whole framing distance, so what survives that
   * addition is the framing itself.
   */
  @Test
  void cameraOffsetIsQuantisedAtSolarMagnitude() {
    logger.info("=== BUG-1 / B — quantisation of the camera offset in solar view ===");
    logger.info("body     focus dist(km)  max err(km)  mean err(km)  max err/dist   max px");
    Random directions = new Random(20260903L);
    for (SolarSystemBody body : LADDER) {
      Vector3f pivot = anchorFloat(helio(body, t0));
      double radiusUnits = PlanetRadius.radiusFor(body) * RenderContext.solar().unitsPerMeter();
      float d = (float) (MeasureSupport.PLANET_FOCUS_RADII * radiusUnits);

      double maxErrUnits = 0;
      double sumErrUnits = 0;
      int samples = 4096;
      for (int i = 0; i < samples; i++) {
        Vector3f offset = unitVector(directions).multLocal(d);
        Vector3f cam = pivot.add(offset);
        double errUnits = cam.subtract(pivot).subtract(offset).length();
        maxErrUnits = Math.max(maxErrUnits, errUnits);
        sumErrUnits += errUnits;
      }

      double distKm = d * MeasureSupport.KM_PER_UNIT;
      double maxErrKm = maxErrUnits * MeasureSupport.KM_PER_UNIT;
      double px =
          maxErrKm / distKm * MeasureSupport.pixelsPerRadian(MeasureSupport.adaptiveFovRad(d));
      logger.info(
          String.format(
              Locale.ROOT,
              "%-8s %15.0f %12.1f %13.1f %13.2f%% %8.1f",
              body.displayName(),
              distKm,
              maxErrKm,
              sumErrUnits / samples * MeasureSupport.KM_PER_UNIT,
              100.0 * maxErrKm / distKm,
              px));
    }
  }

  /**
   * Whether the quantum is re-rolled often enough to read as trembling rather than as a fixed
   * offset. A body has to travel one {@code ulp} for its rounded position to change at all, so the
   * answer is a function of the time compression, not of the frame rate alone.
   */
  @Test
  void reRollRateVersusSimulationSpeed() {
    logger.info("=== BUG-1 / C — how often the rounding is re-rolled, at 60 fps ===");
    for (SolarSystemBody body : List.of(SolarSystemBody.EARTH, SolarSystemBody.PLUTO)) {
      Vector3D helio = helio(body, t0);
      double speedKmPerS =
          OrekitService.get().body(body).getPVCoordinates(t0, icrf).getVelocity().getNorm()
              / 1000.0;
      double ulpKm = Math.ulp((float) maxAbs(exactUnits(helio))) * MeasureSupport.KM_PER_UNIT;
      double thresholdSpeed = ulpKm / speedKmPerS * 60.0;
      logger.info(
          String.format(
              Locale.ROOT,
              "%s: |v| = %.2f km/s, ulp = %.1f km -> one re-roll per frame from x%.0f real time",
              body.displayName(),
              speedKmPerS,
              ulpKm,
              thresholdSpeed));

      for (double speed : new double[] {1, 60, 3600, 86_400, 864_000}) {
        double dt = speed / 60.0;
        Vector3f previous = anchorFloat(helio(body, t0));
        int changed = 0;
        double maxJumpKm = 0;
        int frames = 300;
        for (int f = 1; f <= frames; f++) {
          Vector3f current = anchorFloat(helio(body, t0.shiftedBy(f * dt)));
          double jumpKm = current.subtract(previous).length() * MeasureSupport.KM_PER_UNIT;
          if (!current.equals(previous)) {
            changed++;
          }
          maxJumpKm = Math.max(maxJumpKm, jumpKm);
          previous = current;
        }
        logger.info(
            String.format(
                Locale.ROOT,
                "   x%-8.0f  frames whose rounded anchor moved: %3d/%d   max frame-to-frame"
                    + " step: %.1f km",
                speed,
                changed,
                frames,
                maxJumpKm));
      }
    }
  }

  private static Vector3D helio(SolarSystemBody body, AbsoluteDate t) {
    Vector3D p = OrekitService.get().body(body).getPVCoordinates(t, icrf).getPosition();
    Vector3D sun =
        OrekitService.get().body(SolarSystemBody.SUN).getPVCoordinates(t, icrf).getPosition();
    return p.subtract(sun);
  }

  /** The production conversion, stopped one step before the {@code float} cast. */
  private static Vector3D exactUnits(Vector3D helioMeters) {
    return RenderTransform.toRenderUnitsJmeAxes(helioMeters, null, RenderContext.solar());
  }

  /** The production conversion in full, as {@code PlanetPresenter.updatePose} performs it. */
  private static Vector3f anchorFloat(Vector3D helioMeters) {
    return JmeVectorAdapter.toVector3f(exactUnits(helioMeters));
  }

  private static double errorKm(Vector3f rounded, Vector3D exact) {
    double dx = rounded.x - exact.getX();
    double dy = rounded.y - exact.getY();
    double dz = rounded.z - exact.getZ();
    return Math.sqrt(dx * dx + dy * dy + dz * dz) * MeasureSupport.KM_PER_UNIT;
  }

  private static double maxAbs(Vector3D v) {
    return Math.max(Math.abs(v.getX()), Math.max(Math.abs(v.getY()), Math.abs(v.getZ())));
  }

  private static Vector3f unitVector(Random random) {
    double z = 2 * random.nextDouble() - 1;
    double phi = 2 * Math.PI * random.nextDouble();
    double r = Math.sqrt(1 - z * z);
    return new Vector3f((float) (r * Math.cos(phi)), (float) (r * Math.sin(phi)), (float) z);
  }
}
