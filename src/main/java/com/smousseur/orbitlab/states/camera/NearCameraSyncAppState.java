package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.view.RenderContext;

/**
 * Synchronizes the near viewport camera with the main (far) camera every frame.
 *
 * <p>The far camera works in solar-scale units (1 unit = 1e9 m) while the near camera works in
 * km-scale units (1 unit = 1e3 m). This state converts the far camera's position into km-scale
 * coordinates and copies the rotation and frustum planes so both viewports stay visually aligned.
 */
public final class NearCameraSyncAppState extends BaseAppState {

  /**
   * Conversion factor: 1 solar unit = 1e9 m, 1 km unit = 1e3 m → ratio = 1e6. Multiply far camera
   * position by this to get near camera position in km units.
   */
  private static final float SOLAR_TO_KM = (float) RenderContext.ratioSolarToPlanetPerUnit();

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
    // Convert position from solar-scale (1 unit = 1e9 m) to km-scale (1 unit = 1e3 m)
    Vector3f farPos = farCam.getLocation();
    nearCam.setLocation(farPos.mult(SOLAR_TO_KM));

    nearCam.setRotation(farCam.getRotation());

    // Mirror the full frustum (including adaptive FoV) so both viewports match visually.
    nearCam.setFrustumNear(farCam.getFrustumNear() * SOLAR_TO_KM);
    nearCam.setFrustumFar(farCam.getFrustumFar() * SOLAR_TO_KM);
    nearCam.setFrustumLeft(farCam.getFrustumLeft() * SOLAR_TO_KM);
    nearCam.setFrustumRight(farCam.getFrustumRight() * SOLAR_TO_KM);
    nearCam.setFrustumTop(farCam.getFrustumTop() * SOLAR_TO_KM);
    nearCam.setFrustumBottom(farCam.getFrustumBottom() * SOLAR_TO_KM);
  }

  @Override
  protected void cleanup(Application app) {}

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
