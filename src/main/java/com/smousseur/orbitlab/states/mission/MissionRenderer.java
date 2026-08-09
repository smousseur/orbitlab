package com.smousseur.orbitlab.states.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import com.smousseur.orbitlab.engine.scene.spacecraft.LauncherAssets;
import com.smousseur.orbitlab.engine.scene.spacecraft.SpacecraftPresenter;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Encapsulates all rendering for a single mission: spacecraft display (SpacecraftPresenter +
 * LodView) and trajectory line (delegated to {@link MissionTrajectoryRenderer}). This is NOT an
 * AppState — it is a plain object managed by {@link MissionOrchestratorAppState}.
 */
public final class MissionRenderer {

  private static final double SPACECRAFT_RADIUS_METERS = 50.0;

  /**
   * Camera distance applied when the user clicks the spacecraft, expressed in solar-scale units (1
   * unit = 1e9 m). This value is consumed by the far camera via {@link
   * com.smousseur.orbitlab.states.camera.OrbitCameraAppState}; the near viewport tracks it through
   * {@link com.smousseur.orbitlab.states.camera.NearCameraSyncAppState} (position scaled by 1e6).
   * {@code 5e-7} ≈ 500 m, which places the camera well inside the LOD-3D threshold ({@code radius ×
   * lodMultiplier} = 0.05 × 500 = 25 km in km units) and outside the near viewport's clip plane —
   * 100 m at this distance, since {@code NearCameraSyncAppState} derives it from the focus distance
   * — so the 3D model appears immediately.
   */
  private static final float SPACECRAFT_FOCUS_DISTANCE_SOLAR_UNITS = 5e-7f;

  private final MissionEntry entry;
  private final ApplicationContext context;
  private final RenderContext renderContext;
  private final ColorRGBA trajectoryColor;

  private SpacecraftPresenter presenter;
  private LodView view;
  private MissionTrajectoryRenderer trajectoryRenderer;
  private String modelPath;

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

  /** Initializes the spacecraft view and trajectory geometry. */
  public void initialize() {
    Mission mission = entry.mission();
    Node guiNode = context.guiGraph().getPlanetBillboardsNode();
    // Resolved once and kept: the mesh is baked into the LodView at construction, so modelPath()
    // is what actually flies — that is what MissionOrchestratorAppState compares against the
    // entry's current launcher to detect a wizard edit that swapped it.
    modelPath = modelPathFor(entry);

    // The scene-graph id is derived from the mission id, not the name: names may be duplicated, and
    // two homonymous missions sharing a spatial id would collide in the graph. The name is still
    // carried as the display label (second argument).
    BodyRenderConfig config =
        new BodyRenderConfig(
            "mission-" + entry.id(),
            mission.getName(),
            trajectoryColor,
            SPACECRAFT_RADIUS_METERS,
            modelPath,
            renderContext);

    view = new LodView(guiNode, config, this::onSpacecraftSelected, null);
    presenter = new SpacecraftPresenter(config.id(), view);
    presenter.setVisible(true);

    Node anchor = (Node) view.spatial();
    anchor.attachChild(view.nearSpatial());
    context.sceneGraph().nearBodiesNode().attachChild(anchor);

    ExecutorService assetExecutor = AssetFactory.get().assetLoadingExecutor();
    Model3dView model3dView = view.getModel3dView();
    CompletableFuture.supplyAsync(model3dView::loadModel, assetExecutor)
        .thenApply(spatial -> AssetFactory.get().applyLambert(spatial, 0.3f))
        .thenAccept(model3dView::onModelLoaded);

    trajectoryRenderer =
        new MissionTrajectoryRenderer(entry.id(), renderContext, trajectoryColor);
    trajectoryRenderer.initialize(context.sceneGraph().nearOrbitsNode());
  }

  /**
   * The mesh a mission is drawn with: the one paired with its launcher.
   *
   * <p>Read from the {@link com.smousseur.orbitlab.simulation.mission.operation.MissionSpec spec}
   * rather than from the built {@link Mission}, because the mission only keeps the assembled {@code
   * VehicleStack} — the stages' masses and propulsion, with the launcher identity already dissolved
   * into them. The spec is also stable across the recompositions {@link MissionEntry} performs on a
   * mode toggle or a wizard edit; a legacy entry carries none and falls back.
   *
   * <p>Static, and public, for the same reason as {@link #renderContextFor(MissionEntry)}: {@link
   * MissionOrchestratorAppState} evaluates it on an entry whose renderer already exists, to find out
   * whether that renderer still draws the right vehicle.
   *
   * @param entry the mission entry
   * @return the GLTF asset path to draw that mission's spacecraft with
   */
  public static String modelPathFor(MissionEntry entry) {
    return entry
        .spec()
        .map(spec -> LauncherAssets.modelPath(spec.configuration().launcher().id()))
        .orElse(LauncherAssets.DEFAULT_MODEL_PATH);
  }

  /**
   * The GLTF asset this renderer was initialized with. Fixed for its whole life — swapping the mesh
   * of a live {@link LodView} is not supported, so a launcher change is handled by disposing the
   * renderer and creating a new one.
   *
   * @return the asset path, or {@code null} before {@link #initialize()}
   */
  public String modelPath() {
    return modelPath;
  }

  /**
   * The render context a mission is drawn in: planet scale (1 unit = 1 km), centred on the body its
   * objective targets.
   *
   * <p>Static, and public, because {@link
   * com.smousseur.orbitlab.states.camera.FloatingOriginAppState} needs it before any renderer
   * exists: it converts the focused spacecraft's position itself, and has to use the very same
   * context to land on the same {@code float} triple as the anchor.
   *
   * @param entry the mission entry
   * @return the render context that mission is drawn in
   */
  public static RenderContext renderContextFor(MissionEntry entry) {
    return RenderContext.planet(entry.mission().getObjective().body());
  }

  private void onSpacecraftSelected() {
    FocusView focusView = context.focusView();
    SolarSystemBody parentBody = renderContext.targetBody().orElse(focusView.getBody());
    focusView.setCameraDistance(SPACECRAFT_FOCUS_DISTANCE_SOLAR_UNITS);
    focusView.viewSpacecraft(entry.id(), parentBody);
  }

  /**
   * Shows/hides all visual elements (spacecraft and trajectory).
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
   * <p>Draws unconditionally: whether the mission belongs on screen at all is decided by {@link
   * MissionOrchestratorAppState}, which owns every visibility rule and skips this call entirely
   * when the answer is no. Re-testing it here is what previously let the two disagree.
   *
   * @param point the interpolated ephemeris point, whose position also serves as the trail tip
   * @param trail the mission's display polyline, the same instance on every frame
   * @param upTo index of the last trail vertex flown at the current instant
   * @param cam the active camera
   * @param tpf frame time in seconds, used for orientation smoothing
   */
  public void updateFromEphemeris(
      MissionEphemerisPoint point, TrajectoryPolyline trail, int upTo, Camera cam, float tpf) {
    presenter.updatePose(point.position(), point.velocity(), tpf, renderContext);
    view.updateScreen(cam);
    trajectoryRenderer.update(trail, upTo, point.position());
  }

  /**
   * Detaches all visual elements from the scene. Registration in {@link ApplicationContext} is owned
   * by {@link MissionOrchestratorAppState}, which deregisters this renderer before calling here.
   */
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
