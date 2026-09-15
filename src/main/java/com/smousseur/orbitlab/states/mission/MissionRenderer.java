package com.smousseur.orbitlab.states.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.EclipseGeometry;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.spacecraft.LauncherAssets;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisServiceRegistry;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * Encapsulates all rendering for a single mission: the primary spacecraft, its trajectory, and — as
 * of PHY-5 / L1 — each jettisoned object. This is NOT an AppState — it is a plain object managed by
 * {@link MissionOrchestratorAppState}.
 *
 * <p>Every drawn object is a {@link TrackedObjectView}; this class coordinates them. The primary is
 * the one built with a click handler and the one whose eclipse occluder is pushed; the debris are
 * lean views drawn each frame from their own ephemerides (spec {@code
 * docs/multi-objets/03-conception-L1.md} §2.4).
 */
public final class MissionRenderer {

  /**
   * Camera distance applied when the user clicks the spacecraft, expressed in solar-scale units (1
   * unit = 1e9 m). This value is consumed by the far camera via {@link
   * com.smousseur.orbitlab.states.camera.OrbitCameraAppState}; the near viewport tracks it through
   * {@link com.smousseur.orbitlab.states.camera.NearCameraSyncAppState} (position scaled by 1e6).
   *
   * <p><b>{@code 3.5e-7} ≈ 350 m is five times the tallest launcher of the catalog</b>, which is
   * what 500 m was back when every vehicle was drawn 100 m tall whatever it was. Holding the
   * distance instead of the ratio would have shrunk the Falcon Heavy by 30 % on screen and the
   * Ariane 64 by 38 % the day L5 gave them their real heights; holding the ratio per launcher would
   * have made every vehicle fill the frame identically, throwing away the size comparison those
   * heights just bought. So: one distance, set by the tallest, and a shorter launcher honestly
   * looks shorter.
   *
   * <p>It still leaves the model outside the near viewport's clip plane — 70 m at this distance,
   * since {@code NearCameraSyncAppState} derives it from the focus distance — and well inside the
   * LOD switch, which promotes the 3D model once its projected radius passes 10 px.
   *
   * <p>Public because it is applied by {@link
   * com.smousseur.orbitlab.states.camera.CameraTransitionAppState}, which now owns the framing of
   * every focus target so it can animate its way to it.
   */
  public static final float SPACECRAFT_FOCUS_DISTANCE_SOLAR_UNITS = 3.5e-7f;

  /** The suffix that turns a launcher mesh path into its first booster's, for a debris (L1). */
  private static final String DEBRIS_MESH_SUFFIX = "-booster1.gltf";

  /** A neutral colour for a debris in L1; per-debris colour is L2. */
  private static final ColorRGBA DEBRIS_COLOR = new ColorRGBA(0.7f, 0.7f, 0.72f, 1.0f);

  /** Half the drawn height of a debris (m), a placeholder until the per-piece height (L2, §6). */
  private static final double DEBRIS_DRAWN_RADIUS_METERS = 20.0;

  private final MissionEntry entry;
  private final ApplicationContext context;

  /**
   * The context this renderer was built with — the arc the mission <em>starts</em> in.
   *
   * <p><b>Scale, and nothing else.</b> L3 §3.1 left it serving two purposes and asked L5 to revisit
   * it; L5 did, and took the second away — {@link #onSpacecraftSelected()} now reads the arc the
   * spacecraft is in right now, and falls back here only when the trajectory is missing. What
   * remains is the metres-per-unit {@link LodView} sizes the spacecraft with, which is the same for
   * every planet-scale context whatever the body. Everything drawn per frame derives its own
   * context from the sample.
   */
  private final RenderContext renderContext;

  private final ColorRGBA trajectoryColor;

  private TrackedObjectView primary;
  private String modelPath;

  /** One view per jettisoned object, rebuilt when the entry's debris change (a recomputation). */
  private final List<TrackedObjectView> debrisViews = new ArrayList<>();

  /**
   * The debris list {@link #debrisViews} was built from, compared by identity to detect a change.
   */
  private List<DebrisTrack> builtDebris = List.of();

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

  /** Initializes the primary object's view, and one view per jettisoned object. */
  public void initialize() {
    Mission mission = entry.mission();
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
            drawnRadiusOf(entry),
            modelPath,
            renderContext);

    primary =
        TrackedObjectView.create(
            context, config, entry.id().toString(), trajectoryColor, this::onSpacecraftSelected);

