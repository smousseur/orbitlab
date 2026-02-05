package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import java.util.Objects;

public final class SolarSystemSceneAppState extends BaseAppState {

  private final SceneGraph scene;

  private boolean orbitsVisible = true;

  public SolarSystemSceneAppState(ApplicationContext context) {
    this.scene = Objects.requireNonNull((context).sceneGraph(), "orbitBus");
  }

  public void setOrbitsVisible(boolean visible) {
    this.orbitsVisible = visible;
    scene.orbits().setVisible(visible);
  }

  @Override
  protected void initialize(Application app) {
    scene.setSolarVisible(true);
    scene.orbits().setVisible(orbitsVisible);
  }

  @Override
  protected void cleanup(Application app) {
    scene.detachFromParent();
  }

  @Override
  protected void onEnable() {
    scene.setSolarVisible(true);
  }

  @Override
  protected void onDisable() {
    scene.setSolarVisible(false);
  }
}
