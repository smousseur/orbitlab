package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import java.util.Objects;

/**
 * Application state that implements a floating-origin technique to prevent floating-point precision
 * issues when rendering objects at large distances from the world origin.
 *
 * <p>Each frame, this state translates the solar system root node so that the currently focused
 * body sits at the world origin. In solar view mode, the root stays at the origin; in planet view
 * mode, the focused planet's position is negated and applied to the root, effectively centering the
 * scene on that planet.
 */
public class FloatingOriginAppState extends BaseAppState {
  /** Minimum far plane for the farCam in planet view mode, in solar-scale units. */
  private static final float PLANET_MODE_FAR_MIN = 50_000f;

  private final ApplicationContext context;
  private final Node solarRoot;
  private final SceneGraph sceneGraph;
  private OrbitCameraAppState orbitCam;

  /**
   * Creates a new floating origin state.
   *
   * @param context the application context providing scene graph and focus view information
   */
  public FloatingOriginAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
    sceneGraph = context.sceneGraph();
    solarRoot = sceneGraph.getFarRoot();
  }

  @Override
  protected void initialize(Application app) {
    // TODO Get camera from context
    orbitCam = getState(OrbitCameraAppState.class);
  }

  @Override
  public void update(float tpf) {
    FocusView view = context.focusView();
    switch (view.getMode()) {
      case SOLAR -> {
        sceneGraph.showBodySpatial(SolarSystemBody.SUN);
        solarRoot.setLocalTranslation(0, 0, 0);
        orbitCam.setFarFloor(0f);
      }
      case PLANET -> {
        sceneGraph.showBodySpatial(view.getBody());
        Spatial planetSpatial = sceneGraph.getBodySpatial(view.getBody());
        if (planetSpatial != null) {
          solarRoot.setLocalTranslation(planetSpatial.getLocalTranslation().negate());
        }
        // Ensure the far frustum is large enough to encompass distant orbits and bodies.
        orbitCam.setFarFloor(PLANET_MODE_FAR_MIN);
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
