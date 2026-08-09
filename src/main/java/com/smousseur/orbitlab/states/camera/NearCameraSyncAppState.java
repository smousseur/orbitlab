package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.ViewMode;
import java.util.Objects;

/**
 * Synchronizes the near viewport camera with the main (far) camera every frame.
 *
 * <p>The far camera works in solar-scale units (1 unit = 1e9 m) while the near camera works in
 * km-scale units (1 unit = 1e3 m). This state copies the far camera's position (scaled by {@link
 * #SOLAR_TO_KM}) and rotation so both viewports share the same viewpoint, and derives the
 * field-of-view angle from the far camera so they stay angularly aligned.
 *
 * <p>The near camera's near/far clip planes are <em>not</em> scaled from the far camera: doing so
 * would multiply the far cam's ~100 km minimum near plane by 1e6, producing an unusable near
 * frustum when the far camera is close to its pivot (e.g. when focused on a spacecraft). Instead,
 * the near viewport owns its own depth range, sized for planet/spacecraft scale content from the
 * camera's distance to the near-view origin — see {@link #nearPlane}, which is where the viewport's
 * depth precision is actually decided.
 */
public final class NearCameraSyncAppState extends BaseAppState {

  /**
   * Conversion factor: 1 solar unit = 1e9 m, 1 km unit = 1e3 m → ratio = 1e6. Multiply far camera
   * position by this to get near camera position in km units.
   */
  private static final float SOLAR_TO_KM = (float) RenderContext.ratioSolarToPlanetPerUnit();

  /** Safety floor for the near plane, 5 m: only ever reached if the camera lands on the origin. */
  private static final float NEAR_MIN = 0.005f;

  private static final float NEAR_MAX = 500f;

  static final float FAR_MIN = 100_000f;
  private static final float FAR_MAX = 100_000_000f;

  /**
   * Near-plane factor in spacecraft view. The depth resolution of the near viewport is {@code Δz =
   * 2⁻²⁴ · z² · (1/near − 1/far)}, so with {@code far ≫ near} the near plane alone decides it — the
   * far plane contributes nothing measurable, which is why it is left alone here (spec {@code
   * specs/graphics-effects/spacecraft-view-artefacts.md} §5.3).
   *
   * <p>At the 500 m focus distance the old factor of {@code 5e-4} pinned the near plane to its 10 m
   * floor, giving ~274 km per depth step at the Earth's distance: a 400 km LEO trajectory sat about
   * one and a half steps above the surface and won or lost the depth test per pixel and per frame —
   * the line scintillating over the Earth's disc. At {@code 0.2} the near plane is 100 m, one step
   * is ~27 km, and the orbit clears the surface by some fifteen of them.
   *
   * <p>Nothing is given up for it: in spacecraft view the closest content <em>is</em> the focused
   * spacecraft, sitting on the near origin, so {@code distToOrigin} is the distance to it. A factor
   * of 0.2 keeps the whole ~100 m model in front of the plane.
   */
  private static final float SPACECRAFT_NEAR_FACTOR = 0.2f;

  /**
   * Ceiling on what {@link #SPACECRAFT_NEAR_FACTOR} may produce, in km units.
   *
   * <p>"The closest content is the spacecraft at the origin" is only true while the camera stays
   * near it. Pull the camera {@code d} km towards the nadir from a spacecraft 400 km up and the
   * ground beneath it is {@code 400 − d} km away — shrinking as {@code 0.2·d} grows, the two
   * crossing at ~334 km. Past that the near plane would cut a hole in the planet under the camera:
   * one artefact traded for a worse one.
   *
   * <p>1 km pushes that crossing out to the last kilometre before the surface, and costs nothing
   * where it matters: the cap is inactive below 5 km of zoom, so the whole spacecraft view keeps the
   * 100 m plane, and even when it does engage the depth step at the Earth's distance is ~2.7 km
   * against the 274 km this fix set out to remove.
   */
  private static final float SPACECRAFT_NEAR_MAX = 1f;

  /**
   * Near-plane factor everywhere else. Deliberately far more conservative than {@link
   * #SPACECRAFT_NEAR_FACTOR}, because the assumption that licenses that one does not hold here: in
   * planet view the origin is the body's <em>centre</em> and the closest content is its surface,
   * a planetary radius nearer. The two factors merge the day the near plane is driven by the actual
   * content of the viewport (spec §9.3, "limite connue").
   */
  private static final float DEFAULT_NEAR_FACTOR = 0.0005f;

  private final ApplicationContext context;
  private final Camera nearCam;
  private Camera farCam;

  /**
   * Creates a sync state for the given near viewport camera.
   *
   * @param context the application context, read for the active view mode
   * @param nearCam the near viewport camera to keep in sync with the main camera
   */
  public NearCameraSyncAppState(ApplicationContext context, Camera nearCam) {
    this.context = Objects.requireNonNull(context, "context");
    this.nearCam = Objects.requireNonNull(nearCam, "nearCam");
  }

  @Override
  protected void initialize(Application app) {
    farCam = app.getCamera();
  }

  @Override
  public void update(float tpf) {
    // Convert position from solar-scale (1 unit = 1e9 m) to km-scale (1 unit = 1e3 m).
    Vector3f farPos = farCam.getLocation();
    nearCam.setLocation(farPos.mult(SOLAR_TO_KM));
    nearCam.setRotation(farCam.getRotation());

    // Derive the vertical FoV angle from the far cam's current (possibly adaptive) frustum
    // and apply it to the near cam with our own fixed near/far planes. This keeps the two
    // viewports angularly locked while decoupling their depth ranges.
    float farNear = farCam.getFrustumNear();
    float farTop = farCam.getFrustumTop();
    float halfFovY = FastMath.atan2(farTop, farNear);
    float fovYDeg = halfFovY * 2f * FastMath.RAD_TO_DEG;
    float aspect = (float) nearCam.getWidth() / Math.max(1, nearCam.getHeight());

    float distToOrigin = nearCam.getLocation().length();
    float near = nearPlane(context.focusView().getMode(), distToOrigin);
    float far = FastMath.clamp(distToOrigin * 10f, FAR_MIN, FAR_MAX);
    nearCam.setFrustumPerspective(fovYDeg, aspect, near, far);
  }

  /**
   * The near clip plane to use, given how far the camera sits from the near-view origin. Package
   * private so the depth budget it buys can be pinned by a test without a GL context.
   *
   * @param mode the active view mode, which decides what the origin means and therefore how close
   *     the plane may safely be pulled
   * @param distToOrigin the camera's distance to the near-view origin, in km units
   * @return the near plane distance, in km units
   */
  static float nearPlane(ViewMode mode, float distToOrigin) {
    if (mode == ViewMode.SPACECRAFT) {
      return FastMath.clamp(distToOrigin * SPACECRAFT_NEAR_FACTOR, NEAR_MIN, SPACECRAFT_NEAR_MAX);
    }
    return FastMath.clamp(distToOrigin * DEFAULT_NEAR_FACTOR, NEAR_MIN, NEAR_MAX);
  }

  @Override
  protected void cleanup(Application app) {}

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
