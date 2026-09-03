package com.smousseur.orbitlab.states.camera;

import com.jme3.math.FastMath;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.engine.OrbitCameraConfig;

/**
 * The far camera's frustum for one camera distance: the three values that have to be written
 * together.
 *
 * <h2>Why they are one object rather than three setters ({@code BUG-2})</h2>
 *
 * <p>JME expresses {@code frustumTop} <em>at</em> the near plane, so the field of view a camera
 * actually shows is {@code 2·atan(top/near)} — a property of the couple, not of either half. {@code
 * setFrustumNear} therefore changes the field of view by the ratio of the two near planes, silently
 * and without touching anything named "fov".
 *
 * <p>The camera did exactly that on a wheel event: the wheel branch wrote {@code near} through the
 * frustum update and returned, and the field of view was only put back in agreement by the next
 * {@code update()}. Between the two, {@code SkyboxAppState} — attached first, and recomputing its
 * own field of view from that couple every frame — rendered the sky at the wrong scale for one
 * frame. Measured: <b>5.52° too wide</b> for a single wheel tick in solar view, <b>41.8 px</b> of
 * jump for a star at the edge of a 720p screen, and up to 31° when five ticks land in the same
 * frame. In planet view the near plane sits on a clamp and does not move, which is why the defect
 * was only ever seen from the solar view.
 *
 * <p>The invariant this record exists to pose: <b>the far camera is never observable with a {@code
 * near} that has lost its {@code top}.</b> Every write goes through {@link #applyTo}, which ends on
 * a single {@code setFrustumPerspective}; no path writes one half without the other. That is what
 * keeps the couple's other readers — {@code NearCameraSyncAppState} and {@code
 * PlanetHudMarkersAppState} both derive from it — out of the same trap, whatever the attach order
 * happens to be.
 *
 * @param near the near clip plane, in solar units
 * @param far the far clip plane, in solar units
 * @param fovDegrees the vertical field of view the adaptive curve gives at this distance
 */
record FarFrustum(float near, float far, float fovDegrees) {

  /** Adaptive field of view: narrow when close, wide when far. */
  private static final float FOV_MIN_RAD = (float) Math.toRadians(15.0);

  private static final float FOV_MAX_RAD = (float) Math.toRadians(60.0);

  /** Curve exponent, 0.6 to 1.5: below 1 narrows more at close range. */
  private static final float FOV_CURVE_K = 0.9f;

  /**
   * The frustum to show at the given camera distance.
   *
   * @param config the camera tuning, giving the plane factors and the distance bounds
   * @param distance the camera's distance to its pivot, in solar units
   * @param farFloor the dynamic minimum far plane, raised in planet view so that distant orbits
   *     stay drawn
   * @return the three values, consistent with each other by construction
   */
  static FarFrustum of(OrbitCameraConfig config, float distance, float farFloor) {
    float near = clampFinite(distance * config.nearFactor(), config.nearMin(), config.nearMax());

    // Far scales with distance rather than sitting on a large minimum: forced high when the camera
    // is close, it collapses depth precision into z-fighting.
    float far = clampFinite(distance * config.farFactor(), 0.001f, config.farMax());
    far = Math.max(far, near * 10f);
    far = Math.max(far, 10f);

    // The dynamic floor is what keeps distant orbits drawn in planet view.
    far = Math.max(far, farFloor);

    // Keep the pivot visible: near must stay under the camera distance, minus a margin.
    float margin = Math.max(near * 2f, 0.01f);
    float maxNear = Math.max(0.0001f, distance - margin);
    if (near > maxNear) {
      near = maxNear;
      far = Math.max(far, near * 10f);
    }

    return new FarFrustum(near, far, (float) Math.toDegrees(adaptiveFovRad(config, distance)));
  }

  /**
   * Writes all three values onto the camera in one call.
   *
   * @param cam the far camera
   */
  void applyTo(Camera cam) {
    float aspect = (float) cam.getWidth() / Math.max(1f, cam.getHeight());
    cam.setFrustumPerspective(fovDegrees, aspect, near, far);
  }

  private static float adaptiveFovRad(OrbitCameraConfig config, float distance) {
    float tt = (float) Math.pow(normalizedZoom01(config, distance), FOV_CURVE_K);
    return FastMath.interpolateLinear(tt, FOV_MIN_RAD, FOV_MAX_RAD);
  }

  /**
   * The zoom as a value in {@code [0, 1]}, 0 being fully zoomed in. Logarithmic, so that the same
   * wheel tick feels the same at every scale — which is also what makes the field of view depend on
   * the absolute distance rather than on the focused body's size.
   */
  private static float normalizedZoom01(OrbitCameraConfig config, float distance) {
    float d = FastMath.clamp(distance, config.minDistance(), config.maxDistance());
    float logMin = (float) Math.log(config.minDistance());
    float logMax = (float) Math.log(config.maxDistance());
    float logD = (float) Math.log(d);
    return FastMath.clamp((logD - logMin) / (logMax - logMin), 0f, 1f);
  }

  private static float clampFinite(float v, float min, float max) {
    if (!Float.isFinite(v)) {
      return min;
    }
    return FastMath.clamp(v, min, max);
  }
}
