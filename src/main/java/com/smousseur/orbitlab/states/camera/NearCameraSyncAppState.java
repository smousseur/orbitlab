package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.view.RenderContext;

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
 * the near viewport owns a fixed depth range sized for planet/spacecraft scale content.
 */
public final class NearCameraSyncAppState extends BaseAppState {

  /**
   * Conversion factor: 1 solar unit = 1e9 m, 1 km unit = 1e3 m → ratio = 1e6. Multiply far camera
   * position by this to get near camera position in km units.
   */
  private static final float SOLAR_TO_KM = (float) RenderContext.ratioSolarToPlanetPerUnit();

  private static final float NEAR_MIN = 0.01f;
  private static final float NEAR_MAX = 500f;

  private static final float FAR_MIN = 100_000f;
  private static final float FAR_MAX = 100_000_000f;

  private final Camera nearCam;
  private Camera farCam;

  /**
   * Creates a sync state for the given near viewport camera.
   *
   * @param nearCam the near viewport camera to keep in sync with the main camera
   */
  public NearCameraSyncAppState(Camera nearCam) {
    this.nearCam = nearCam;
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
    float near = FastMath.clamp(distToOrigin * 0.0005f, NEAR_MIN, NEAR_MAX);
    float far = FastMath.clamp(distToOrigin * 10f, FAR_MIN, FAR_MAX);
    nearCam.setFrustumPerspective(fovYDeg, aspect, near, far);
  }

  @Override
  protected void cleanup(Application app) {}

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
