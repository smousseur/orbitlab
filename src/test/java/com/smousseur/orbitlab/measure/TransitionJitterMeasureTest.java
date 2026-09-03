package com.smousseur.orbitlab.measure;

import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.CameraTransitionConfig;
import com.smousseur.orbitlab.engine.Easing;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * BUG-1 and BUG-5, measured on the transition that actually plays: a solar-view focus on Pluto, 2.5
 * s of {@code SMOOTHSTEP} at 60 fps, the pivot walked linearly between the two endpoints and the
 * camera distance interpolated geometrically — {@code CameraTransition}, reproduced here in {@code
 * float} exactly as it runs.
 *
 * <p>Two questions, and the fiches' reserve rests on both. What the observer's view direction does,
 * frame to frame, under the current scheme against a frame re-centred on the interpolated pivot;
 * and what the destination body's apparent size does over the same frames, since the pivot and the
 * distance are not on the same schedule.
 */
class TransitionJitterMeasureTest {

  private static final Logger logger = LogManager.getLogger(TransitionJitterMeasureTest.class);

  private static final CameraTransitionConfig TRANSITION = CameraTransitionConfig.defaults();

  /** Frame duration assumed for the replay, in real seconds. */
  private static final float TPF = 1f / 60f;

  /** Solar view default camera distance, {@code OrbitCameraConfig.defaultDistance()}. */
  private static final float SOLAR_DEFAULT_DISTANCE = 800f;

