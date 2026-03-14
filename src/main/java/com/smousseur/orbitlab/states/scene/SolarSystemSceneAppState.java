package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import java.util.Objects;

/**
 * Application state that manages the top-level visibility of the solar system scene,
 * including planet nodes and orbit line overlays.
 *
 * <p>Controls whether the solar system scene graph is visible and provides a method
 * to toggle orbit path visibility independently. When enabled, the solar system is shown;
 * when disabled, it is hidden. On cleanup, the scene is fully detached from the parent node.
 */
public final class SolarSystemSceneAppState extends BaseAppState {

  private final SceneGraph scene;

  private boolean orbitsVisible = true;

  /**
   * Creates a new solar system scene state.
   *
   * @param context the application context providing the scene graph
   */
  public SolarSystemSceneAppState(ApplicationContext context) {
    this.scene = Objects.requireNonNull((context).sceneGraph(), "orbitBus");
  }

  /**
   * Sets the visibility of orbital path lines in the scene.
   *
   * @param visible {@code true} to show orbit lines, {@code false} to hide them
   */
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