    rebuildDebrisViews();
  }

  /**
   * Half the launcher's height, which is the number {@link BodyRenderConfig} asks for.
   *
   * <p><b>Half, and not the height</b>: {@code Model3dView} scales the mesh by twice it, and both
   * launcher assets are normalized to exactly one unit tall with their base at the origin — so the
   * vehicle comes out at its own height, and every detached piece of the same asset set comes out
   * at its own fraction of it, with no per-piece number anywhere. The same value is the radius
   * {@link LodView} projects to decide the 3D-versus-icon switch, so a shorter vehicle turns back
   * into its icon closer in. That is the whole of the per-object LOD threshold: it follows the
   * height rather than being configured (spec {@code docs/etagement/01-decoupage.md} §3.8).
   *
   * <p>A mission carrying no spec is the legacy path, drawn with {@code
   * LauncherAssets.DEFAULT_MODEL_PATH} — and {@link LauncherModel#DEFAULT_HEIGHT_METERS} is that
   * very mesh's height.
   */
  private static double drawnRadiusOf(MissionEntry entry) {
    return entry
            .spec()
            .map(spec -> spec.configuration().launcher().heightMeters())
            .orElse(LauncherModel.DEFAULT_HEIGHT_METERS)
        / 2.0;
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
   * <p>Static, and public, for the same reason as {@link #renderBodyOf}: {@link
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
   * The GLTF asset this renderer's primary was initialized with. Fixed for its whole life —
   * swapping the mesh of a live {@link LodView} is not supported, so a launcher change is handled
   * by disposing the renderer and creating a new one.
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
   * com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris#displayPointAt} at the
   * same date, so deriving this from the point they already share makes disagreement impossible
   * without them first disagreeing about the position, which would break everything anyway.
   * Publishing it once per frame instead would not work: {@code CameraTransitionAppState} is
   * attached before {@code FloatingOriginAppState}, so it would read the previous frame's value.
   *
   * <p><b>Why the arc in spacecraft view and the focus elsewhere</b> (spec {@code
   * docs/multi-corps/07-conception-L5.md} §3.1). Following a spacecraft, the near scene is centred
   * on the spacecraft and the one globe the near viewport can hold has to be the body its
   * coordinates are about, or the Earth would be drawn where the Moon should be — 1 837 km from a
   * spacecraft at perilune. Looking at a planet, the centre is that planet and the trajectory has
   * to come to it. The switch therefore happens at the arc boundary, atomically, and reverses by
   * itself when the clock is scrubbed backwards, because it is a function of the sample and not an
   * event.
   *
   * @param point the sample being drawn
   * @param view the current focus
   * @return the body that sample is drawn about
   */
  public static SolarSystemBody renderBodyOf(MissionEphemerisPoint point, FocusView view) {
    return view.getMode() == ViewMode.SPACECRAFT ? point.arc().body() : view.renderCentreBody();
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
   * half. <b>Both go through {@code TrajectoryArc.convertPosition}</b>, and that is not tidiness:
   * the vertices are written relative to this very position, so a second conversion path would put
   * a visible kink between the last vertex and the spacecraft model — at the one place the eye is
   * looking (spec {@code docs/multi-corps/07-conception-L5.md} §3.3).
   *
   * <p>Returns the argument untouched when the bodies agree, which is every trajectory that exists
   * before L6.
   *
   * @param point the sample
   * @param renderBody the body to express it about
   * @return the position in that body's frame, in metres
   */
  public static Vector3D renderPositionOf(MissionEphemerisPoint point, SolarSystemBody renderBody) {
    return point.arc().body() == renderBody
        ? point.position()
        : point
            .arc()
            .convertPosition(point.position(), point.time(), TrajectoryArc.forBody(renderBody));
  }

  /**
   * Hands the camera the body the spacecraft is <em>currently</em> orbiting, not the one it
   * launched from: clicking a spacecraft already inside the lunar sphere of influence must frame it
   * against the Moon. Falls back to the construction-time context while the trajectory is
   * unavailable, the same degradation {@code FloatingOriginAppState} accepts.
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
   * Shows/hides all visual elements (primary and debris).
   *
   * @param visible whether to show or hide
   */
  public void setVisible(boolean visible) {
    if (primary != null) {
      primary.setVisible(visible);
    }
    for (TrackedObjectView debris : debrisViews) {
      debris.setVisible(visible);
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
   * <p>The primary is drawn from the point the orchestrator interpolated; each debris is drawn from
   * its own ephemeris at {@code now}, and stays hidden until {@code now} reaches its jettison — a
   * debris does not exist before its separation, and reappears by itself when the clock is scrubbed
   * back, because visibility is a function of the date and not an event (spec {@code
   * docs/multi-objets/03-conception-L1.md} §2.4).
   *
   * @param point the interpolated primary point, whose position also serves as the trail tip
   * @param trail the primary's display polyline, the same instance on every frame
   * @param upTo index of the last trail vertex flown at the current instant
   * @param now the current simulation date, for the debris' own interpolation
   * @param cam the active camera
   * @param tpf frame time in seconds, used for orientation smoothing
   */
  public void updateFromEphemeris(
      MissionEphemerisPoint point,
      TrajectoryPolyline trail,
      int upTo,
      AbsoluteDate now,
      Camera cam,
      float tpf) {
    FocusView focus = context.focusView();
    primary.updateFromPoint(point, trail, upTo, cam, tpf, focus);
    pushEclipseOccluder(point, focus);
    updateDebris(now, cam, tpf, focus);
  }

  /** Draws each debris from its own ephemeris at {@code now}, hidden before its jettison. */
  private void updateDebris(AbsoluteDate now, Camera cam, float tpf, FocusView focus) {
    syncDebrisViews();
    for (int i = 0; i < debrisViews.size(); i++) {
      TrackedObjectView debrisView = debrisViews.get(i);
      MissionEphemeris ephemeris = builtDebris.get(i).ephemeris();
      if (now.compareTo(ephemeris.startDate()) < 0) {
        debrisView.setVisible(false);
        continue;
      }
      TrajectoryPolyline trail = ephemeris.displayTrail();
      boolean within = now.compareTo(ephemeris.endDate()) <= 0;
      MissionEphemerisPoint pt = ephemeris.displayPointAt(now);
      int upTo = within ? trail.indexUpTo(now) : trail.size() - 1;
      debrisView.setVisible(true);
      debrisView.updateFromPoint(pt, trail, upTo, cam, tpf, focus);
    }
  }

  /** Rebuilds the debris views when a recomputation replaced the entry's debris list. */
  private void syncDebrisViews() {
    if (entry.getDebris() == builtDebris) {
      return;
    }
    rebuildDebrisViews();
  }

  private void rebuildDebrisViews() {
    for (TrackedObjectView debris : debrisViews) {
      debris.cleanup();
    }
    debrisViews.clear();
    builtDebris = entry.getDebris();
    for (int i = 0; i < builtDebris.size(); i++) {
      BodyRenderConfig config = debrisConfig(i, builtDebris.get(i));
      debrisViews.add(
          TrackedObjectView.create(
              context, config, entry.id() + "-debris-" + i, DEBRIS_COLOR, null));
    }
  }

  /**
   * The render config for one debris: the first-booster mesh derived from the launcher's, a neutral
   * colour, a placeholder height, and the scale context of the arc it starts in.
   */
  private BodyRenderConfig debrisConfig(int index, DebrisTrack track) {
    RenderContext scale = RenderContext.planet(track.ephemeris().firstPoint().arc().body());
    String debrisMeshPath = modelPath.replace(".gltf", DEBRIS_MESH_SUFFIX);
    return new BodyRenderConfig(
        "mission-" + entry.id() + "-debris-" + index,
        entry.mission().getName() + " debris",
        DEBRIS_COLOR,
        DEBRIS_DRAWN_RADIUS_METERS,
        debrisMeshPath,
        scale);
  }

  /**
   * Pushes the arc's own central body as the primary spacecraft's eclipse occulter (`docs/eclipses/
   * 01-decoupage.md`, L1) — the render body follows physics, not the camera. The three quantities
   * derived here — the render body, the converted position, and the context — are pure functions of
   * the sample and the focus, so recomputing them beside {@link TrackedObjectView#updateFromPoint}
   * cannot disagree with it. Debris push no occluder.
   */
  private void pushEclipseOccluder(MissionEphemerisPoint point, FocusView focus) {
    SolarSystemBody renderBody = renderBodyOf(point, focus);
    RenderContext ctx = RenderContext.planet(renderBody);
    Vector3D position = renderPositionOf(point, renderBody);

    SolarSystemBody occluderBody = point.arc().body();
    Vector3D occluderCentreInRenderFrame =
        point.arc().convertPosition(Vector3D.ZERO, point.time(), TrajectoryArc.forBody(renderBody));
    Vector3D occluderPositionMeters = occluderCentreInRenderFrame.subtract(position);
    Vector3f occluderPositionRender =
        JmeVectorAdapter.toJmeBodyRelativePosition(occluderPositionMeters, ctx);
    float occluderRadiusRender =
        (float) (PlanetRadius.radiusFor(occluderBody) * ctx.unitsPerMeter());

    EphemerisServiceRegistry.get()
        .orElseThrow(() -> new OrbitlabException("Cannot get EphemerisService"))
        .trySampleHelioIcrf(occluderBody, point.time())
        .ifPresent(
            occluderHelio -> {
              Vector3D occluderHelioPosition = occluderHelio.getKey();
              double sunDistanceMeters = occluderHelioPosition.getNorm();
              Vector3D sunDirectionIcrf = occluderHelioPosition.negate().normalize();
              Vector3f sunDirectionRender =
                  JmeVectorAdapter.toVector3f(ctx.axisConvention().icrfToJme(sunDirectionIcrf));
              float sunApparentRadius =
                  (float) EclipseGeometry.sunApparentRadius(sunDistanceMeters);
              primary
                  .view()
                  .setOccluder(
                      occluderPositionRender,
                      occluderRadiusRender,
                      sunDirectionRender,
                      sunApparentRadius);
            });
  }

  /**
   * Detaches all visual elements from the scene. Registration in {@link ApplicationContext} is
   * owned by {@link MissionOrchestratorAppState}, which deregisters this renderer before calling
   * here.
   */
  public void cleanup() {
    if (primary != null) {
      primary.cleanup();
    }
    for (TrackedObjectView debris : debrisViews) {
      debris.cleanup();
    }
    debrisViews.clear();
  }
}