  private static Vector3f plutoAnchor;
  private static float arrivalDistance;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    Frame icrf = OrekitService.get().icrf();
    AbsoluteDate t0 = new AbsoluteDate(2026, 9, 3, 12, 0, 0.0, TimeScalesFactory.getUTC());
    Vector3D pluto =
        OrekitService.get().body(SolarSystemBody.PLUTO).getPVCoordinates(t0, icrf).getPosition();
    Vector3D sun =
        OrekitService.get().body(SolarSystemBody.SUN).getPVCoordinates(t0, icrf).getPosition();
    plutoAnchor =
        JmeVectorAdapter.toVector3f(
            RenderTransform.toRenderUnitsJmeAxes(pluto.subtract(sun), null, RenderContext.solar()));
    arrivalDistance =
        (float)
            (MeasureSupport.PLANET_FOCUS_RADII
                * PlanetRadius.radiusFor(SolarSystemBody.PLUTO)
                * RenderContext.solar().unitsPerMeter());
  }

  /**
   * The shake itself. {@code applyCameraPose} builds the camera as {@code pivotWorld.add(offset)}
   * and then looks at the pivot, so the rendered view direction is the difference of two
   * solar-magnitude floats — and the offset it is supposed to describe shrinks by nine orders of
   * magnitude while the pivot does not.
   */
  @Test
  void viewDirectionShakeAcrossTheTransition() {
    logger.info("=== JITTER / A — angular error of the view direction, per scheme ===");
    logger.info(
        "frame     t   camera dist(km)   today: err(deg)  err(px)   re-centred: err(deg)  err(px)");
    double worstTodayPx = 0;
    double worstRecentredPx = 0;
    double worstShakePx = 0;
    double lateShakePx = 0;
    Vector3f previousError = null;
    int frames = Math.round(TRANSITION.durationSec() / TPF);
    for (int f = 0; f <= frames; f++) {
      float u = easedProgress(f);
      float distance = currentDistance(u);
      Vector3f pivot = pivot(u);
      Vector3f offset = offset(distance);

      // Today: the whole pose is built at solar magnitude, in the heliocentric frame.
      Vector3f camToday = pivot.add(offset);
      double errToday = angleBetweenDeg(pivot.subtract(camToday), offset.negate());

      // Re-centred: the far root carries -pivot, so the camera's own pivot is the origin.
      Vector3f camRecentred = new Vector3f().add(offset);
      double errRecentred = angleBetweenDeg(new Vector3f().subtract(camRecentred), offset.negate());

      double pxPerDeg =
          MeasureSupport.pixelsPerRadian(MeasureSupport.adaptiveFovRad(distance)) * Math.PI / 180.0;
      worstTodayPx = Math.max(worstTodayPx, errToday * pxPerDeg);
      worstRecentredPx = Math.max(worstRecentredPx, errRecentred * pxPerDeg);

      // A constant error is an offset nobody sees; what shakes is its frame-to-frame change.
      Vector3f errorVector =
          pivot.subtract(camToday).normalizeLocal().subtractLocal(offset.negate().normalizeLocal());
      if (previousError != null) {
        double shakePx = Math.toDegrees(errorVector.subtract(previousError).length()) * pxPerDeg;
        worstShakePx = Math.max(worstShakePx, shakePx);
        if (f > frames - 40) {
          lateShakePx = Math.max(lateShakePx, shakePx);
        }
      }
      previousError = errorVector;

      if (f % 15 == 0 || f > frames - 4) {
        logger.info(
            String.format(
                Locale.ROOT,
                "%5d %5.3f %17.0f %17.4f %8.1f %21.4f %8.1f",
                f,
                f * TPF / TRANSITION.durationSec(),
                distance * MeasureSupport.KM_PER_UNIT,
                errToday,
                errToday * pxPerDeg,
                errRecentred,
                errRecentred * pxPerDeg));
      }
    }
    logger.info(
        String.format(
            Locale.ROOT,
            "worst view-direction error: today %.1f px, re-centred %.1f px",
            worstTodayPx,
            worstRecentredPx));
    logger.info(
        String.format(
            Locale.ROOT,
            "frame-to-frame shake of that error, today: %.1f px worst, %.1f px over the last 40"
                + " frames — re-centred, the error is exactly zero so there is nothing to shake",
            worstShakePx,
            lateShakePx));
  }

  /**
   * The framing schedule, which is the other half of the pop. The pivot walks the 5 324 units to
   * Pluto on a {@code SMOOTHSTEP} ramp while the camera distance collapses geometrically, so the
   * camera reaches its final <em>distance to the pivot</em> long before the pivot reaches Pluto.
   */
  @Test
  void whereTheDestinationActuallyIs() {
    logger.info("=== JITTER / B — where Pluto is while the camera says it has arrived ===");
    logger.info("frame     t   dist to pivot(km)   dist to Pluto(km)   Pluto radius(px)");
    int frames = Math.round(TRANSITION.durationSec() / TPF);
    double radiusUnits =
        PlanetRadius.radiusFor(SolarSystemBody.PLUTO) * RenderContext.solar().unitsPerMeter();
    for (int f = 0; f <= frames; f++) {
      float u = easedProgress(f);
      float distance = currentDistance(u);
      Vector3f pivot = pivot(u);
      Vector3f cam = pivot.add(offset(distance));
      double toPluto = plutoAnchor.subtract(cam).length();

      if (f % 15 == 0 || f > frames - 6) {
        logger.info(
            String.format(
                Locale.ROOT,
                "%5d %5.3f %19.0f %19.0f %18.1f",
                f,
                f * TPF / TRANSITION.durationSec(),
                distance * MeasureSupport.KM_PER_UNIT,
                toPluto * MeasureSupport.KM_PER_UNIT,
                MeasureSupport.projectedRadiusPx(radiusUnits, toPluto)));
      }
    }
  }

  /**
   * The same flight under the schedule that replaced it: the destination is the pivot, and what
   * ramps geometrically is the camera's distance to it. Directly comparable to the table above.
   */
  @Test
  void whereTheDestinationIsUnderTheNewSchedule() {
    double radiusUnits =
        PlanetRadius.radiusFor(SolarSystemBody.PLUTO) * RenderContext.solar().unitsPerMeter();
    Vector3f camStart = offset(SOLAR_DEFAULT_DISTANCE);
    double r0 = camStart.subtract(plutoAnchor).length();
    double r1 = arrivalDistance;

    logger.info("=== JITTER / C — the same flight, destination-centred schedule ===");
    logger.info("frame     t   dist to Pluto(km)   Pluto radius(px)   growth vs previous frame");
    int frames = Math.round(TRANSITION.durationSec() / TPF);
    double previousPx = 0;
    double worstGrowth = 0;
    for (int f = 0; f <= frames; f++) {
      double u = easedProgress(f);
      double r = Math.exp(Math.log(r0) + u * (Math.log(r1) - Math.log(r0)));
      double px = MeasureSupport.projectedRadiusPx(radiusUnits, r);
      double growth = previousPx > 0 ? px / previousPx : 1.0;
      worstGrowth = Math.max(worstGrowth, growth);

      if (f % 15 == 0 || f > frames - 4) {
        logger.info(
            String.format(
                Locale.ROOT,
                "%5d %5.3f %19.0f %18.1f %26s",
                f,
                f * TPF / TRANSITION.durationSec(),
                r * MeasureSupport.KM_PER_UNIT,
                px,
                previousPx > 0 ? String.format(Locale.ROOT, "x%.2f", growth) : "-"));
      }
      previousPx = px;
    }
    logger.info(
        String.format(
            Locale.ROOT,
            "worst frame-to-frame growth of the apparent radius: x%.2f (was x138 on the last frame)",
            worstGrowth));
  }

  private static float easedProgress(int frame) {
    float elapsed = Math.min(frame * TPF, TRANSITION.durationSec());
    return Easing.SMOOTHSTEP.apply(elapsed / TRANSITION.durationSec());
  }

  /** {@code CameraTransition.currentDistance}: geometric, not linear. */
  private static float currentDistance(float u) {
    double logSrc = Math.log(SOLAR_DEFAULT_DISTANCE);
    double logDst = Math.log(arrivalDistance);
    return (float) Math.exp(logSrc + u * (logDst - logSrc));
  }

  /** {@code CameraTransition.currentPivot}: a straight line from the Sun to Pluto, in float. */
  private static Vector3f pivot(float u) {
    return new Vector3f().interpolateLocal(plutoAnchor, u);
  }

  /**
   * The camera offset {@code applyCameraPose} builds. The bearing is held fixed: the orientation
   * finishes turning after 35 % of the duration, so it is constant over the stretch that matters.
   */
  private static Vector3f offset(float distance) {
    return new Vector3f(0.482f, 0.140f, 0.865f).normalizeLocal().multLocal(distance);
  }

  private static double angleBetweenDeg(Vector3f a, Vector3f b) {
    double dot = a.normalize().dot(b.normalize());
    return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
  }
}
