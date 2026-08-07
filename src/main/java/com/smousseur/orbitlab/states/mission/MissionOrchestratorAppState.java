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
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final Map<MissionId, MissionRenderer> renderers = new LinkedHashMap<>();
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

      MissionRenderer renderer = renderers.get(id);

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

      // Lazy-create renderer on the first visible frame
      if (renderer == null) {
        renderer = createRenderer(entry);
        renderers.put(id, renderer);
      }

      // Visibility rules
      if (now.compareTo(eph.startDate()) < 0) {
        // clock before ephemeris → hide everything
        renderer.setVisible(false);
      } else if (now.compareTo(eph.endDate()) <= 0) {
        // clock within ephemeris → interpolate + partial trail
        MissionEphemerisPoint pt = eph.interpolate(now);
        List<Vector3D> trail = eph.positionsUpTo(now);
        renderer.setVisible(true);
        renderer.updateFromEphemeris(pt, trail, cam, tpf);
      } else {
        // clock after ephemeris → last position + full trail
        MissionEphemerisPoint last = eph.lastPoint();
        List<Vector3D> trail = eph.allPositions();
        renderer.setVisible(true);
        renderer.updateFromEphemeris(last, trail, cam, tpf);
      }
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
          MissionRenderer renderer = renderers.remove(id);
          if (renderer != null) renderer.cleanup();
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
            logger.info(
                "Computation completed for mission '{}' [{}]",
                mission.getName(),
                entry.id().shortForm());
          } catch (Exception e) {
            mission.setStatus(MissionStatus.FAILED);
            logger.error(
                "Computation failed for mission '{}' [{}]",
                mission.getName(),
                entry.id().shortForm(),
                e);
          }
        });
  }

  private MissionRenderer createRenderer(MissionEntry entry) {
    RenderContext renderContext = RenderContext.planet(entry.mission().getObjective().body());
    ColorRGBA color = entry.getColor();
    if (color == null) color = ColorRGBA.Cyan;

    MissionRenderer renderer = new MissionRenderer(entry, context, renderContext, color);
    renderer.initialize();
    logger.info(
        "Renderer created for mission '{}' [{}]",
        entry.mission().getName(),
        entry.id().shortForm());
    return renderer;
  }

  private void cleanupRemovedMissions(Set<MissionId> activeMissionIds) {
    renderers
        .keySet()
        .removeIf(
            id -> {
              if (!activeMissionIds.contains(id)) {
                MissionRenderer renderer = renderers.get(id);
                if (renderer != null) renderer.cleanup();
                resetFocusIfFollowing(id);
                return true;
              }
              return false;
            });
  }

  @Override
  protected void cleanup(Application app) {
    renderers.values().forEach(MissionRenderer::cleanup);
    renderers.clear();
    if (optimizationExecutor != null) {
      optimizationExecutor.shutdownNow();
    }
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
