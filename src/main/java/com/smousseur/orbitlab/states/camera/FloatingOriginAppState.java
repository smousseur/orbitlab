package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;

import java.util.Objects;

public class FloatingOriginAppState extends BaseAppState {

  private final ApplicationContext context;
  private final Node solarRoot;
  private final SceneGraph sceneGraph;

  public FloatingOriginAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
    sceneGraph = context.sceneGraph();
    solarRoot = sceneGraph.getFarRoot();
  }

  @Override
  protected void initialize(Application app) {}

  @Override
  public void update(float tpf) {
    FocusView view = context.focusView();
    switch (view.getMode()) {
      case SOLAR -> solarRoot.setLocalTranslation(0, 0, 0);
      case PLANET -> {
        Spatial planetSpatial = sceneGraph.getBodySpatial(view.getBody());
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
