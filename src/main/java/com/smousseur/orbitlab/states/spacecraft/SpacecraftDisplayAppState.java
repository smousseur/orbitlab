package com.smousseur.orbitlab.states.spacecraft;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * Application state that displays a spacecraft's position from a mission in the near viewport.
 * Visible only in PLANET view mode.
 *
 * <p>Each frame, propagates the mission to the current simulation time, reads the spacecraft
 * position in GCRF, and updates the spacecraft's visual representation via the shared {@link
 * LodView} and {@link SpacecraftPresenter}.
 */
public final class SpacecraftDisplayAppState extends BaseAppState {

  private static final double SPACECRAFT_RADIUS_METERS = 50.0;
  private static final double SPACECRAFT_LOD_MULTIPLIER = 500.0;
  private static final String SPACECRAFT_MODEL_PATH = "models/spacecraft/spacecraft.gltf";

  private final ApplicationContext context;
  private final SimulationClock clock;
  private final Mission mission;
  private final RenderContext renderContext;

  private SpacecraftPresenter presenter;
  private LodView view;
  private SpacecraftTrajectoryAppState trajectoryState;

  /**
   * Creates a new spacecraft display state.
   *
   * @param context the application context
   * @param mission the mission whose spacecraft state to visualize
   * @param renderContext the render context for coordinate scaling (typically Planet)
   */
  public SpacecraftDisplayAppState(
      ApplicationContext context, Mission mission, RenderContext renderContext) {
    this.context = Objects.requireNonNull(context, "context");
    this.clock = Objects.requireNonNull(context.clock(), "clock");
    this.mission = Objects.requireNonNull(mission, "mission");
    this.renderContext = Objects.requireNonNull(renderContext, "renderContext");
  }

  @Override
  protected void initialize(Application app) {
    Node guiNode = context.guiGraph().getPlanetBillboardsNode();

    BodyRenderConfig config =
        new BodyRenderConfig(
            "spacecraft-" + mission.getName(),
            mission.getName(),
            ColorRGBA.Cyan,
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

    context.setSpacecraftPresenter(presenter);

    ExecutorService assetExecutor = AssetFactory.get().assetLoadingExecutor();
    Model3dView model3dView = view.getModel3dView();
    CompletableFuture.supplyAsync(model3dView::loadModel, assetExecutor)
        .thenAccept(model3dView::onModelLoaded);

    trajectoryState = getState(SpacecraftTrajectoryAppState.class);
  }

  @Override
  public void update(float tpf) {
    if (!mission.isOnGoing()) {
      return;
    }

    boolean isPlanetMode = context.focusView().getMode() == ViewMode.PLANET;
    if (!isPlanetMode) {
      view.setVisible(false);
      return;
    }
    view.setVisible(true);

    AbsoluteDate now = clock.now();
    mission.update(now);

    SpacecraftState state = mission.getCurrentState();
    if (state == null) {
      return;
    }

    Vector3D posGcrf = state.getPosition();
    presenter.updatePose(posGcrf, renderContext);

    Camera cam = getApplication().getCamera();
    view.updateScreen(cam);

    if (trajectoryState != null) {
      trajectoryState.addPosition(posGcrf);
    }
  }

  @Override
  protected void cleanup(Application app) {
    view.spatial().removeFromParent();
    view.detach();
    context.setSpacecraftPresenter(null);
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
