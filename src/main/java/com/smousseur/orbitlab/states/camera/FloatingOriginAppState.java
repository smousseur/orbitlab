package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.states.mission.MissionRenderer;
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
        sceneGraph.setSolarVisible(true);
        sceneGraph.nearFrame().setLocalTranslation(0f, 0f, 0f);
        sceneGraph.showBodySpatial(SolarSystemBody.SUN);
        solarRoot.setLocalTranslation(0, 0, 0);
        orbitCam.setFarFloor(0f);
      }
      case PLANET -> {
        sceneGraph.setSolarVisible(true);
        sceneGraph.nearFrame().setLocalTranslation(0f, 0f, 0f);
        sceneGraph.showBodySpatial(view.getBody());
        Spatial planetSpatial = sceneGraph.getBodySpatial(view.getBody());
        if (planetSpatial != null) {
          solarRoot.setLocalTranslation(planetSpatial.getLocalTranslation().negate());
        }
        // Ensure the far frustum is large enough to encompass distant orbits and bodies.
        orbitCam.setFarFloor(PLANET_MODE_FAR_MIN);
      }
      case SPACECRAFT -> {
        // Keep the far scene visible so that orbit lines of other planets remain drawn.
        // The 2D HUD icons are handled independently by PlanetHudMarkersAppState.
        sceneGraph.setSolarVisible(true);
        sceneGraph.showBodySpatial(view.getBody());

        // Keep the far root centered on the parent body — the projection camera used by
        // PlanetHudMarkersAppState is a clone of the far camera and needs the parent at origin
        // to place the other planet icons correctly around it.
        Spatial planetSpatial = sceneGraph.getBodySpatial(view.getBody());
        if (planetSpatial != null) {
          solarRoot.setLocalTranslation(planetSpatial.getLocalTranslation().negate());
        }
        orbitCam.setFarFloor(PLANET_MODE_FAR_MIN);

        // Offset the near frame so the spacecraft sits at the near-view origin. The trajectory
        // trail lives under nearOrbitsNode (child of nearFrame) and inherits the translation.
        MissionRenderer mr = context.getMissionRenderer(view.getFocusedMission());
        if (mr != null && mr.getAnchorSpatial() != null) {
          Vector3f nearPos = mr.getAnchorSpatial().getLocalTranslation();
          sceneGraph.nearFrame().setLocalTranslation(nearPos.negate());
        } else {
          sceneGraph.nearFrame().setLocalTranslation(0f, 0f, 0f);
        }
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
