package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import java.util.Map;

/**
 * Application state that updates HUD markers (screen-space labels and indicators) for all planets
 * each frame.
 *
 * <p>Projects the 3D world positions of planet presenters onto the 2D screen using the application
 * camera, keeping on-screen markers aligned with their corresponding celestial bodies as the camera
 * moves.
 *
 * <p>When the main camera's frustum far plane is too small (e.g. in planet view), a temporary
 * projection camera with an extended far plane is used so distant planets still project to valid
 * screen coordinates.
 */
public class PlanetHudMarkersAppState extends BaseAppState {
  private final FocusView focusView;
  private Camera camera;

  /** Dedicated camera clone used only for screen-space projection of distant icons. */
  private Camera projectionCam;

  private final Map<SolarSystemBody, PlanetPresenter> planets;

  /**
   * Creates a new planet HUD markers state.
   *
   * @param context the application context providing the planet presenter registry
   */
  public PlanetHudMarkersAppState(ApplicationContext context) {
    focusView = context.focusView();
    planets = context.getPlanets();
  }

  @Override
  protected void initialize(Application app) {
    camera = app.getCamera();
    projectionCam = camera.clone();
  }

  @Override
  public void update(float tpf) {
    // Sync projection camera with the real camera
    projectionCam.setLocation(camera.getLocation());
    projectionCam.setRotation(camera.getRotation());
    projectionCam.setFrustumNear(camera.getFrustumNear());
    projectionCam.setFrustumLeft(camera.getFrustumLeft());
    projectionCam.setFrustumRight(camera.getFrustumRight());
    projectionCam.setFrustumTop(camera.getFrustumTop());
    projectionCam.setFrustumBottom(camera.getFrustumBottom());

    // Use a far plane large enough to encompass the whole solar system for projection
    projectionCam.setFrustumFar(Math.max(camera.getFrustumFar(), 50_000f));

    for (PlanetPresenter presenter : planets.values()) {
      SolarSystemBody body = presenter.body();
      BodyView view = presenter.view();
      if (!body.isSatellite() || focusView.isSatelliteVisible(body)) {
        view.updateScreen(projectionCam);
      }
    }
  }

  @Override
  protected void cleanup(Application app) {}

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
