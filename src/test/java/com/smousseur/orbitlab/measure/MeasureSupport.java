package com.smousseur.orbitlab.measure;

import com.jme3.math.FastMath;
import com.smousseur.orbitlab.engine.EngineConfig;
import com.smousseur.orbitlab.engine.OrbitCameraConfig;

/**
 * Shared arithmetic for the {@code H-RND} measurement harness (BUG-1, BUG-2, BUG-5): the far
 * camera's frustum and its adaptive field of view, reproduced so that a measurement can be made
 * without a GL context or a running application.
 *
 * <p><b>These are transcriptions, not the production code.</b> {@code fovMinRad}, {@code fovMaxRad}
 * and {@code fovCurveK} are private fields of {@code OrbitCameraAppState} (:80-82), and {@code
 * updateFrustum} / {@code normalizedZoom01} are private methods of the same class. Every value and
 * every branch below is copied from there. If one of them moves, this harness measures a camera the
 * application no longer has — which is exactly why the harness is throwaway and the numbers it
 * produces belong in {@code docs/bugs.md} rather than in a pinned assertion.
 */
final class MeasureSupport {

  /** 1 solar unit = 1e9 m = 1e6 km ({@code RenderContext.Solar.SOLAR_METERS_PER_UNIT}). */
  static final double KM_PER_UNIT = 1.0e6;

  /** Window height, from {@code OrbitLabApplication:73} — {@code setResolution(1280, 720)}. */
  static final float SCREEN_HEIGHT_PX = 720f;

  static final float SCREEN_WIDTH_PX = 1280f;

  /** Icon size, from {@code BillboardIconView.ICON_SIZE}. */
  static final float ICON_SIZE_PX = 16f;

  /** Multiple of a body's radius the camera settles at, from {@code CameraTransitionAppState}. */
  static final double PLANET_FOCUS_RADII = 5.0;

  /** {@code OrbitCameraAppState:80-82} — narrow when close, wide when far. */
  private static final float FOV_MIN_RAD = (float) Math.toRadians(15.0);

  private static final float FOV_MAX_RAD = (float) Math.toRadians(60.0);

  private static final float FOV_CURVE_K = 0.9f;

  static final OrbitCameraConfig CAM = EngineConfig.defaultSolarSystem().orbitCamera();

  private MeasureSupport() {}

  /** Copy of {@code OrbitCameraAppState.normalizedZoom01}. */
  static float normalizedZoom01(float distance) {
    float d = FastMath.clamp(distance, CAM.minDistance(), CAM.maxDistance());
    float logMin = (float) Math.log(CAM.minDistance());
    float logMax = (float) Math.log(CAM.maxDistance());
    float logD = (float) Math.log(d);
    return FastMath.clamp((logD - logMin) / (logMax - logMin), 0f, 1f);
  }

  /** Copy of {@code OrbitCameraAppState.updateAdaptiveFov}, returning the angle it would apply. */
  static float adaptiveFovRad(float distance) {
    float t = normalizedZoom01(distance);
    float tt = (float) Math.pow(t, FOV_CURVE_K);
    return FastMath.interpolateLinear(tt, FOV_MIN_RAD, FOV_MAX_RAD);
  }

  /**
   * Copy of {@code OrbitCameraAppState.updateFrustum}: the near and far planes it writes, in that
   * order, for a camera distance and the dynamic far floor {@code FloatingOriginAppState} sets.
   */
  static float[] frustum(float distance, float farFloor) {
    float near = clampFinite(distance * CAM.nearFactor(), CAM.nearMin(), CAM.nearMax());
    float far = clampFinite(distance * CAM.farFactor(), 0.001f, CAM.farMax());
    far = Math.max(far, near * 10f);
    far = Math.max(far, 10f);
    far = Math.max(far, farFloor);
    float margin = Math.max(near * 2f, 0.01f);
    float maxNear = Math.max(0.0001f, distance - margin);
    if (near > maxNear) {
      near = maxNear;
      far = Math.max(far, near * 10f);
    }
    return new float[] {near, far};
  }

  private static float clampFinite(float v, float min, float max) {
    return Float.isFinite(v) ? FastMath.clamp(v, min, max) : min;
  }

  /**
   * Screen pixels subtended by one radian at the centre of the view — the conversion {@code
   * LodView.updateScreen} performs, written once here.
   */
  static double pixelsPerRadian(float fovRad) {
    return SCREEN_HEIGHT_PX * 0.5 / Math.tan(fovRad / 2.0);
  }

  /**
   * Projected radius in pixels of a body of {@code radiusUnits} seen from {@code distanceUnits},
   * exactly as {@code LodView.updateScreen} computes it, with the field of view the adaptive curve
   * gives at that distance.
   */
  static double projectedRadiusPx(double radiusUnits, double distanceUnits) {
    float fov = adaptiveFovRad((float) distanceUnits);
    return radiusUnits / distanceUnits * (SCREEN_HEIGHT_PX * 0.5) / Math.tan(fov / 2.0);
  }
}
