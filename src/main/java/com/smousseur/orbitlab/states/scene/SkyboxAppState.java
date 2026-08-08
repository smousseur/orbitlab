package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.AssetFactory;
import java.util.Objects;

/**
 * Application state that renders the starfield skybox in its own pre-view.
 *
 * <p>JME's sky shader ({@code Common/MatDefs/Misc/Sky.vert}) zeroes the vertex {@code w} before
 * applying the view matrix, so the sky is already immune to camera <em>translation</em> — the zoom
 * dolly and the floating-origin offset have no effect on it. What it is <em>not</em> immune to is
 * the projection: after the rotation the shader restores {@code w = 1}, which leaves the sky mesh
 * sitting at its raw model radius (10 world units) in view space. The far camera's near plane is
 * derived from the zoom distance and climbs up to 10 000 units, so a skybox rendered by the far
 * viewport is clipped away as soon as the camera pulls back.
 *
 * <p>Hence the dedicated {@code SkyView} pre-view: its camera keeps a fixed depth range that always
 * brackets the sky radius, and this state only mirrors the far camera's orientation and adaptive
 * field of view so the sky stays angularly locked to the scene.
 */
public class SkyboxAppState extends BaseAppState {
  /** Depth range of the sky camera, chosen to bracket the sky mesh radius (10 world units). */
  private static final float SKY_NEAR = 1f;

  private static final float SKY_FAR = 100f;

  private final ApplicationContext context;
  private final Node skyRoot;

  private Camera skyCam;
  private Camera farCam;
  private Spatial skybox;

  public SkyboxAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
    this.skyRoot = context.sceneGraph().getSkyRoot();
  }

  @Override
  protected void initialize(Application app) {
    farCam = app.getCamera();
    skyCam = context.skyCamera();
    // The sky shader ignores the camera translation; keeping it at the origin avoids any doubt.
    skyCam.setLocation(Vector3f.ZERO);

    skybox =
        AssetFactory.get()
            .createSkybox(
                "skybox/left.png",
                "skybox/right.png",
                "skybox/front.png",
                "skybox/back.png",
                "skybox/top.png",
                "skybox/bottom.png");
    skyRoot.attachChild(skybox);
  }

  @Override
  public void update(float tpf) {
    if (skyCam == null || farCam == null) {
      return;
    }
    skyCam.setRotation(farCam.getRotation());

    // Mirror the far camera's adaptive vertical FoV while keeping our own depth range, so the
    // apparent size of the stars follows the scene as the camera narrows or widens its field.
    float halfFovY = FastMath.atan2(farCam.getFrustumTop(), farCam.getFrustumNear());
    float fovYDeg = halfFovY * 2f * FastMath.RAD_TO_DEG;
    float aspect = (float) skyCam.getWidth() / Math.max(1, skyCam.getHeight());
    skyCam.setFrustumPerspective(fovYDeg, aspect, SKY_NEAR, SKY_FAR);
  }

  @Override
  protected void cleanup(Application app) {
    // The SkyView viewport itself is left in place: it owns the color clear for the whole frame
    // (the far viewport no longer clears it), so removing it would leave an undefined background.
    if (skybox != null) {
      skybox.removeFromParent();
      skybox = null;
    }
    skyCam = null;
    farCam = null;
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
