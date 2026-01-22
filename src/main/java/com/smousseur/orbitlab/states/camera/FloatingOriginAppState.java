package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.SimulationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;

import java.util.Objects;

public class FloatingOriginAppState extends BaseAppState {

  private final SimulationContext context;
  private final Node solarRoot;
  private final SceneGraph sceneGraph;

  public FloatingOriginAppState(SimulationContext context) {
    this.context = Objects.requireNonNull(context, "context");
    sceneGraph = context.sceneGraph();
    solarRoot = sceneGraph.getSolarRoot();
  }

  @Override
  protected void initialize(Application app) {}

  @Override
  public void update(float tpf) {
    FocusView view = context.focusView();
    switch (view.getMode()) {
      case SOLAR -> solarRoot.setLocalTranslation(0, 0, 0);
      case PLANET -> {
        Spatial planetSpatial = sceneGraph.getPlanetSpatial(view.getBody());
        solarRoot.setLocalTranslation(planetSpatial.getLocalTranslation().negate());
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
