package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * Application state that orchestrates the lifecycle and rendering of all missions in the {@link
 * MissionContext}. For each mission, this state manages: computation submission, ephemeris-based
 * rendering with visibility rules, and cleanup.
 */
public final class MissionOrchestratorAppState extends BaseAppState {
  private static final Logger logger = LogManager.getLogger(MissionOrchestratorAppState.class);

  private final ApplicationContext context;
  private ExecutorService optimizationExecutor;

  public MissionOrchestratorAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    optimizationExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "mission-optimizer");
              t.setDaemon(true);
              return t;
            });
  }

  @Override
  public void update(float tpf) {
    pollMissionActions();

    AbsoluteDate now = context.clock().now();
    Camera cam = context.nearCamera();
    Set<MissionId> activeMissionIds = new HashSet<>();

    for (MissionEntry entry : context.missionContext().getMissions()) {
      MissionId id = entry.id();
      activeMissionIds.add(id);

      MissionRenderer renderer = context.getMissionRenderer(id);

      // Only render if READY + visible
      if (entry.mission().getStatus() != MissionStatus.READY || !entry.isVisible()) {
        if (renderer != null) renderer.setVisible(false);
        continue;
      }

      MissionEphemeris eph = entry.getEphemeris().orElse(null);
      if (eph == null) {
        if (renderer != null) renderer.setVisible(false);
        continue;
      }

      // A wizard edit can swap the launcher, and the 3D mesh is baked into the renderer at
      // initialize(). The entry keeps its identity through such an edit, so nothing else would
      // rebuild the renderer and the mission would keep flying the previous vehicle's shape.
      if (renderer != null && !renderer.modelPath().equals(MissionRenderer.modelPathFor(entry))) {
        disposeRenderer(id);
        renderer = null;
      }

      // Lazy-create renderer on the first visible frame
      if (renderer == null) {
        renderer = createRenderer(entry);
      }

      // Visibility rules
      if (now.compareTo(eph.startDate()) < 0) {
        // clock before ephemeris → hide everything
        renderer.setVisible(false);
        continue;
      }

      // A mission is drawn in the planet-scale context of the body its objective targets, so it
      // only belongs on screen while the camera is looking at that body — not in the solar view,
      // and not while focusing another body that shares the same near viewport, the Moon being the
      // case that surfaced this. Deciding it here rather than inside the renderer is what makes the
      // rule cover the trajectory line as well: setVisible() hides both, whereas the mode test that
      // used to live in updateFromEphemeris() hid the spacecraft alone and left the line drawn.
      if (!context.focusView().isMissionVisible(entry.mission().getObjective().body())) {
        renderer.setVisible(false);
        continue;
      }

      // Within the ephemeris the point is interpolated and the trail stops at the current instant;
      // past its end the mission is over, so both settle on the last sample. The trail itself is
      // the same shared, pre-decimated polyline in both cases — nothing is allocated here, where
      // the previous code built a fresh list of up to 86 400 positions per frame and per mission.
      TrajectoryPolyline trail = eph.displayTrail();
      boolean within = now.compareTo(eph.endDate()) <= 0;
      MissionEphemerisPoint pt = eph.displayPointAt(now);
      int upTo = within ? trail.indexUpTo(now) : trail.size() - 1;

      renderer.setVisible(true);
      renderer.updateFromEphemeris(pt, trail, upTo, cam, tpf);
    }

    cleanupRemovedMissions(activeMissionIds);
  }

  private void pollMissionActions() {
    EventBus bus = context.eventBus();
    EventBus.MissionActionRequest request;
    while ((request = bus.pollMissionAction()) != null) {
      MissionId id = request.missionId();
      switch (request.action()) {
        case OPTIMIZE ->
            context.missionContext().findMission(id).ifPresent(this::submitForComputation);
        case TOGGLE_VISIBLE ->
            context
                .missionContext()
                .findMission(id)
                .ifPresent(
                    entry -> {
                      if (entry.mission().getStatus() != MissionStatus.READY) {
                        return;
                      }
                      boolean turningOn = !entry.isVisible();
                      entry.setVisible(turningOn);
                      if (!turningOn
                          && id.equals(context.missionContext().getTelemetryFocusMissionId())) {
                        context.missionContext().setTelemetryFocusMissionId(null);
                      }
                    });
        case DELETE -> {
          disposeRenderer(id);
          context.missionContext().removeMission(id);
          resetFocusIfFollowing(id);
          logger.info("Mission [{}] deleted", id.shortForm());
        }
      }
    }
  }

  private void resetFocusIfFollowing(MissionId missionId) {
    if (missionId.equals(context.focusView().getFocusedMission())) {
      context.focusView().reset();
    }
  }

  private void submitForComputation(MissionEntry entry) {
    Mission mission = entry.mission();
    mission.setStatus(MissionStatus.COMPUTING);
    entry.setEphemeris(null); // invalidate previous
    // Cleared on entry rather than on success: a mission relaunched after a failure would otherwise
    // keep displaying the previous error for the whole duration of the new computation.
    entry.clearLastError();

    optimizationExecutor.submit(
        () -> {
          try {
            logger.info(
                "Starting computation for mission '{}' [{}]",
                mission.getName(),
                entry.id().shortForm());
            AbsoluteDate launchDate = entry.getScheduledDate().orElseGet(context.clock()::now);
            entry.setScheduledDate(launchDate);

            // The planner is selected from the entry's optimization mode; FAST reproduces the
            // legacy fixed-load path (analytic composition, single CMA-ES pass at budgeted loads).
            MissionComputeResult result =
                new MissionPlanOptimizer(entry, launchDate).compute().computation();

            // Adopt the mission actually flown: for a fixed-load run it is the entry's own mission;
            // for PRECISE it is the sizing sweep's winning internal mission (scaled loads, solved
            // stages), already optimized and marked READY by the optimizer.
            entry.setMission(result.mission());
            entry.setOptimizerResult(result.optimizerResult());
            entry.setEphemeris(result.ephemeris());
            // Reporting-only, but the reason MissionComputeResult carries them rather than merely
            // logging them: the panel displays both without recomputing anything.
            entry.setAchievedOrbit(result.achievedOrbit());
            entry.setPerformanceReport(result.performanceReport());
            logger.info(
                "Computation completed for mission '{}' [{}]",
                mission.getName(),
                entry.id().shortForm());
          } catch (Exception e) {
            // Status first, error last. Mission.status is a plain field, so the volatile write to
            // lastError is the only publication edge this thread has: a render thread that sees the
            // error is then guaranteed to see FAILED too. Reversed, it could read the new error
            // beside a stale COMPUTING and display "Computing..." forever.
            mission.setStatus(MissionStatus.FAILED);
            entry.setLastError(MissionEntry.describeFailure(e));
            logger.error(
                "Computation failed for mission '{}' [{}]",
                mission.getName(),
                entry.id().shortForm(),
                e);
          }
        });
  }

  private MissionRenderer createRenderer(MissionEntry entry) {
    RenderContext renderContext = MissionRenderer.renderContextFor(entry);
    ColorRGBA color = entry.getColor();
    if (color == null) color = ColorRGBA.Cyan;

    MissionRenderer renderer = new MissionRenderer(entry, context, renderContext, color);
    renderer.initialize();
    // The context registry is the single source of truth for live renderers: register here, right
    // after creation, and deregister in disposeRenderer(). Nothing else writes to it.
    context.addMissionRenderer(entry.id(), renderer);
    logger.info(
        "Renderer created for mission '{}' [{}]",
        entry.mission().getName(),
        entry.id().shortForm());
    return renderer;
  }

  /**
   * Deregisters the renderer bound to a mission id and detaches its visuals. No-op if no renderer
   * was created for that mission.
   *
   * @param missionId the mission whose renderer must be disposed
   */
  private void disposeRenderer(MissionId missionId) {
    MissionRenderer renderer = context.removeMissionRenderer(missionId);
    if (renderer != null) renderer.cleanup();
  }

  private void cleanupRemovedMissions(Set<MissionId> activeMissionIds) {
    // Snapshot the ids: disposeRenderer() mutates the registry we are walking.
    List<MissionId> staleIds =
        context.missionRenderers().keySet().stream()
            .filter(id -> !activeMissionIds.contains(id))
            .toList();
    for (MissionId id : staleIds) {
      disposeRenderer(id);
      resetFocusIfFollowing(id);
    }
  }

  @Override
  protected void cleanup(Application app) {
    List.copyOf(context.missionRenderers().keySet()).forEach(this::disposeRenderer);
    if (optimizationExecutor != null) {
      optimizationExecutor.shutdownNow();
    }
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
