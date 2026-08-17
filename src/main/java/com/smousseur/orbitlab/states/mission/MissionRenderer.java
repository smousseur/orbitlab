package com.smousseur.orbitlab.states.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import com.smousseur.orbitlab.engine.scene.spacecraft.LauncherAssets;
import com.smousseur.orbitlab.engine.scene.spacecraft.SpacecraftPresenter;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
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
   * <p><b>Scale, and nothing else.</b> L3 §3.1 left it serving two purposes and asked L5 to revisit
   * it; L5 did, and took the second away — {@link #onSpacecraftSelected()} now reads the arc the
   * spacecraft is in right now, and falls back here only when the trajectory is missing. What
   * remains is the metres-per-unit {@link LodView} sizes the spacecraft with, which is the same for
   * every planet-scale context whatever the body. Everything drawn per frame derives its own context
   * from the sample.
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
   * <p>Static, and public, for the same reason as {@link #renderBodyOf}:
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
   * The body every drawn coordinate of a mission is expressed about, this frame.
   *
   * <p><b>It takes a point and not a mission entry</b>, and that is the whole of PHY-4 / L3's
   * rendering seam (spec {@code docs/multi-corps/05-conception-L3.md} §3.1), which L5 extends
   * rather than replaces. Three states convert the same spacecraft position every frame — {@link
   * com.smousseur.orbitlab.states.camera.FloatingOriginAppState} negates it onto the near frame,
   * {@code MissionOrchestratorAppState} places the anchor at it, {@code CameraTransitionAppState}
   * aims at it — and they must not disagree. All three already call {@link
   * com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris#displayPointAt} at the same
   * date, so deriving this from the point they already share makes disagreement impossible without
   * them first disagreeing about the position, which would break everything anyway. Publishing it
   * once per frame instead would not work: {@code CameraTransitionAppState} is attached before
   * {@code FloatingOriginAppState}, so it would read the previous frame's value.
   *
   * <p><b>Why the arc in spacecraft view and the focus elsewhere</b> (spec {@code
   * docs/multi-corps/07-conception-L5.md} §3.1). Following a spacecraft, the near scene is centred
   * on the spacecraft and the one globe the near viewport can hold has to be the body its
   * coordinates are about, or the Earth would be drawn where the Moon should be — 1 837 km from a
   * spacecraft at perilune. Looking at a planet, the centre is that planet and the trajectory has to
   * come to it. The switch therefore happens at the arc boundary, atomically, and reverses by itself
   * when the clock is scrubbed backwards, because it is a function of the sample and not an event.
   *
   * @param point the sample being drawn
   * @param view the current focus
   * @return the body that sample is drawn about
   */
  public static SolarSystemBody renderBodyOf(MissionEphemerisPoint point, FocusView view) {
    return view.getMode() == ViewMode.SPACECRAFT ? point.arc().body() : view.getBody();
  }

  /**
   * The render context a sample is drawn in: planet scale (1 unit = 1 km), centred on {@link
   * #renderBodyOf}.
   *
   * @param point the sample being drawn
   * @param view the current focus
   * @return the render context that sample is drawn in
   */
  public static RenderContext renderContextFor(MissionEphemerisPoint point, FocusView view) {
    return RenderContext.planet(renderBodyOf(point, view));
  }

  /**
   * A sample's position, expressed about {@code renderBody} rather than about its own arc.
   *
   * <p>The point-sized half of L5's conversion; {@link
   * com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline#positionAt} is the bulk
   * half. <b>Both go through {@code TrajectoryArc.convertPosition}</b>, and that is not tidiness: the
   * vertices are written relative to this very position, so a second conversion path would put a
   * visible kink between the last vertex and the spacecraft model — at the one place the eye is
   * looking (spec {@code docs/multi-corps/07-conception-L5.md} §3.3).
   *
   * <p>Returns the argument untouched when the bodies agree, which is every trajectory that exists
   * before L6.
   *
   * @param point the sample
   * @param renderBody the body to express it about
   * @return the position in that body's frame, in metres
   */
  public static Vector3D renderPositionOf(
      MissionEphemerisPoint point, SolarSystemBody renderBody) {
    return point.arc().body() == renderBody
        ? point.position()
        : point
            .arc()
            .convertPosition(point.position(), point.time(), TrajectoryArc.forBody(renderBody));
  }

  /**
   * Hands the camera the body the spacecraft is <em>currently</em> orbiting, not the one it launched
   * from: clicking a spacecraft already inside the lunar sphere of influence must frame it against
   * the Moon. Falls back to the construction-time context while the trajectory is unavailable, the
   * same degradation {@code FloatingOriginAppState} accepts.
   */
  private void onSpacecraftSelected() {
    MissionEphemeris ephemeris = entry.getEphemeris().orElse(null);
    SolarSystemBody parentBody =
        ephemeris != null
            ? ephemeris.displayPointAt(context.clock().now()).arc().body()
            : renderContext.targetBody().orElseGet(() -> context.focusView().getBody());
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
   * the same body by construction (spec {@code docs/multi-corps/05-conception-L3.md} §3.2).
   *
   * <p><b>And the position is converted here, once.</b> The sample is expressed about its own arc's
   * body, which is not necessarily the one the near scene is centred on — looking at the Earth while
   * the spacecraft is at perilune, an unconverted anchor would be planted 1 837 km from the
   * geocentre. The converted value feeds the spacecraft's pose and serves as the origin the ribbon's
   * vertices are written against, so the two cannot come from different frames.
   *
   * @param point the interpolated ephemeris point, whose position also serves as the trail tip
   * @param trail the mission's display polyline, the same instance on every frame
   * @param upTo index of the last trail vertex flown at the current instant
   * @param cam the active camera
   * @param tpf frame time in seconds, used for orientation smoothing
   */
  public void updateFromEphemeris(
      MissionEphemerisPoint point, TrajectoryPolyline trail, int upTo, Camera cam, float tpf) {
    SolarSystemBody renderBody = renderBodyOf(point, context.focusView());
    RenderContext ctx = RenderContext.planet(renderBody);
    Vector3D position = renderPositionOf(point, renderBody);
    // The velocity is deliberately left in the arc's own frame. Two body-centred ICRF frames share
    // their axes but not their motion, so a converted velocity would point somewhere else — and
    // what this drives is the model's attitude, which belongs to the frame the vehicle actually
    // flies in, not to the one it happens to be drawn about.
    presenter.updatePose(position, point.velocity(), tpf, ctx);
    // Always allowed its 3D model: a spacecraft anchor hangs off the near bodies node with its own
    // body-relative position, so it does not compete for the near origin the way the planets do.
    view.updateScreen(cam, true);
    trajectoryRenderer.update(trail, upTo, position, ctx);
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
