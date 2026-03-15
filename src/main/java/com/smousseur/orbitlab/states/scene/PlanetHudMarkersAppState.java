package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;

import java.util.Map;

/**
 * Application state that updates HUD markers (screen-space labels and indicators) for all
 * planets each frame.
 *
 * <p>Projects the 3D world positions of planet presenters onto the 2D screen using the
 * application camera, keeping on-screen markers aligned with their corresponding celestial
 * bodies as the camera moves.
 */
public class PlanetHudMarkersAppState extends BaseAppState {
  private Camera camera;
  private final Map<SolarSystemBody, PlanetPresenter> planets;

  /**
   * Creates a new planet HUD markers state.
   *
   * @param context the application context providing the planet presenter registry
   */
  public PlanetHudMarkersAppState(ApplicationContext context) {
    planets = context.getPlanets();
  }

  @Override
  protected void initialize(Application app) {
    camera = app.getCamera();
  }

  @Override
  public void update(float tpf) {
    planets.values().stream().map(PlanetPresenter::view).forEach(view -> view.updateScreen(camera));
  }

  @Override
  protected void cleanup(Application app) {}

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
