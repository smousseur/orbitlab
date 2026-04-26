package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
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
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * Application state that orchestrates the lifecycle and rendering of all missions in the {@link
 * MissionContext}. For each mission, this state manages: computation submission, ephemeris-based
 * rendering with visibility rules, and cleanup.
 */
public final class MissionOrchestratorAppState extends BaseAppState {
  private static final Logger logger = LogManager.getLogger(MissionOrchestratorAppState.class);

  private static final ColorRGBA[] TRAJECTORY_PALETTE = {
    ColorRGBA.Cyan,
    ColorRGBA.Yellow,
    ColorRGBA.Green,
    ColorRGBA.Orange,
    ColorRGBA.Magenta,
    ColorRGBA.Pink
  };

  private final ApplicationContext context;
  private final Map<String, MissionRenderer> renderers = new LinkedHashMap<>();
  private ExecutorService optimizationExecutor;
  private int colorIndex = 0;

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
    Set<String> activeMissionNames = new HashSet<>();

    for (MissionEntry entry : context.missionContext().getMissions()) {
      String name = entry.mission().getName();
      activeMissionNames.add(name);

      MissionRenderer renderer = renderers.get(name);

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
        renderers.put(name, renderer);
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

    cleanupRemovedMissions(activeMissionNames);
  }

  private void pollMissionActions() {
    EventBus bus = context.eventBus();
    EventBus.MissionActionRequest request;
    while ((request = bus.pollMissionAction()) != null) {
      String name = request.missionName();
      switch (request.action()) {
        case OPTIMIZE ->
            context.missionContext().findMission(name).ifPresent(this::submitForComputation);
        case TOGGLE_VISIBLE ->
            context
                .missionContext()
                .findMission(name)
                .ifPresent(
                    entry -> {
                      if (entry.mission().getStatus() != MissionStatus.READY) {
                        return;
                      }
                      boolean turningOn = !entry.isVisible();
                      if (turningOn) {
                        for (MissionEntry other : context.missionContext().getMissions()) {
                          if (other != entry && other.isVisible()) {
                            other.setVisible(false);
                          }
                        }
                      }
                      entry.setVisible(turningOn);
                    });
        case DELETE -> {
          MissionRenderer renderer = renderers.remove(name);
          if (renderer != null) renderer.cleanup();
          context.missionContext().removeMission(name);
          resetFocusIfFollowing(name);
          logger.info("Mission '{}' deleted", name);
        }
      }
    }
  }

  private void resetFocusIfFollowing(String missionName) {
    if (missionName.equals(context.focusView().getFocusedMission())) {
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
            logger.info("Starting computation for mission '{}'", mission.getName());
            AbsoluteDate launchDate = entry.getScheduledDate().orElseGet(context.clock()::now);
            entry.setScheduledDate(launchDate);
            SpacecraftState initialState = mission.getInitialState(launchDate);
            mission.setCurrentState(initialState);
            mission.setInitialDate(launchDate);

            MissionOptimizer optimizer = new MissionOptimizer(mission);
            MissionComputeResult result = optimizer.optimize();

            entry.setOptimizerResult(result.optimizerResult());
            entry.setEphemeris(result.ephemeris());
            // Status already set to READY by optimizer
            logger.info("Computation completed for mission '{}'", mission.getName());
          } catch (Exception e) {
            mission.setStatus(MissionStatus.FAILED);
            logger.error("Computation failed for mission '{}'", mission.getName(), e);
          }
        });
  }

  private MissionRenderer createRenderer(MissionEntry entry) {
    RenderContext renderContext =
        RenderContext.planet(entry.mission().getObjective().body());
    ColorRGBA color = TRAJECTORY_PALETTE[colorIndex % TRAJECTORY_PALETTE.length];
    colorIndex++;

    MissionRenderer renderer = new MissionRenderer(entry, context, renderContext, color);
    renderer.initialize();
    logger.info("Renderer created for mission '{}'", entry.mission().getName());
    return renderer;
  }

  private void cleanupRemovedMissions(Set<String> activeMissionNames) {
    renderers
        .keySet()
        .removeIf(
            name -> {
              if (!activeMissionNames.contains(name)) {
                MissionRenderer renderer = renderers.get(name);
                if (renderer != null) renderer.cleanup();
                resetFocusIfFollowing(name);
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
