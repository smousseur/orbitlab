package com.smousseur.orbitlab.measure;

import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

/**
 * BUG-5 — the two measures the fiche asks for, as far as they can be settled without a screen.
 *
 * <p>The first — <i>"le pop est-il exactement synchrone avec la fin de la transition ?"</i> — is an
 * observation and stays one. What is computable is its <b>amplitude</b>: the veto {@code
 * PlanetHudMarkersAppState:71} keeps the destination body on its 16 px icon until the focus flips,
 * and {@code CameraTransitionAppState} flips it with the camera already at 5 radii. This class
 * measures what the icon is replaced by on that frame.
 *
 * <p>The second — <i>"le pop existe-t-il aussi sans transition ?"</i> — is answerable here: with
 * the veto true, only the 6↔10 px hysteresis of {@code LodView} plays, and one wheel tick moves the
 * projected radius by a known fraction.
 */
class FocusPopMeasureTest {

  private static final Logger logger = LogManager.getLogger(FocusPopMeasureTest.class);

  /** {@code LodView.updateScreen}: threshold to promote the 3D model when it is not showing yet. */
  private static final double SHOW_3D_THRESHOLD_PX = 10.0;

  /** The same, to demote it once it shows — the other end of the hysteresis band. */
  private static final double HIDE_3D_THRESHOLD_PX = 6.0;

  @Test
  void whatTheIconIsReplacedBy() {
    logger.info("=== BUG-5 / A — the disc the veto holds back until the last frame ===");
    logger.info(
        "body      radius(km)  focus dist(km)  fov(deg)  disc radius(px)  disc/screen  icon->disc");
    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (body == SolarSystemBody.SUN) {
        continue;
      }
      double radiusUnits = PlanetRadius.radiusFor(body) * RenderContext.solar().unitsPerMeter();
      double d = MeasureSupport.PLANET_FOCUS_RADII * radiusUnits;
      double px = MeasureSupport.projectedRadiusPx(radiusUnits, d);
      logger.info(
          String.format(
              Locale.ROOT,
              "%-9s %10.0f %15.0f %9.1f %16.0f %11.2f%% %10.0fx",
              body.displayName(),
              PlanetRadius.radiusFor(body) / 1000.0,
              d * MeasureSupport.KM_PER_UNIT,
              Math.toDegrees(MeasureSupport.adaptiveFovRad((float) d)),
              px,
              200.0 * px / MeasureSupport.SCREEN_HEIGHT_PX,
              2 * px / MeasureSupport.ICON_SIZE_PX));
    }
  }

  /**
   * How much earlier the level of detail would have promoted the model, had the veto not been held
   * on the source body for the whole approach: the distance at which the projected radius crosses
   * the 10 px threshold, expressed in radii of the body being flown at.
   */
  @Test
  void whenTheModelWouldHaveBeenWarranted() {
    logger.info("=== BUG-5 / B — where the 10 px threshold actually falls ===");
    logger.info("body      threshold dist(radii)  ratio to the 5-radii framing");
    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (body == SolarSystemBody.SUN) {
        continue;
      }
      double radiusUnits = PlanetRadius.radiusFor(body) * RenderContext.solar().unitsPerMeter();
      double radii = thresholdDistanceInRadii(radiusUnits);
      logger.info(
          String.format(
              Locale.ROOT,
              "%-9s %22.0f %28.0fx",
              body.displayName(),
              radii,
              radii / MeasureSupport.PLANET_FOCUS_RADII));
    }
  }

  /**
   * The no-transition case: zooming with the wheel on a body that is already focused, where the
   * veto is permanently true. One tick multiplies the distance by {@code exp(±0.12)}, so the
   * projected radius crosses the threshold in steps of that size.
   */
  @Test
  void withoutATransition() {
    double radiusUnits =
        PlanetRadius.radiusFor(SolarSystemBody.EARTH) * RenderContext.solar().unitsPerMeter();
    double dSwitch = distanceForProjectedRadius(radiusUnits, SHOW_3D_THRESHOLD_PX);
    double dOneTickOut = dSwitch * Math.exp(MeasureSupport.CAM.zoomSpeed());
    double pxBefore = MeasureSupport.projectedRadiusPx(radiusUnits, dOneTickOut);

    logger.info("=== BUG-5 / C — the same switch reached by the wheel, veto true ===");
    logger.info(
        String.format(
            Locale.ROOT,
            "Earth: model appears at %.0f px projected radius; one tick earlier it was %.1f px"
                + " -> the frame-to-frame change is %.1f px, against the %.0f px of the icon it"
                + " replaces",
            SHOW_3D_THRESHOLD_PX,
            pxBefore,
            2 * (SHOW_3D_THRESHOLD_PX - pxBefore),
            MeasureSupport.ICON_SIZE_PX));
    logger.info(
        String.format(
            Locale.ROOT,
            "hysteresis band %.0f..%.0f px is %.1f wheel ticks wide",
            HIDE_3D_THRESHOLD_PX,
            SHOW_3D_THRESHOLD_PX,
            Math.log(SHOW_3D_THRESHOLD_PX / HIDE_3D_THRESHOLD_PX)
                / MeasureSupport.CAM.zoomSpeed()));
  }

  private static double thresholdDistanceInRadii(double radiusUnits) {
    return distanceForProjectedRadius(radiusUnits, SHOW_3D_THRESHOLD_PX) / radiusUnits;
  }

  /**
   * Bisects the distance at which a body of {@code radiusUnits} projects to {@code targetPx}. The
   * field of view varies with the distance, so the relation is not a simple inverse.
   */
  private static double distanceForProjectedRadius(double radiusUnits, double targetPx) {
    double low = radiusUnits;
    double high = radiusUnits * 1e9;
    for (int i = 0; i < 200; i++) {
      double mid = Math.sqrt(low * high);
      if (MeasureSupport.projectedRadiusPx(radiusUnits, mid) > targetPx) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return Math.sqrt(low * high);
  }
}
