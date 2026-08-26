package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.states.mission.MissionRenderer;
import java.util.Objects;

/**
 * Application state that implements a floating-origin technique to prevent floating-point precision
 * issues when rendering objects at large distances from the world origin.
 *
 * <p>Each frame, this state translates the solar system root node so that the currently focused
 * body sits at the world origin. In solar view mode, the root stays at the origin; in planet view
 * mode, the focused planet's position is negated and applied to the root, effectively centering the
 * scene on that planet. In spacecraft view mode it additionally offsets the near frame onto the
 * focused spacecraft.
 *
 * <p><b>This state is the sole owner of both frame offsets, and must run before anything that reads
 * a world position out of the scene graph</b> — see {@link #nearFrameOffset} for what breaks
 * otherwise.
 */
public class FloatingOriginAppState extends BaseAppState {
  /** Minimum far plane for the farCam in planet view mode, in solar-scale units. */
  private static final float PLANET_MODE_FAR_MIN = 50_000f;

  private final ApplicationContext context;
  private final Node solarRoot;
  private final SceneGraph sceneGraph;
  private OrbitCameraAppState orbitCam;

  /**
   * Last near-frame offset that could be computed, kept as the fallback for the frames where it
   * cannot. A focused mission loses its ephemeris while it is being recomputed; snapping the offset
   * back to zero for those frames would drop the near-view origin onto the centre of the Earth and
   * put the camera inside the planet. Holding the last known position leaves the view where the
   * user left it until the new trajectory lands.
   */
  private final Vector3f lastNearOffset = new Vector3f();

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
      }
      case PLANET -> {
        sceneGraph.setSolarVisible(true);
        sceneGraph.nearFrame().setLocalTranslation(0f, 0f, 0f);
        sceneGraph.showBodySpatial(view.getBody());
        Spatial planetSpatial = sceneGraph.getBodySpatial(view.getBody());
        if (planetSpatial != null) {
          solarRoot.setLocalTranslation(planetSpatial.getLocalTranslation().negate());
        }
      }
      case SPACECRAFT -> {
        // Keep the far scene visible so that orbit lines of other planets remain drawn.
        // The 2D HUD icons are handled independently by PlanetHudMarkersAppState.
        sceneGraph.setSolarVisible(true);

        // One point, read once, and everything below derives from it: the body the scene is
        // centred on and the offset that puts the spacecraft on the origin cannot disagree
        // because they are two readings of the same object (spec L3 §3.1).
        MissionEphemerisPoint point = displayPoint(view.getFocusedMission());
        SolarSystemBody renderBody =
            point == null ? view.getBody() : MissionRenderer.renderBodyOf(point, view);

        // The near viewport holds exactly one globe, parked on the origin of nearBodiesNode, and
        // it has to be the body the spacecraft's coordinates are about — otherwise the Earth is
        // drawn where the Moon should be, 1 837 km from a spacecraft at perilune (spec
        // docs/multi-corps/07-conception-L5.md §3.2). That constraint is also the mechanism: the
        // globe then lands at |p| from the spacecraft, which is exactly where it belongs.
        sceneGraph.showBodySpatial(renderBody);

        // Keep the far root centered on that same body — the projection camera used by
        // PlanetHudMarkersAppState is a clone of the far camera and needs it at the origin
        // to place the other planet icons correctly around it.
        Spatial planetSpatial = sceneGraph.getBodySpatial(renderBody);
        if (planetSpatial != null) {
          solarRoot.setLocalTranslation(planetSpatial.getLocalTranslation().negate());
        }

        // Offset the near frame so the spacecraft sits at the near-view origin. Both of the frame's
        // children cancel this offset with the matching +p: the spacecraft anchor under
        // nearBodiesNode, and the trajectory line under nearOrbitsNode, which carries it on the
        // geometry itself so its vertices can stay spacecraft-relative.
        sceneGraph.nearFrame().setLocalTranslation(nearFrameOffset(point, renderBody));
      }
    }

    // Keep the far frustum large enough to encompass distant orbits and bodies whenever either end
    // of the view is planet scale — the destination of a transition counts, and that is the whole
    // point of asking FocusView rather than switching on the mode above.
    //
    // The far plane otherwise tracks the camera distance, which a transition drives down by four
    // orders of magnitude while the mode is still SOLAR: it would collapse to its 10-unit absolute
    // minimum somewhere around the middle of a fly-in, sweeping the Sun and every orbit line out of
    // the far viewport one by one, and drop them all back in when the mode finally flipped. The
    // cost
    // is a coarser depth range in the far viewport for the 2.5 s a transition lasts, which that
    // viewport already lives with permanently in planet view.
    orbitCam.setFarFloor(view.isPlanetScale() ? PLANET_MODE_FAR_MIN : 0f);
  }

  /**
   * Translation to apply to the near frame so that the focused spacecraft lands on the near-view
   * origin: the negation of its position at the current instant, in near render units. Falls back
   * to {@link #lastNearOffset} when no mission is focused or its trajectory is not available.
   *
   * <p><b>Why this reads the ephemeris and not the spacecraft's scene anchor.</b> It used to read
   * the anchor, which made this state a <em>consumer</em> of what {@code
   * MissionOrchestratorAppState} writes, and therefore forced it to run after the orchestrator. But
   * the orchestrator itself reads a world position back out of the graph — {@code
   * LodView.updateScreen} measures the camera-to-spacecraft distance to pick the level of detail —
   * and at that point the near frame still carried the <em>previous</em> frame's offset. The
   * spacecraft therefore measured as {@code p(t) − p(t−1)} away from the origin instead of zero:
   * harmless at ×1, but at ×300 that is some 40 km, the projected radius falls under the LOD
   * threshold, and the 3D model silently drops to its 2D icon. Rendering was never wrong — only the
   * measurement was, and only because it happened too early.
   *
   * <p>So this state no longer consumes the graph: it derives the offset from the same ephemeris
   * the orchestrator will read, and runs <em>before</em> it (see the attach order in {@code
   * OrbitLabApplication}). Both go through {@link MissionEphemeris#displayPointAt} at the same date
   * and {@link JmeVectorAdapter#toJmeBodyRelativePosition} with the same context, so the offset and
   * the anchor stay bit-for-bit opposite and cancel exactly.
   *
   * @param point the focused mission's sample at the current instant, may be {@code null}
   * @param renderBody the body the near scene is centred on, derived from that same point
   * @return the near-frame translation, never {@code null}
   */
  private Vector3f nearFrameOffset(MissionEphemerisPoint point, SolarSystemBody renderBody) {
    if (point == null) {
      return lastNearOffset;
    }
    // negateLocal, not negate: the anchor is placed at the un-negated value of the very same
    // conversion, so the two floats must stay exact opposites (cf. toJmeBodyRelativePosition).
    return lastNearOffset.set(
        JmeVectorAdapter.toJmeBodyRelativePosition(
                MissionRenderer.renderPositionOf(point, renderBody),
                RenderContext.planet(renderBody))
            .negateLocal());
  }

  /**
   * The focused mission's sample at the current instant, or {@code null} when there is no mission
   * or its trajectory is being recomputed.
   *
   * <p>Read here rather than inside {@link #nearFrameOffset} because the body the scene is centred
   * on is derived from the same sample: two lookups could, on the frame a trajectory is replaced,
   * return points from different arcs and centre the scene on one body while offsetting it by the
   * other's coordinates.
   *
   * @param missionId the focused mission, may be {@code null}
   * @return the sample, or {@code null}
   */
  private MissionEphemerisPoint displayPoint(MissionId missionId) {
    MissionEntry entry =
        missionId == null ? null : context.missionContext().findMission(missionId).orElse(null);
    MissionEphemeris ephemeris = entry == null ? null : entry.getEphemeris().orElse(null);
    return ephemeris == null ? null : ephemeris.displayPointAt(context.clock().now());
  }

  @Override
  protected void cleanup(Application app) {}

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
