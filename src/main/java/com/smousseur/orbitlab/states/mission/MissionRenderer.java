package com.smousseur.orbitlab.states.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
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
   *
   * <p>Public because it is applied by {@link
   * com.smousseur.orbitlab.states.camera.CameraTransitionAppState}, which now owns the framing of
   * every focus target so it can animate its way to it.
   */
  public static final float SPACECRAFT_FOCUS_DISTANCE_SOLAR_UNITS = 5e-7f;

  private final MissionEntry entry;
  private final ApplicationContext context;

  /**
   * The context this renderer was built with — the arc the mission <em>starts</em> in.
   *
   * <p>Construction-time only, and only for what does not depend on the body: the scale {@link
   * LodView} sizes the spacecraft with, and the parent body {@link #onSpacecraftSelected()} hands
   * the camera. Everything drawn per frame takes its context from the sample instead (spec {@code
   * docs/multi-corps/05-conception-L3.md} §3.1). L5, which has to place a lunar arc rather than
   * merely name it, is where this field has to be revisited.
   */
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

    trajectoryRenderer = new MissionTrajectoryRenderer(entry.id(), trajectoryColor);
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
   * <p>Static, and public, for the same reason as {@link #renderContextFor(MissionEphemerisPoint)}:
   * {@link
   * MissionOrchestratorAppState} evaluates it on an entry whose renderer already exists, to find
   * out whether that renderer still draws the right vehicle.
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
   * The render context a sample is drawn in: planet scale (1 unit = 1 km), centred on the body of
   * <b>that sample's arc</b>.
   *
   * <p><b>It takes a point and not a mission entry</b>, and that is the whole of PHY-4 / L3's
   * rendering seam (spec {@code docs/multi-corps/05-conception-L3.md} §3.1). Three states convert
   * the same spacecraft position every frame — {@link
   * com.smousseur.orbitlab.states.camera.FloatingOriginAppState} negates it onto the near frame,
   * {@code MissionOrchestratorAppState} places the anchor at it, {@code CameraTransitionAppState}
   * aims at it — and they must not disagree about the context. All three already call {@link
   * com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris#displayPointAt} at the same
   * date, so deriving the context from the point they already share makes disagreement impossible
   * without them first disagreeing about the position, which would break everything anyway. Read
   * from the entry, the arc would be a second, independent lookup — the one place a stale frame
   * could creep in.
   *
   * <p><b>What this does not do.</b> {@code JmeVectorAdapter.toJmeBodyRelativePosition} reads only
   * the scale and the axis convention, and both are the same for every planet-scale context: {@code
   * planet(EARTH)} and {@code planet(MOON)} produce the same {@code float} triple for the same
   * vector. Switching on the arc therefore fixes <em>which body the coordinates are about</em>, not
   * where the line lands on screen. Drawing a lunar arc in its right place is L5's work.
   *
   * @param point the sample being drawn
   * @return the render context that sample is drawn in
   */
  public static RenderContext renderContextFor(MissionEphemerisPoint point) {
    return RenderContext.planet(point.arc().body());
  }

  private void onSpacecraftSelected() {
    SolarSystemBody parentBody =
        renderContext.targetBody().orElseGet(() -> context.focusView().getBody());
    context.cameraTransition().requestSpacecraft(entry.id(), parentBody);
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
   * <p><b>The context is derived here, per frame, and passed down.</b> It used to be the field this
   * renderer was built with, which was correct only because a mission had one body for its whole
   * life. Taking it from the point instead is what makes the anchor and the near-frame offset read
   * the same arc by construction (spec {@code docs/multi-corps/05-conception-L3.md} §3.2). Today it
   * changes no arithmetic — the conversion is blind to the body — so this is structure, not
   * behaviour; its value is that no stale context survives anywhere below.
   *
   * @param point the interpolated ephemeris point, whose position also serves as the trail tip
   * @param trail the mission's display polyline, the same instance on every frame
   * @param upTo index of the last trail vertex flown at the current instant
   * @param cam the active camera
   * @param tpf frame time in seconds, used for orientation smoothing
   */
  public void updateFromEphemeris(
      MissionEphemerisPoint point, TrajectoryPolyline trail, int upTo, Camera cam, float tpf) {
    RenderContext context = renderContextFor(point);
    presenter.updatePose(point.position(), point.velocity(), tpf, context);
    // Always allowed its 3D model: a spacecraft anchor hangs off the near bodies node with its own
    // body-relative position, so it does not compete for the near origin the way the planets do.
    view.updateScreen(cam, true);
    trajectoryRenderer.update(trail, upTo, point.position(), context);
  }

  /**
   * Detaches all visual elements from the scene. Registration in {@link ApplicationContext} is
   * owned by {@link MissionOrchestratorAppState}, which deregisters this renderer before calling
   * here.
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
