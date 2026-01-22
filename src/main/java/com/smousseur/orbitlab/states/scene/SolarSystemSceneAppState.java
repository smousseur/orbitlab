package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.SimulationContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.events.OrbitEventBus;
import com.smousseur.orbitlab.engine.scene.OrbitColors;
import com.smousseur.orbitlab.engine.scene.OrbitLineFactory;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.simulation.orbit.OrbitPath;

import java.util.Objects;

public final class SolarSystemSceneAppState extends BaseAppState {

  private final AssetManager assetManager;
  private final OrbitEventBus orbitBus;

  private final SceneGraph scene;

  private boolean orbitsVisible = true;

  public SolarSystemSceneAppState(SimulationContext context, AssetManager assetManager) {
    this.assetManager = Objects.requireNonNull(assetManager, "assetManager");
    this.orbitBus = Objects.requireNonNull((context).orbitBus(), "orbitBus");
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
  public void update(float tpf) {
    // Drain bus on JME thread => safe to create/attach geometries here.
    OrbitEventBus.OrbitPathReady evt;
    while ((evt = orbitBus.pollOrbitPathReady()) != null) {
      onOrbitPathReady(evt.body(), evt.path());
    }
  }

  private void onOrbitPathReady(SolarSystemBody body, OrbitPath path) {
    Geometry geom =
        OrbitLineFactory.buildHeliocentricLineStrip(
            assetManager, path, OrbitColors.colorFor(body), 2.0f);

    Node bucket = scene.orbits().orbitNode(body);
    bucket.detachAllChildren();
    bucket.attachChild(geom);
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
