package com.smousseur.orbitlab.states.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.AxisConvention;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.EclipseGeometry;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.planet.PlanetDrawnRotation;
import com.smousseur.orbitlab.engine.scene.spacecraft.LauncherAssets;
import com.smousseur.orbitlab.engine.scene.spacecraft.LauncherStackGeometry;
import com.smousseur.orbitlab.engine.scene.spacecraft.PayloadAssets;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisServiceRegistry;
import com.smousseur.orbitlab.simulation.mission.FollowedObject;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.SeparationImpulse;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

  /**
   * Mount radius (m) a jettisoned booster is drawn out to, along the flank it occupied on the stack
   * (the shared fan direction, {@link SeparationImpulse#fanDirection}), so it sits back where it
   * detached rather than piling on the axis (PHY-5 / L6, spec {@code
   * docs/multi-objets/08-conception-L6.md} §D2). Sized to the measured mount ring (~0.06 of the
   * stack height, ≈ 4 m for the Ariane 64).
   */
  private static final double BOOSTER_LATERAL_SPREAD_METERS = 4.0;

  /**
   * Below this altitude (m) a debris' last sample counts as an impact: it lands, and gets a ground
   * track. Above it, the debris ends at the mission horizon in orbit and gets none (PHY-5 / L7
   * §D2).
   */
  private static final double LANDED_ALTITUDE_METERS = 1000.0;

  private final MissionEntry entry;
  private final ApplicationContext context;

  /**
   * The context this renderer was built with — the arc the mission <em>starts</em> in.
   *
   * <p><b>Scale, and nothing else.</b> L3 §3.1 left it serving two purposes; L5 took the second
   * away and SEL-1 / L2 the last — {@link #onSpacecraftSelected()} now delegates to {@code
   * FocusController}, which resolves the arc the object is in and falls back to the focused body,
   * not here. What remains is the metres-per-unit {@link LodView} sizes the spacecraft with, which
   * is the same for every planet-scale context whatever the body. Everything drawn per frame
   * derives its own context from the sample.
   */
  private final RenderContext renderContext;

  private final ColorRGBA trajectoryColor;

  private TrackedObjectView primary;
  private String modelPath;

  /** One view per jettisoned object, rebuilt when the entry's debris change (a recomputation). */
  private final List<TrackedObjectView> debrisViews = new ArrayList<>();

  /**
   * One ground-track view per debris, parallel to {@link #debrisViews}: {@code null} for an orbital
   * debris (no ground track), and for a landing debris until its rotations are available and it is
   * built lazily (PHY-5 / L7 §D3).
   */
  private final List<DebrisGroundTrackView> debrisGroundTracks = new ArrayList<>();

  /**
   * The debris list {@link #debrisViews} was built from, compared by identity to detect a change.
   */
  private List<DebrisTrack> builtDebris = List.of();

  /** The primary's silhouette timeline, derived from the debris (PHY-5 / L3). */
  private PrimarySilhouette silhouette = PrimarySilhouette.from(List.of());

  /** The payload's drawn asset (mesh + true size), resolved once — null when it has no mesh. */
  private PayloadAssets.PayloadAsset payloadAsset;

  /** Full stack height (m) — the launcher's own height — for the render-only stack seats (L6). */
  private double stackHeightMeters;

  /** The {@code after_s1} remnant's fraction of the full stack, for the primary's seat (L6). */
  private double afterS1Fraction;

  /** How many boosters are jettisoned in the block, for their lateral fan (L6). */
  private int boosterCount;

  /** The mesh currently drawn, compared each frame to detect a silhouette change (PHY-5 / L5). */
  private MeshTarget appliedTarget;

  /**
   * A silhouette's drawn mesh and its size. A {@code drawnSizeMeters} of {@code -1} means "use the
   * launcher config's own scale" (a launcher phase, whose asset is one unit tall); a positive value
   * is the payload's true catalog size, to which its bounding box is normalized (spec {@code
   * docs/multi-objets/07-conception-L5.md} §3.3).
   */
  private record MeshTarget(String path, double drawnSizeMeters) {}

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
    stackHeightMeters = 2.0 * drawnRadiusOf(entry);
    afterS1Fraction =
        LauncherStackGeometry.afterS1Fraction(
            entry.spec().map(spec -> spec.configuration().launcher().id()).orElse(null));
    payloadAsset =
        entry
            .spec()
            .map(spec -> spec.configuration().payloadId())
            .flatMap(PayloadAssets::forPayload)
            .orElse(null);
    appliedTarget = launcherTarget(modelPath);

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
            context,
            config,
            entry.id().toString(),
            trajectoryColor,
            this::onSpacecraftSelected,
            context.sceneGraph().nearBodiesNode());

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
   * Follows the mission's primary — camera and telemetry both — through the shared {@code
   * FocusController}, which resolves the body the object is currently orbiting (SEL-1 / L2).
   * Clicking the spacecraft now also points the telemetry at it, where before it only moved the
   * camera.
   */
  private void onSpacecraftSelected() {
    context.focusController().select(new FollowedObject.Primary(entry.id()));
  }

  /**
   * The click handler a debris carries: it selects the debris (camera + telemetry) through the
   * shared {@code FocusController}, but only while the debris' icon — its handle — is actually
   * shown, i.e. the global "show debris" toggle is on. A decluttered debris is not selectable,
   * which is what lets L3 return the focus to the primary when the toggle is turned off (SEL-1 / L2
   * §2.6).
   */
  private Runnable debrisSelectHandler(DebrisTrack track) {
    return () -> {
      if (context.displaySettings().isDebrisVisible()) {
        context
            .focusController()
            .select(new FollowedObject.Debris(entry.id(), track.role(), track.exemplarIndex()));
      }
    };
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
    // The primary is drawn at its seated point — the propagated sample lifted by the render-only
    // stack seat, baked into the point rather than carried as a separate model offset (SEL-1 / L2).
    // The mesh, its ribbon, the debris reference and the floating origin all key on this one point,
    // so the near frame frames the drawn silhouette — down to a bare payload — instead of the empty
    // centre of mass the seat lifts it off. The eclipse occluder stays on the raw propagated point.
    MissionEphemerisPoint drawnPrimary = renderedPrimaryPoint(point, now);
    primary.updateFromPoint(drawnPrimary, Vector3D.ZERO, null, null, trail, upTo, cam, tpf, focus);
    pushEclipseOccluder(point, focus);
    updatePrimarySilhouette(now);
    updateDebris(drawnPrimary, now, cam, tpf, focus);
  }

  /**
   * The primary silhouette's seat, along the flight direction, this frame: {@code H − drawnHeight},
   * so the nose stays fixed and the stack shrinks from the bottom as it sheds pieces (PHY-5 / L6
   * §D1). {@code FULL} and {@code AFTER_BOOSTERS} keep the full height (boosters are shed
   * laterally), so their seat is zero.
   */
  private double primaryAxialSeat(PrimarySilhouette.SilhouettePhase phase) {
    return switch (phase) {
      case FULL, AFTER_BOOSTERS -> 0.0;
      case AFTER_S1 -> (1.0 - afterS1Fraction) * stackHeightMeters;
      case PAYLOAD -> stackHeightMeters - payloadDrawnHeightMeters();
    };
  }

  /**
   * The drawn height of the payload silhouette: its true catalog size, or the {@code after_s1}
   * remnant's height when the payload has no mesh (the same fallback its mesh takes, §D1).
   */
  private double payloadDrawnHeightMeters() {
    return payloadAsset != null
        ? payloadAsset.drawnSizeMeters()
        : afterS1Fraction * stackHeightMeters;
  }

  /**
   * The primary's drawn point at {@code now}: the propagated sample lifted along the flight
   * direction by the render-only stack seat (PHY-5 / L6, {@link #primaryAxialSeat}). Baking the
   * seat into the <em>point</em> — rather than carrying it as a model offset added on top of a raw
   * anchor — is what lets the near frame centre on the silhouette the eye sees: once the stack has
   * shed down to a bare payload the seat has lifted the mesh a whole launcher-height toward the
   * nose, off the centre of mass the propagation tracks, and centring on the raw sample framed that
   * empty point instead of the payload (SEL-1 / L2).
   *
   * <p>Called with the very same raw sample by this renderer (to place the anchor, its ribbon and
   * the debris reference) and by {@link
   * com.smousseur.orbitlab.states.camera.FloatingOriginAppState} (to offset the near frame): both
   * add a bit-identical seat, so the anchor and the offset stay exact opposites and the silhouette
   * neither jitters nor drifts. The seat is zero in the {@code FULL} and {@code AFTER_BOOSTERS}
   * phases, where the returned sample is {@code raw} itself, so early flight is untouched.
   *
   * @param raw the propagated sample at {@code now}
   * @param now the current simulation date, for the silhouette phase
   * @return the seated sample the primary is drawn at, or {@code raw} when the seat is zero
   */
  public MissionEphemerisPoint renderedPrimaryPoint(MissionEphemerisPoint raw, AbsoluteDate now) {
    Vector3D seat =
        StackSeat.offset(
            raw.velocity(), raw.position(), primaryAxialSeat(silhouette.phaseAt(now)), 0.0, 1, 1);
    if (seat.getNorm() == 0.0) {
      return raw;
    }
    return new MissionEphemerisPoint(
        raw.time(),
        raw.position().add(seat),
        raw.velocity(),
        raw.stageName(),
        raw.propulsive(),
        raw.mass(),
        raw.altitudeMeters(),
        raw.arc());
  }

  /**
   * Swaps the primary's mesh to the silhouette {@code now} calls for, when it differs from the one
   * drawn (PHY-5 / L3). A function of the date, so scrubbing the clock back restores the fuller
   * stack by itself. The suffix glues onto the launcher path: {@code ""} is the full mesh.
   */
  private void updatePrimarySilhouette(AbsoluteDate now) {
    MeshTarget target = targetFor(silhouette.phaseAt(now));
    if (target.equals(appliedTarget)) {
      return;
    }
    if (target.drawnSizeMeters() > 0) {
      primary.swapMesh(target.path(), target.drawnSizeMeters());
    } else {
      primary.swapMesh(target.path());
    }
    appliedTarget = target;
  }

  /**
   * The mesh a silhouette phase resolves to. Launcher phases keep the launcher's own scale (the
   * assets are one unit tall, so {@code Model3dView} sizes them by the vehicle height); the payload
   * is drawn at its true catalog size by normalizing the mesh's bounding box (§3.3). A payload with
   * no mesh — a cargo module, or a mission carrying no catalog payload — falls back to the {@code
   * after_s1} launcher stack, the very target {@code AFTER_S1} yields, so scrubbing across the
   * boundary swaps nothing (spec {@code docs/multi-objets/07-conception-L5.md} §3.2).
   */
  private MeshTarget targetFor(PrimarySilhouette.SilhouettePhase phase) {
    return switch (phase) {
      case FULL -> launcherTarget(modelPath);
      case AFTER_BOOSTERS -> launcherTarget(withLauncherSuffix("-after_boosters"));
      case AFTER_S1 -> launcherTarget(withLauncherSuffix("-after_s1"));
      case PAYLOAD ->
          payloadAsset != null
              ? new MeshTarget(payloadAsset.meshPath(), payloadAsset.drawnSizeMeters())
              : launcherTarget(withLauncherSuffix("-after_s1"));
    };
  }

  private MeshTarget launcherTarget(String path) {
    return new MeshTarget(path, -1.0);
  }

  private String withLauncherSuffix(String suffix) {
    return modelPath.replace(".gltf", suffix + ".gltf");
  }

  /**
   * Draws each debris from its own ephemeris at {@code now}, hidden before its jettison. Each is
   * placed relative to {@code primaryPoint} — the object it hangs under in the scene graph — so its
   * position keeps full precision far from Earth (PHY-5, the GEO "tremble" fix, {@link
   * TrackedObjectView#updateFromPoint}), and carries its own seat so it is drawn where it detached
   * (§D2) rather than piled on the axis. The reference is the primary's <em>drawn</em> point — the
   * same seated point that positions the shared anchor the debris hang under (SEL-1 / L2) — so that
   * anchor's position cancels out of each debris' world position exactly, and the primary's own
   * seat, baked into that shared point, cancels with it rather than being inherited.
   */
  private void updateDebris(
      MissionEphemerisPoint primaryPoint,
      AbsoluteDate now,
      Camera cam,
      float tpf,
      FocusView focus) {
    syncDebrisViews();
    boolean debrisVisible = context.displaySettings().isDebrisVisible();
    for (int i = 0; i < debrisViews.size(); i++) {
      TrackedObjectView debrisView = debrisViews.get(i);
      DebrisTrack track = builtDebris.get(i);
      MissionEphemeris ephemeris = track.ephemeris();
      if (now.compareTo(ephemeris.startDate()) < 0) {
        debrisView.setVisible(false);
        hideGroundTrack(i);
        continue;
      }
      TrajectoryPolyline trail = ephemeris.displayTrail();
      boolean within = now.compareTo(ephemeris.endDate()) <= 0;
      MissionEphemerisPoint pt = ephemeris.displayPointAt(now);
      Vector3D seat = debrisSeat(track, pt);
      Vector3D upHint = debrisUpHint(track, pt);
      // Once a landing piece has impacted, it rests on the ground and must ride the turning globe
      // with its ground track's impact marker, not hang at the frozen inertial pose of the impact
      // instant, which the rotating Earth drifts out from under (PHY-5 / L7 §D3 kept the piece
      // inertial — right for the fall, adrift at rest; docs/bugs.md BUG-28). Position, heading,
      // roll
      // and seat all co-rotate rigidly with the drawn globe.
      if (!within && hasLanded(track)) {
        LandedPose landed = landedPose(pt, seat, upHint, now);
        pt = landed.point();
        seat = landed.seat();
        upHint = landed.upHint();
      }
      int upTo = within ? trail.indexUpTo(now) : trail.size() - 1;
      debrisView.setVisible(true);
      boolean followed = isFollowed(track, focus);
      // Zoomed into this mission: the near frame sits on one of its objects, so a debris'
      // geocentre-relative ground track cancels that large offset erratically and jitters up close
      // (SEL-1 / L2). A landing debris shows its primary-relative ribbon instead while the primary
      // holds the focus — the near frame is then centred on that same primary, so the line cancels
      // it exactly. A followed debris keys on itself; any other focus keeps the ground track.
      boolean primaryFocused =
          focus.getFocusedObject() instanceof FollowedObject.Primary p
              && p.mission().equals(entry.id());
      boolean nearView = followed || primaryFocused;
      boolean relativeDescent = primaryFocused && debrisVisible && hasLanded(track);
      // The followed debris is promoted to the origin anchor — reparented under the near-bodies
      // node
      // and drawn absolutely (no reference point) — so the floating origin, centred on the same
      // point, cancels its position bit-for-bit: it stops jittering, and after impact both read the
      // co-rotated point so it no longer drifts as the Earth turns (SEL-1 / L2, approach A). Every
      // other debris keeps the precise arrangement PHY-5 built: hung under the primary, drawn
      // relative to it.
      debrisView.reparent(
          followed ? context.sceneGraph().nearBodiesNode() : (Node) primary.view().spatial());
      // A debris' descent is drawn the precise way the primary's trajectory is — an inertial ribbon
      // expressed about the object it hangs under, so it cancels the near-frame offset exactly —
      // whenever the near frame is on one of this mission's objects: itself when followed, else the
      // primary when the primary is focused. Its geocentre-relative ground track, which jitters up
      // close, is shown only in the zoomed-out view.
      debrisView.setInertialTrail(followed || relativeDescent);
      updateGroundTrack(i, track, debrisVisible, nearView);
      // Off by default: a debris shows only its close-range 3D mesh; the far-range icon and the
      // ground track come with the global "show debris" toggle (PHY-5 / L7 §D1).
      debrisView.setSecondaryDisplay(debrisVisible);
      debrisView.updateFromPoint(
          pt, seat, upHint, followed ? null : primaryPoint, trail, upTo, cam, tpf, focus);
    }
  }

  /**
   * A booster's roll reference: its separation (fan) direction, so its marked face turns outward
   * toward the flank it occupied on the stack — the orientation it had while mounted (PHY-5 / L7).
   * The seat and the kick share this direction ({@link SeparationImpulse#fanDirection}), so a
   * booster is drawn, seated and pushed off on the one flank. Other debris roll about world up
   * ({@code null}), as any single object does.
   */
  private Vector3D debrisUpHint(DebrisTrack track, MissionEphemerisPoint pt) {
    if (track.role() != StageRole.BOOSTER) {
      return null;
    }
    return SeparationImpulse.fanDirection(
        pt.velocity(), pt.position(), track.exemplarIndex(), boosterCount);
  }

  /**
   * Shows a landing debris' ground track when the "show debris" toggle is on, building it lazily
   * the first time its rotations are available (PHY-5 / L7 §D3). An orbital debris (it never lands)
   * gets none, and a hidden toggle hides it. Whether the near view is centred on Earth at all is a
   * separate, coarser gate owned by {@code PlanetPoseAppState}, which culls the shared frame these
   * tracks hang under when the focus is not the Earth.
   */
  private void updateGroundTrack(
      int index, DebrisTrack track, boolean debrisVisible, boolean nearView) {
    // Hidden in the near view — this debris followed, or its primary focused: up close its
    // geocentre-relative vertices jitter against the near-frame offset, so the debris shows its
    // primary-relative ribbon instead (SEL-1 / L2). Shown for the zoomed-out view, where the jitter
    // is far and invisible.
    boolean wanted = debrisVisible && hasLanded(track) && !nearView;
    DebrisGroundTrackView groundTrack = debrisGroundTracks.get(index);
    if (!wanted) {
      if (groundTrack != null) {
        groundTrack.setVisible(false);
      }
      return;
    }
    if (groundTrack == null) {
      groundTrack = buildGroundTrack(track, index).orElse(null);
      debrisGroundTracks.set(index, groundTrack);
    }
    if (groundTrack != null) {
      groundTrack.setVisible(true);
    }
  }

  private void hideGroundTrack(int index) {
    DebrisGroundTrackView groundTrack = debrisGroundTracks.get(index);
    if (groundTrack != null) {
      groundTrack.setVisible(false);
    }
  }

  /** Whether a debris reaches the ground — its last sample is at (near) zero altitude (§D2). */
  private static boolean hasLanded(DebrisTrack track) {
    return track.ephemeris().lastPoint().altitudeMeters() < LANDED_ALTITUDE_METERS;
  }

  /**
   * The point a debris is <em>drawn</em> at now: its interpolated sample, carried into the globe's
   * current drawn rotation once it has impacted (the same {@link #landedPose} turn), so a landed
   * piece rides the turning Earth. This is what both the render here and the camera's {@code
   * FloatingOriginAppState} read for a followed debris, so the frame is centred on exactly where
   * the debris is drawn — it neither jitters nor drifts as the Earth turns (SEL-1 / L2, approach
   * A).
   *
   * @param ephemeris the debris' display ephemeris
   * @param now the current simulation date
   * @return the sample to draw, co-rotated once landed
   */
  public static MissionEphemerisPoint renderedPointOf(
      MissionEphemeris ephemeris, AbsoluteDate now) {
    MissionEphemerisPoint pt = ephemeris.displayPointAt(now);
    boolean landed =
        now.compareTo(ephemeris.endDate()) > 0
            && ephemeris.lastPoint().altitudeMeters() < LANDED_ALTITUDE_METERS;
    if (!landed) {
      return pt;
    }
    Optional<Quaternion> atImpact = PlanetDrawnRotation.at(SolarSystemBody.EARTH, pt.time());
    Optional<Quaternion> atNow = PlanetDrawnRotation.at(SolarSystemBody.EARTH, now);
    if (atImpact.isEmpty() || atNow.isEmpty()) {
      return pt;
    }
    return new MissionEphemerisPoint(
        pt.time(),
        rotateWithGlobe(pt.position(), atImpact.get(), atNow.get()),
        rotateWithGlobe(pt.velocity(), atImpact.get(), atNow.get()),
        pt.stageName(),
        pt.propulsive(),
        pt.mass(),
        pt.altitudeMeters(),
        pt.arc());
  }

  /**
   * Whether {@code track} is the object the view is currently following (SEL-1 / L2, approach A).
   */
  private boolean isFollowed(DebrisTrack track, FocusView focus) {
    return focus.getFocusedObject() instanceof FollowedObject.Debris debris
        && debris.mission().equals(entry.id())
        && debris.role() == track.role()
        && debris.exemplar() == track.exemplarIndex();
  }

  /** An impacted debris' whole pose, carried into the globe's current drawn rotation. */
  private record LandedPose(MissionEphemerisPoint point, Vector3D seat, Vector3D upHint) {}

  /**
   * Carries a just-landed debris' frozen impact pose into the globe's <em>current</em> drawn
   * rotation, so the whole piece rides the turning Earth with its ground track's impact marker
   * instead of hanging at the inertial pose of the impact instant. Position (a point about the
   * geocentre), velocity and the seat and roll references (directions) are all turned by the same
   * drawn rotation the marker follows, so the mesh stays rigid on the ground: {@code lookAt} and
   * the seat offset are rotation-equivariant, so turning their inputs turns the drawn attitude with
   * them.
   *
   * <p>A {@code null} roll hint (a non-booster, rolling about world up) becomes celestial north
   * made explicit so it can be turned like any other; at {@code now = t_impact} the rotation is
   * identity and the pose is returned unmoved, so there is no jump when the fall ends. Returns the
   * frozen pose unchanged when the drawn rotation is not yet available for either date (PHY-5 / L7
   * §D3, corrected for the post-impact rest; docs/bugs.md BUG-28).
   *
   * @param impact the impact sample (the ephemeris' last point)
   * @param seat the seat offset computed at the impact pose
   * @param upHint the roll reference at the impact pose, or {@code null} for world up
   * @param now the current simulation date
   * @return the pose turned into the globe's current drawn rotation
   */
  private LandedPose landedPose(
      MissionEphemerisPoint impact, Vector3D seat, Vector3D upHint, AbsoluteDate now) {
    Optional<Quaternion> atImpact = PlanetDrawnRotation.at(SolarSystemBody.EARTH, impact.time());
    Optional<Quaternion> atNow = PlanetDrawnRotation.at(SolarSystemBody.EARTH, now);
    if (atImpact.isEmpty() || atNow.isEmpty()) {
      return new LandedPose(impact, seat, upHint);
    }
    Quaternion drawnAtImpact = atImpact.get();
    Quaternion drawnNow = atNow.get();
    MissionEphemerisPoint pinned =
        new MissionEphemerisPoint(
            impact.time(),
            rotateWithGlobe(impact.position(), drawnAtImpact, drawnNow),
            rotateWithGlobe(impact.velocity(), drawnAtImpact, drawnNow),
            impact.stageName(),
            impact.propulsive(),
            impact.mass(),
            impact.altitudeMeters(),
            impact.arc());
    Vector3D roll = upHint != null ? upHint : Vector3D.PLUS_K;
    return new LandedPose(
        pinned,
        rotateWithGlobe(seat, drawnAtImpact, drawnNow),
        rotateWithGlobe(roll, drawnAtImpact, drawnNow));
  }

  /**
   * Turns an ICRF vector — a position about the geocentre or a direction — by the globe's drawn
   * rotation between two dates: de-rotates by {@code atImpact} into the body-fixed frame and
   * re-rotates by {@code atNow}. Uses the same drawn rotation ({@link PlanetDrawnRotation}) the
   * ground track and its impact marker follow, so a landed piece and its marker coincide by
   * construction. The uniform render scale cancels under the rotation, leaving pure axis-mapping
   * and quaternion. Package-private and taking the rotations explicitly (rather than reading {@link
   * PlanetDrawnRotation}) so the frame algebra is testable off the ephemeris runtime, as {@link
   * DebrisGroundTrack} is.
   *
   * @param icrf the vector in ICRF axes (metres for a position, any scale for a direction)
   * @param atImpact the globe's drawn rotation at impact
   * @param atNow the globe's drawn rotation now
   * @return the vector turned by the drawn rotation since impact
   */
  static Vector3D rotateWithGlobe(Vector3D icrf, Quaternion atImpact, Quaternion atNow) {
    AxisConvention axes = RenderContext.planet(SolarSystemBody.EARTH).axisConvention();
    Quaternion drawnSinceImpact = atNow.mult(atImpact.inverse());
    Vector3f rotated = drawnSinceImpact.mult(JmeVectorAdapter.toVector3f(axes.icrfToJme(icrf)));
    return axes.jmeToIcrf(JmeVectorAdapter.toVector3D(rotated));
  }

  /**
   * Builds a landing debris' ground track from its whole fall, in the Earth rotating frame — or
   * empty while any sample's drawn rotation is not yet available, so the caller retries next frame
   * (PHY-5 / L7 §D3).
   */
  private Optional<DebrisGroundTrackView> buildGroundTrack(DebrisTrack track, int index) {
    RenderContext ctx = RenderContext.planet(SolarSystemBody.EARTH);
    List<MissionEphemerisPoint> points = track.ephemeris().allPoints();
    List<Vector3f> gcrfJme = new ArrayList<>(points.size());
    List<AbsoluteDate> times = new ArrayList<>(points.size());
    for (MissionEphemerisPoint point : points) {
      gcrfJme.add(JmeVectorAdapter.toJmeBodyRelativePosition(point.position(), ctx));
      times.add(point.time());
    }
    return DebrisGroundTrack.tryBuild(
            gcrfJme, times, t -> PlanetDrawnRotation.at(SolarSystemBody.EARTH, t))
        .map(
            groundTrack ->
                new DebrisGroundTrackView(
                    context.sceneGraph().earthRotatingFrame(),
                    groundTrack,
                    trajectoryColor,
                    entry.id() + "-debris-" + index));
  }

  /**
   * A jettisoned piece's seat, at the place it detached from (PHY-5 / L6 §D2): a booster on the
   * exact flank it was mounted on (lateral fan, shared with the separation kick — {@link
   * SeparationImpulse#fanDirection}), the upper stage up where it sat within {@code after_s1}, the
   * core at the base (no seat).
   */
  private Vector3D debrisSeat(DebrisTrack track, MissionEphemerisPoint pt) {
    return switch (track.role()) {
      case BOOSTER ->
          StackSeat.offset(
              pt.velocity(),
              pt.position(),
              0.0,
              BOOSTER_LATERAL_SPREAD_METERS,
              track.exemplarIndex(),
              boosterCount);
      case UPPER ->
          StackSeat.offset(
              pt.velocity(), pt.position(), (1.0 - afterS1Fraction) * stackHeightMeters, 0.0, 1, 1);
      case CORE, KICK -> Vector3D.ZERO;
    };
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
    cleanupGroundTracks();
    builtDebris = entry.getDebris();
    silhouette = PrimarySilhouette.from(builtDebris);
    boosterCount = (int) builtDebris.stream().filter(t -> t.role() == StageRole.BOOSTER).count();
    for (int i = 0; i < builtDebris.size(); i++) {
      DebrisTrack track = builtDebris.get(i);
      BodyRenderConfig config = debrisConfig(i, track);
      TrackedObjectView view =
          TrackedObjectView.create(
              context,
              config,
              entry.id() + "-debris-" + i,
              trajectoryColor,
              debrisSelectHandler(track),
              (Node) primary.view().spatial());
      // A debris draws no inertial ribbon: its trajectory is a ground track (landing) or nothing
      // (orbital), decided per frame in updateDebris (PHY-5 / L7 §D3).
      view.setInertialTrail(false);
      debrisViews.add(view);
      debrisGroundTracks.add(null);
    }
  }

  private void cleanupGroundTracks() {
    for (DebrisGroundTrackView groundTrack : debrisGroundTracks) {
      if (groundTrack != null) {
        groundTrack.cleanup();
      }
    }
    debrisGroundTracks.clear();
  }

  /**
   * The render config for one debris: the piece's own mesh derived from the launcher's, its label,
   * a neutral colour, the <em>launcher's</em> own draw scale, and the scale context of the arc it
   * starts in.
   *
   * <p><b>Same radius as the primary, on purpose.</b> Every piece mesh ({@code booster<i>}, {@code
   * core}, {@code S2}) is exported in the launcher's frame — one unit is the full stack, and a
   * piece fills its true fraction of it (measured: a Falcon Heavy booster is 0.65 unit, so 45 m of
   * the 70 m stack; the S2 is 0.21 unit). {@code Model3dView} scales a mesh by the vehicle height
   * and by the mesh's own extent, so feeding it the launcher height draws each piece at its true
   * size, in step with the primary and the {@code after_*} silhouettes. A per-piece radius was the
   * L2 placeholder that drew every piece at one fixed size regardless of the mesh.
   */
  private BodyRenderConfig debrisConfig(int index, DebrisTrack track) {
    RenderContext scale = RenderContext.planet(track.ephemeris().firstPoint().arc().body());
    String debrisMeshPath = modelPath.replace(".gltf", meshSuffixFor(track));
    return new BodyRenderConfig(
        "mission-" + entry.id() + "-debris-" + index,
        labelFor(track),
        trajectoryColor,
        drawnRadiusOf(entry),
        debrisMeshPath,
        scale);
  }

  /**
   * The mesh-path suffix for a jettisoned piece, glued onto the launcher's path — {@code
   * heavy_falcon.gltf} becomes {@code heavy_falcon-booster.gltf}. This is the render layer's own
   * asset mapping; the simulation carries only the role and index.
   *
   * <p><b>All boosters share one {@code -booster} mesh.</b> A block's exemplars are the same
   * physical booster, drawn as standalone debris (each spread to its own flank by the seat, PHY-5 /
   * L6). The per-exemplar meshes {@code -booster<i>} were exported as slices of the full stack and
   * carried each mount's own baked rotation, which drew them mis-oriented (PHY-5 / L7); the single
   * {@code -booster} is a recentered, un-rotated booster in the shared one-unit-stack frame.
   *
   * <p><b>The upper stage leaves as {@code after_s1}, not {@code S2}.</b> The primary flies the
   * {@code after_s1} silhouette (upper stage <em>and</em> fairing) right up to this separation,
   * because the fairing has no jettison of its own (découpage §1). Drawing the debris as the bare
   * {@code S2} would make it 12 m shorter than the remnant it detached from, so the piece would
   * appear to shrink as it separates; drawing it as {@code after_s1} makes it fill exactly the box
   * the remnant occupied, and it peels away seamlessly while the payload is revealed (PHY-5 / L6).
   */
  private static String meshSuffixFor(DebrisTrack track) {
    return switch (track.role()) {
      case BOOSTER -> "-booster.gltf";
      case CORE -> "-core.gltf";
      case UPPER -> "-after_s1.gltf";
      case KICK -> "-core.gltf";
    };
  }

  /** The label a jettisoned piece's icon shows — shared with the telemetry identity line (L2). */
  private static String labelFor(DebrisTrack track) {
    return FollowedObject.debrisLabel(track.role(), track.exemplarIndex());
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
    cleanupGroundTracks();
  }
}
