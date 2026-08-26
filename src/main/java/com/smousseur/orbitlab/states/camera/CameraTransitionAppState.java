package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.TransitionTarget;
import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.CameraTransitionConfig;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.states.mission.MissionRenderer;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Animates the camera between focus states instead of cutting to them.
 *
 * <p>Every way of changing the focus — the {@code R} key, clicking a planet, clicking a mission's
 * spacecraft — goes through one of the {@code requestXxx} methods rather than mutating {@link
 * FocusView} directly. That is what centralises the two guards this state owns: a request while a
 * transition is already in flight is dropped, and so is a request for the focus already in effect.
 *
 * <p><b>The focus state itself is left untouched until the very last frame.</b> {@code FocusView}
 * decides which body {@link FloatingOriginAppState} centres the rendered frame on, and switching it
 * mid-animation would move the ground under the interpolation. Only the pivot and the distance are
 * animated; the mode, body and mission flip over atomically at the end (see {@link #finish}).
 *
 * <p><b>Attach order matters.</b> This state must run before {@link FloatingOriginAppState} so that
 * on the frame the focus flips, the floating origin re-centres the scene within the same frame —
 * see {@link #finish} for why anything else shows a one-frame jump.
 */
public final class CameraTransitionAppState extends BaseAppState {

  private static final Logger LOGGER = LogManager.getLogger(CameraTransitionAppState.class);

  /**
   * Multiple of a body's radius the camera settles at when focusing it. Was inlined in {@code
   * PlanetPoseAppState.onSelectPlanet} before the call site delegated the framing here.
   */
  private static final double PLANET_FOCUS_RADII = 5.0;

  /**
   * Shortest travel, in solar units, that still carries a usable bearing to turn towards. One
   * nanometre of a solar unit is one metre — well under the shortest real transition, a launch pad
   * to the centre of its planet.
   */
  private static final float MIN_TRAVEL_FOR_BEARING = 1e-9f;

  private final ApplicationContext context;
  private final CameraTransitionConfig config;

  private CameraTransition active;

  /**
   * Creates the transition state.
   *
   * @param context the application context, read for the focus view, the scene graph and the orbit
   *     camera
   */
  public CameraTransitionAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
    this.config = Objects.requireNonNull(context.getEngineConfig().cameraTransition(), "config");
  }

  /**
   * Whether a transition is currently playing. Consumed by the orbit camera to ignore mouse input
   * for the duration.
   *
   * @return {@code true} while the camera is animating
   */
  public boolean isActive() {
    return active != null;
  }

  /**
   * Requests a transition back to the whole-system view.
   *
   * @return {@code true} if a transition was started
   */
  public boolean requestSolar() {
    return request(new TransitionTarget.Solar());
  }

  /**
   * Requests a transition to a celestial body.
   *
   * @param body the body to focus
   * @return {@code true} if a transition was started
   */
  public boolean requestPlanet(SolarSystemBody body) {
    return request(new TransitionTarget.Planet(body));
  }

  /**
   * Requests a transition to a mission's spacecraft.
   *
   * @param missionId the mission to follow
   * @param parentBody the body that mission's trajectory is expressed about
   * @return {@code true} if a transition was started
   */
  public boolean requestSpacecraft(MissionId missionId, SolarSystemBody parentBody) {
    return request(new TransitionTarget.Spacecraft(missionId, parentBody));
  }

  /**
   * Drops an in-flight transition heading for a given mission, and puts the camera back where the
   * transition started.
   *
   * <p>Called when that mission disappears. A transition applies its target only on its last frame,
   * so without this a mission deleted mid-flight would still be handed to {@link
   * FocusView#viewSpacecraft} a couple of seconds later — focusing an entry that no longer exists.
   *
   * @param missionId the mission that is going away
   */
  public void cancelIfTargeting(MissionId missionId) {
    CameraTransition transition = active;
    if (transition == null
        || !(transition.target() instanceof TransitionTarget.Spacecraft spacecraft)
        || !spacecraft.missionId().equals(missionId)) {
      return;
    }
    active = null;
    context.focusView().endTransition();
    context.focusView().setCameraDistance(transition.sourceDistance());
    OrbitCameraAppState orbitCam = context.orbitCamera();
    if (orbitCam != null) {
      orbitCam.setOrientation(transition.sourceOrientation());
      orbitCam.clearTarget();
    }
    LOGGER.debug("Camera transition to {} cancelled: the mission is gone", transition.target());
  }

  private boolean request(TransitionTarget target) {
    if (isActive()) {
      LOGGER.debug("Camera transition to {} ignored: one is already playing", target);
      return false;
    }
    if (isCurrentFocus(target)) {
      LOGGER.debug("Camera transition to {} ignored: already focused", target);
      return false;
    }

    FocusView focusView = context.focusView();
    OrbitCameraAppState orbitCam = context.orbitCamera();
    if (orbitCam == null) {
      // No camera to animate yet (requests can only reach us after boot, but the context is filled
      // in by the application and this keeps the state usable in isolation): apply straight away.
      applyFocus(target);
      focusView.setCameraDistance(targetDistance(target));
      return false;
    }

    // Snapshot rather than a live supplier: the source pivot is fixed in the rendered frame, which
    // stays centred on the current focus for the whole transition. Taking the camera's own last
    // pivot rather than assuming the origin is what keeps a panned view from snapping back.
    Vector3f from = orbitCam.pivotWorldPosition();
    Supplier<Vector3f> dst = pivotSupplier(target);
    CameraOrientation currentOrientation = orbitCam.orientation();

    active =
        new CameraTransition(
            target,
            () -> from,
            dst,
            focusView.getCameraDistance(),
            targetDistance(target),
            currentOrientation,
            targetOrientation(target, from, dst.get(), currentOrientation),
            config);

    orbitCam.setTarget(active::currentPivot);
    // Announce the destination before the focus flips, so the rules that decide what the scene
    // shows can already account for it — otherwise everything keyed on the focus catches up in one
    // step on the final frame.
    focusView.beginTransition(target.mode(), target.anchorBody());
    LOGGER.debug("Camera transition to {} started over {} s", target, config.durationSec());
    return true;
  }

  @Override
  public void update(float tpf) {
    CameraTransition transition = active;
    if (transition == null) {
      return;
    }
    transition.advance(tpf);
    context.focusView().setCameraDistance(transition.currentDistance());
    OrbitCameraAppState orbitCam = context.orbitCamera();
    if (orbitCam != null) {
      orbitCam.setOrientation(transition.currentOrientation());
    }
    if (transition.isFinished()) {
      finish(transition);
    }
  }

  /**
   * Hands the view over to the target focus, on the same frame the pivot reached it.
   *
   * <p>The switch is visually free because the two moves cancel. Flipping the focus makes {@link
   * FloatingOriginAppState} translate the far root so the new anchor body lands on the origin,
   * which shifts the whole scene by minus the pivot we just interpolated to; clearing the camera's
   * target on the same frame drops its pivot back to that origin by the same amount. The camera
   * therefore keeps looking at the same point in space, and the scene has not moved relative to it.
   * Both halves are required — losing either one is a one-frame jump of the full distance between
   * the two bodies.
   */
  private void finish(CameraTransition transition) {
    TransitionTarget target = transition.target();
    applyFocus(target);
    context.focusView().endTransition();
    // The exact endpoint, not the interpolated approximation, so a transition always settles on the
    // same distance regardless of how the frames happened to fall.
    context.focusView().setCameraDistance(transition.targetDistance());
    OrbitCameraAppState orbitCam = context.orbitCamera();
    if (orbitCam != null) {
      orbitCam.setOrientation(transition.targetOrientation());
      orbitCam.clearTarget();
    }
    active = null;
    LOGGER.debug("Camera transition to {} finished", target);
  }

  private void applyFocus(TransitionTarget target) {
    FocusView focusView = context.focusView();
    if (target instanceof TransitionTarget.Planet planet) {
      focusView.viewPlanet(planet.body());
    } else if (target instanceof TransitionTarget.Spacecraft spacecraft) {
      focusView.viewSpacecraft(spacecraft.missionId(), spacecraft.parentBody());
    } else {
      focusView.reset();
    }
  }

  private boolean isCurrentFocus(TransitionTarget target) {
    FocusView focusView = context.focusView();
    if (target instanceof TransitionTarget.Planet planet) {
      return focusView.getMode() == ViewMode.PLANET && focusView.getBody() == planet.body();
    }
    if (target instanceof TransitionTarget.Spacecraft spacecraft) {
      return focusView.getMode() == ViewMode.SPACECRAFT
          && spacecraft.missionId().equals(focusView.getFocusedMission());
    }
    return focusView.getMode() == ViewMode.SOLAR;
  }

  /**
   * The distance the camera settles at for a given target, in solar units. These are the values the
   * call sites used to set themselves before delegating here; they are unchanged.
   */
  private float targetDistance(TransitionTarget target) {
    if (target instanceof TransitionTarget.Planet planet) {
      double radius = PlanetRadius.radiusFor(planet.body());
      return (float) (radius * PLANET_FOCUS_RADII / RenderContext.SOLAR_METERS_PER_UNIT);
    }
    if (target instanceof TransitionTarget.Spacecraft) {
      return MissionRenderer.SPACECRAFT_FOCUS_DISTANCE_SOLAR_UNITS;
    }
    return context.getEngineConfig().orbitCamera().defaultDistance();
  }

  /**
   * The orientation the camera settles at, which is also the one it turns to over the first third
   * of the transition.
   *
   * <p>Two rules, because the two kinds of destination want opposite things. Flying <em>to</em>
   * something means putting it straight ahead: the camera takes up station on the near side of the
   * pivot and watches its target grow, which is the whole point of animating the orientation at all
   * — the pivot spends the middle of a transition in empty space, and without this there is nothing
   * to look at there.
   *
   * <p>Flying back <em>out</em> to the solar view has no such destination. Aiming along that travel
   * would end the move in the plane of the ecliptic staring into the Sun, the system seen edge-on;
   * the answer there is simply the orientation the view is meant to be seen from, which is the one
   * {@link CameraOrientation#defaults()} already defines and the camera boots with.
   *
   * @param target what the transition is heading for
   * @param from the pivot the camera starts at
   * @param to the pivot it will end at
   * @param current the camera's orientation right now, kept when the travel is degenerate
   */
  private CameraOrientation targetOrientation(
      TransitionTarget target, Vector3f from, Vector3f to, CameraOrientation current) {
    if (target instanceof TransitionTarget.Solar) {
      return CameraOrientation.defaults();
    }
    Vector3f travel = to.subtract(from);
    if (travel.length() < MIN_TRAVEL_FOR_BEARING) {
      // Nothing to align on — the two pivots coincide. Happens when a mission's trajectory is not
      // available and its pivot falls back to its parent, which may be the body already focused.
      return current;
    }
    return CameraOrientation.facingAlong(travel);
  }

  private Supplier<Vector3f> pivotSupplier(TransitionTarget target) {
    if (target instanceof TransitionTarget.Spacecraft spacecraft) {
      return () -> spacecraftPivot(spacecraft);
    }
    SolarSystemBody body = target.anchorBody();
    return () -> bodyPivot(body);
  }

  /**
   * Where a body sits in the rendered frame, in solar units.
   *
   * <p>Derived from the scene graph's <em>local</em> translations rather than read back as a world
   * translation, for two reasons. The local values are the ones {@code PlanetPoseAppState} has
   * already written this frame, whereas world transforms are only refreshed after every state has
   * run, so reading them here would lag by a frame. And the offset {@link FloatingOriginAppState}
   * applies is fully determined by the focus, which is frozen for the duration of a transition: the
   * far root carries minus the focused body's own position, so subtracting it here reproduces
   * exactly what will be drawn — the focused body at the origin, everything else around it.
   */
  private Vector3f bodyPivot(SolarSystemBody body) {
    return bodyLocal(body).subtractLocal(bodyLocal(context.focusView().getBody()));
  }

  /**
   * Where a mission's spacecraft sits in the rendered frame, in solar units: its parent body, plus
   * its own body-relative offset converted down from the planet scale it is stored at.
   *
   * <p>The offset is not negligible and cannot be folded into the parent's position: a LEO
   * spacecraft is some 7000 km from the Earth's centre, four orders of magnitude more than the
   * {@code 5e-7} solar units the camera settles at. Aiming at the parent instead would fly the
   * camera into the centre of the planet and only reveal the spacecraft on the final frame.
   *
   * <p>Falls back to the parent body while the trajectory is unavailable (a mission being
   * recomputed), which is the same degradation {@code FloatingOriginAppState} accepts.
   */
  private Vector3f spacecraftPivot(TransitionTarget.Spacecraft target) {
    MissionEntry entry = context.missionContext().findMission(target.missionId()).orElse(null);
    MissionEphemeris ephemeris = entry == null ? null : entry.getEphemeris().orElse(null);
    if (ephemeris == null) {
      return bodyPivot(target.parentBody());
    }
    // Both halves come from the sample, and the pair is what makes this correct: the offset is
    // expressed about the arc's body, so the body it is added to has to be that same one. Taking
    // the parent captured at click time instead left the two disagreeing by the whole Earth-Moon
    // distance for a spacecraft that had crossed into the lunar sphere of influence since
    // (spec docs/multi-corps/07-conception-L5.md §5.2).
    MissionEphemerisPoint point = ephemeris.displayPointAt(context.clock().now());
    SolarSystemBody arcBody = point.arc().body();
    Vector3f planetUnits =
        JmeVectorAdapter.toJmeBodyRelativePosition(point.position(), RenderContext.planet(arcBody));
    return bodyPivot(arcBody)
        .addLocal(planetUnits.divideLocal((float) RenderContext.ratioSolarToPlanetPerUnit()));
  }

  /** The body anchor's position under the far bodies node, or the origin if it has none yet. */
  private Vector3f bodyLocal(SolarSystemBody body) {
    SceneGraph sceneGraph = context.sceneGraph();
    Spatial spatial = body == null ? null : sceneGraph.getBodySpatial(body);
    return spatial == null ? new Vector3f() : spatial.getLocalTranslation().clone();
  }

  @Override
  protected void initialize(Application app) {}

  @Override
  protected void cleanup(Application app) {
    active = null;
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
