package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import com.smousseur.orbitlab.engine.scene.spacecraft.SpacecraftPresenter;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

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

  /**
   * Creates a new mission renderer.
   *
   * @param entry the mission entry to render
   * @param context the application context
   * @param renderContext the render context for coordinate scaling
   * @param trajectoryColor the color for the trajectory line
   */
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

    Node nearBodiesNode = context.sceneGraph().nearBodiesNode();
    Spatial anchor = view.spatial();
    nearBodiesNode.attachChild(anchor);

    // Load 3D model asynchronously
    ExecutorService assetExecutor = AssetFactory.get().assetLoadingExecutor();
    Model3dView model3dView = view.getModel3dView();
    CompletableFuture.supplyAsync(model3dView::loadModel, assetExecutor)
        .thenAccept(model3dView::onModelLoaded);

    // Initialize trajectory renderer
    trajectoryRenderer =
        new MissionTrajectoryRenderer(mission.getName(), renderContext, trajectoryColor);
    trajectoryRenderer.initialize(context.sceneGraph().nearOrbitsNode());
  }

  /**
   * Updates the spacecraft display and trajectory for the current frame.
   *
   * @param tpf time per frame
   * @param cam the active camera
   */
  public void update(float tpf, Camera cam) {
    Mission mission = entry.mission();

    if (!mission.isOnGoing()) {
      return;
    }

    boolean isPlanetMode = context.focusView().getMode() == ViewMode.PLANET;
    if (!isPlanetMode) {
      view.setVisible(false);
      return;
    }
    view.setVisible(true);

    SimulationClock clock = context.clock();
    AbsoluteDate now = clock.now();
    mission.update(now);

    SpacecraftState state = mission.getCurrentState();
    if (state == null) {
      return;
    }

    Vector3D posGcrf = state.getPosition();
    presenter.updatePose(posGcrf, renderContext);
    view.updateScreen(cam);

    trajectoryRenderer.addPosition(posGcrf);
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
