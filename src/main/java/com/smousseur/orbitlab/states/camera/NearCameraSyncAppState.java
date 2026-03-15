package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.renderer.Camera;

/**
 * Synchronizes the near viewport camera with the main (far) camera every frame.
 *
 * <p>The near viewport uses a separate {@link Camera} instance cloned at startup. Without
 * synchronization, this camera would remain static. This state copies the far camera's position,
 * rotation, and frustum near plane each frame so the near scene (planet-scale km coordinate
 * space) tracks the user's viewpoint correctly.
 *
 * <p>The frustum far plane is not synced; the near camera keeps a fixed far value to cover the
 * full planet-scale scene without wasting depth-buffer precision on solar-system distances.
 */
public final class NearCameraSyncAppState extends BaseAppState {

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
    nearCam.setLocation(farCam.getLocation());
    nearCam.setRotation(farCam.getRotation());
    // Mirror the dynamically adjusted near frustum so planet-scale geometry is not clipped.
    nearCam.setFrustumNear(farCam.getFrustumNear());
  }

  @Override
  protected void cleanup(Application app) {}

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
