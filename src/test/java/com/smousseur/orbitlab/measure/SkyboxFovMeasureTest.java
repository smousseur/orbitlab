package com.smousseur.orbitlab.measure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.FastMath;
import com.jme3.renderer.Camera;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

/**
 * BUG-2 — the confirmation the fiche asks for: <i>"loguer le {@code fovYDeg} calculé dans {@code
 * SkyboxAppState.update} et celui appliqué dans {@code updateAdaptiveFov}. Sur une frame où la
 * molette a tourné, les deux doivent diverger"</i>.
 *
 * <p>Run against a real {@code com.jme3.renderer.Camera}, which is pure arithmetic and needs no GL
 * context: the whole argument rests on what {@code setFrustumNear} does to {@code frustumTop}, and
 * that is the library's behaviour rather than ours.
 *
 * <p>The frame simulated below is the one the fiche describes: input is dispatched before any
 * {@code AppState.update}, so the wheel branch of {@code onAnalog} writes {@code near} through
 * {@code updateFrustum}, and {@code SkyboxAppState} — attached at {@code OrbitLabApplication:95},
 * before {@code OrbitCameraAppState} at {@code :112} — reads the couple before {@code
 * updateAdaptiveFov} repairs it.
 */
class SkyboxFovMeasureTest {

  private static final Logger logger = LogManager.getLogger(SkyboxFovMeasureTest.class);

  /**
   * {@code OrbitCameraAppState.applyWheelZoom}: {@code distance *= exp(-wheelDelta * zoomSpeed)}.
   */
  private static final double ZOOM_SPEED = MeasureSupport.CAM.zoomSpeed();

  /** Solar view default, {@code OrbitCameraConfig.defaultDistance()} = 20 000 × 0.04. */
  private static final float SOLAR_DEFAULT_DISTANCE = MeasureSupport.CAM.defaultDistance();

  /**
   * Earth focus: 5 radii, in solar units — the framing {@code CameraTransitionAppState} settles at.
   */
  private static final float EARTH_FOCUS_DISTANCE = 5f * 6_378_137f * 1e-9f;

  /**
   * The invariant the whole mechanism rests on: writing {@code near} alone leaves {@code top} where
   * it was, so the couple no longer describes the field of view it was set with.
   */
  @Test
  void setFrustumNearLeavesTopUntouched() {
    Camera cam = new Camera(1280, 720);
    cam.setFrustumPerspective(30f, 1280f / 720f, 1f, 1000f);
    float topBefore = cam.getFrustumTop();
    float fovBefore = fovYDeg(cam);

    cam.setFrustumNear(0.5f);

    logger.info("=== BUG-2 / A — what setFrustumNear does to the couple (top, near) ===");
    logger.info(
        String.format(
            Locale.ROOT,
            "top %.6f -> %.6f (unchanged), near 1.0 -> 0.5, fovY %.2f deg -> %.2f deg",
            topBefore,
            cam.getFrustumTop(),
            fovBefore,
            fovYDeg(cam)));
    assertEquals(topBefore, cam.getFrustumTop(), 0f, "setFrustumNear must not rescale frustumTop");
  }

  /**
   * The amplitude, in solar view: how far the sky's field of view is from the scene's on the single
   * frame a wheel event has moved {@code near} without moving {@code top}.
   */
  @Test
  void divergenceOnAWheelFrameInSolarView() {
    logger.info(
        "=== BUG-2 / B — solar view, wheel frame, d0 = {} units ===", SOLAR_DEFAULT_DISTANCE);
    logger.info("ticks  fov applied  fov the sky shows  divergence  star scale  edge star jump");
    for (int ticks = 1; ticks <= 5; ticks++) {
      report(SOLAR_DEFAULT_DISTANCE, ticks);
    }
  }

