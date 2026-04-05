package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import com.smousseur.orbitlab.engine.scene.spacecraft.SpacecraftPresenter;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Encapsulates all rendering for a single mission: spacecraft display (SpacecraftPresenter +
 * LodView) and trajectory line (delegated to {@link MissionTrajectoryRenderer}). This is NOT an
 * AppState — it is a plain object managed by {@link MissionOrchestratorAppState}.
 */
public final class MissionRenderer {

  private static final double SPACECRAFT_RADIUS_METERS = 50.0;
  private static final double SPACECRAFT_LOD_MULTIPLIER = 500.0;
  private static final String SPACECRAFT_MODEL_PATH =
      "models/vehicles/heavy_falcon/heavy_falcon.gltf";

  private final MissionEntry entry;
  private final ApplicationContext context;
  private final RenderContext renderContext;
  private final ColorRGBA trajectoryColor;

  private SpacecraftPresenter presenter;
  private LodView view;
  private MissionTrajectoryRenderer trajectoryRenderer;

  public MissionRenderer(
      MissionEntry entry,
      ApplicationContext context,
      RenderContext renderContext,
      ColorRGBA trajectoryColor) {
    this.entry = Objects.requireNonNull(entry, "entry");
    this.context = Objects.requireNonNull(context, "context");
    this.renderContext = Objects.requireNonNull(renderContext, "renderContext");
    this.trajectoryColor = Objects.requireNonNull(trajectoryColor, "trajectoryColor");
  }

  /**
   * Initializes the spacecraft view and trajectory geometry.
   *
   * @param app the JME application
   */
  public void initialize(Application app) {
    Mission mission = entry.mission();
    Node guiNode = context.guiGraph().getPlanetBillboardsNode();

    BodyRenderConfig config =
        new BodyRenderConfig(
            "mission-" + mission.getName(),
            mission.getName(),
            trajectoryColor,
            SPACECRAFT_RADIUS_METERS,
            SPACECRAFT_LOD_MULTIPLIER,
            SPACECRAFT_MODEL_PATH,
            renderContext);

    view = new LodView(guiNode, config, null, null);
    presenter = new SpacecraftPresenter(config.id(), view);
    presenter.setVisible(true);

    Node anchor = (Node) view.spatial();
    anchor.attachChild(view.nearSpatial());
    context.sceneGraph().nearBodiesNode().attachChild(anchor);

    ExecutorService assetExecutor = AssetFactory.get().assetLoadingExecutor();
    Model3dView model3dView = view.getModel3dView();
    CompletableFuture.supplyAsync(model3dView::loadModel, assetExecutor)
        .thenAccept(model3dView::onModelLoaded);

    trajectoryRenderer =
        new MissionTrajectoryRenderer(mission.getName(), renderContext, trajectoryColor);
    trajectoryRenderer.initialize(context.sceneGraph().nearOrbitsNode());
  }

  /**
   * Shows/hides all visual elements (spacecraft + trajectory).
   *
   * @param visible whether to show or hide
   */
  public void setVisible(boolean visible) {
    if (view != null) {
      view.setVisible(visible);
    }
    if (trajectoryRenderer != null) {
      trajectoryRenderer.setVisible(visible);
    }
  }

  /**
   * Updates display from a pre-computed ephemeris point. No propagation — pure rendering from
   * pre-calculated data.
   *
   * @param point the interpolated ephemeris point
   * @param trailPositions the positions for the trajectory trail
   * @param cam the active camera
   */
  public void updateFromEphemeris(
      MissionEphemerisPoint point, List<Vector3D> trailPositions, Camera cam) {
    boolean isPlanetMode = context.focusView().getMode() == ViewMode.PLANET;
    if (!isPlanetMode) {
      if (view != null) view.setVisible(false);
      return;
    }

    presenter.updatePose(point.position(), renderContext);
    view.updateScreen(cam);
    trajectoryRenderer.setPositions(trailPositions);
    trajectoryRenderer.update();
  }

  /** Detaches all visual elements from the scene. */
  public void cleanup() {
    if (view != null) {
      view.spatial().removeFromParent();
      view.detach();
    }
    if (trajectoryRenderer != null) {
      trajectoryRenderer.cleanup();
    }
  }
}
