package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;

import java.util.Map;

public class PlanetHudMarkersAppState extends BaseAppState {
  private Camera camera;
  private Map<SolarSystemBody, PlanetPresenter> planets;

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