  /**
   * The same frame in planet view, where {@code near} is pinned to {@code nearMin} — {@code d ×
   * 0.001} is below the floor for any framing closer than 1 solar unit, which every planet focus
   * is. The couple therefore stays consistent and the jump cannot occur.
   */
  @Test
  void divergenceOnAWheelFrameInPlanetView() {
    float[] nf = MeasureSupport.frustum(EARTH_FOCUS_DISTANCE, 50_000f);
    logger.info(
        "=== BUG-2 / C — planet view (Earth focus, d = {} units) ===", EARTH_FOCUS_DISTANCE);
    logger.info(
        String.format(
            Locale.ROOT,
            "near = %.6f (floor is %.6f: %s), so the wheel does not move it",
            nf[0],
            MeasureSupport.CAM.nearMin(),
            nf[0] == MeasureSupport.CAM.nearMin() ? "PINNED" : "free"));
    logger.info("ticks  fov applied  fov the sky shows  divergence  star scale  edge star jump");
    for (int ticks = 1; ticks <= 5; ticks++) {
      report(EARTH_FOCUS_DISTANCE, ticks);
    }
  }

  /**
   * The fiche's second, independent question — <i>"le ciel doit-il suivre la FoV du tout ?"</i> —
   * costed: how much the stars grow between the two ends of the adaptive range.
   */
  @Test
  void starScaleAcrossTheZoomRange() {
    float narrow = MeasureSupport.adaptiveFovRad(MeasureSupport.CAM.minDistance());
    float wide = MeasureSupport.adaptiveFovRad(MeasureSupport.CAM.maxDistance());
    double factor = Math.tan(wide / 2) / Math.tan(narrow / 2);
    logger.info("=== BUG-2 / D — cost of locking the sky to the adaptive FoV ===");
    logger.info(
        String.format(
            Locale.ROOT,
            "fov %.1f deg (closest) .. %.1f deg (farthest) -> stars change scale by x%.2f over the"
                + " zoom range",
            Math.toDegrees(narrow),
            Math.toDegrees(wide),
            factor));
  }

  /** One wheel frame at {@code d0}, {@code ticks} events landing in it. */
  private void report(float d0, int ticks) {
    float fovAppliedBefore = MeasureSupport.adaptiveFovRad(d0);
    float[] before = MeasureSupport.frustum(d0, 0f);

    Camera cam =
        new Camera((int) MeasureSupport.SCREEN_WIDTH_PX, (int) MeasureSupport.SCREEN_HEIGHT_PX);
    cam.setFrustumPerspective(
        (float) Math.toDegrees(fovAppliedBefore),
        MeasureSupport.SCREEN_WIDTH_PX / MeasureSupport.SCREEN_HEIGHT_PX,
        before[0],
        before[1]);

    // The wheel branch of onAnalog: dolly, pose, updateFrustum. No updateAdaptiveFov.
    float d1 =
        FastMath.clamp(
            (float) (d0 * Math.exp(-ticks * ZOOM_SPEED)),
            MeasureSupport.CAM.minDistance(),
            MeasureSupport.CAM.maxDistance());
    float[] after = MeasureSupport.frustum(d1, 0f);
    cam.setFrustumNear(after[0]);
    cam.setFrustumFar(after[1]);

    // SkyboxAppState.update, reading the couple before OrbitCameraAppState repairs it.
    float fovSky = 2f * FastMath.atan2(cam.getFrustumTop(), cam.getFrustumNear());
    float fovCorrect = MeasureSupport.adaptiveFovRad(d1);

    double starScale = Math.tan(fovSky / 2) / Math.tan(fovCorrect / 2);
    double edgeJumpPx = MeasureSupport.SCREEN_HEIGHT_PX * 0.5 * Math.abs(1 - 1 / starScale);

    logger.info(
        String.format(
            Locale.ROOT,
            "%5d %11.2f %18.2f %11.2f %11.3f %14.1f px",
            ticks,
            Math.toDegrees(fovCorrect),
            Math.toDegrees(fovSky),
            Math.toDegrees(fovSky - fovCorrect),
            starScale,
            edgeJumpPx));
  }

  private static float fovYDeg(Camera cam) {
    return 2f * FastMath.atan2(cam.getFrustumTop(), cam.getFrustumNear()) * FastMath.RAD_TO_DEG;
  }
}
