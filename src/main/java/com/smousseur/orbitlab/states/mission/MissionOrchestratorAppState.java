package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizerResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPlayer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Application state that orchestrates the lifecycle and rendering of all missions in the {@link
 * MissionContext}. Replaces the former {@code SpacecraftDisplayAppState} and {@code
 * SpacecraftTrajectoryAppState}.
 *
 * <p>For each mission, this state manages: optimization submission, player preparation, renderer
 * creation, per-frame updates, and cleanup. Missions are started explicitly via {@link
 * #startMission(MissionEntry)}.
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

  /**
   * Creates a new mission orchestrator state.
   *
   * @param context the application context
   */
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

    MissionContext missionContext = context.missionContext();
    Camera cam = getApplication().getCamera();

    // Track which missions are still in the context
    Set<String> activeMissionNames = new HashSet<>();

    for (MissionEntry entry : missionContext.getMissions()) {
      String name = entry.mission().getName();
      activeMissionNames.add(name);
      MissionStatus status = entry.mission().getStatus();

      switch (status) {
        case DRAFT, OPTIMIZING, FAILED -> {
          // Nothing to render
        }
        case READY -> {
          if (!entry.isPlayerPrepared()) {
            prepareMission(entry);
          }
        }
        case RUNNING -> {
          MissionRenderer renderer = renderers.get(name);
          if (renderer != null) {
            renderer.update(tpf, cam);
          }
        }
        case COMPLETED -> {
          // Renderer stays visible but no propagation
        }
      }
    }

    // Cleanup renderers for removed missions
    renderers
        .keySet()
        .removeIf(
            name -> {
              if (!activeMissionNames.contains(name)) {
                MissionRenderer renderer = renderers.get(name);
                if (renderer != null) {
                  renderer.cleanup();
                }
                return true;
              }
              return false;
            });
  }

  private void pollMissionActions() {
    EventBus bus = context.eventBus();
    EventBus.MissionActionRequest request;
    while ((request = bus.pollMissionAction()) != null) {
      String name = request.missionName();
      switch (request.action()) {
        case OPTIMIZE ->
            context.missionContext().findMission(name).ifPresent(this::submitForOptimization);
        case START -> context.missionContext().findMission(name).ifPresent(this::startMission);
        case DELETE -> {
          MissionRenderer renderer = renderers.remove(name);
          if (renderer != null) {
            renderer.cleanup();
          }
          context.missionContext().removeMission(name);
          logger.info("Mission '{}' deleted", name);
        }
      }
    }
  }

  /**
   * Submits a mission for background optimization. Sets the mission status to {@link
   * MissionStatus#OPTIMIZING} and runs the optimizer on a background thread. On completion, the
   * status is set to {@link MissionStatus#READY} or {@link MissionStatus#FAILED}.
   *
   * @param entry the mission entry to optimize
   */
  public void submitForOptimization(MissionEntry entry) {
    entry.mission().setStatus(MissionStatus.OPTIMIZING);
    optimizationExecutor.submit(
        () -> {
          try {
            MissionOptimizer optimizer = new MissionOptimizer(entry.mission());
            MissionOptimizerResult result = optimizer.optimize();
            entry.setOptimizerResult(result);
            entry.mission().setStatus(MissionStatus.READY);
            logger.info("Optimization completed for mission '{}'", entry.mission().getName());
          } catch (Exception e) {
            entry.mission().setStatus(MissionStatus.FAILED);
            logger.error("Optimization failed for mission '{}'", entry.mission().getName(), e);
          }
        });
  }

  /**
   * Starts a mission that has been prepared (READY + playerPrepared). Sets the status to {@link
   * MissionStatus#RUNNING} and calls {@code mission.start()}.
   *
   * @param entry the mission entry to start
   * @throws IllegalStateException if the mission is not in READY state or not prepared
   */
  public void startMission(MissionEntry entry) {
    if (entry.mission().getStatus() != MissionStatus.READY || !entry.isPlayerPrepared()) {
      throw new IllegalStateException(
          "Mission '" + entry.mission().getName() + "' is not ready to start");
    }
    entry.mission().start(context.clock().now());
    logger.info("Mission '{}' started", entry.mission().getName());
  }

  private void prepareMission(MissionEntry entry) {
    entry
        .getOptimizerResult()
        .ifPresent(
            result -> {
              MissionPlayer player = new MissionPlayer(entry.mission());
              player.play(result, context.clock().now());
              entry.setPlayerPrepared(true);

              // Create renderer
              RenderContext renderContext = RenderContext.planet(context.focusView().getBody());
              ColorRGBA color = TRAJECTORY_PALETTE[colorIndex % TRAJECTORY_PALETTE.length];
              colorIndex++;

              MissionRenderer renderer = new MissionRenderer(entry, context, renderContext, color);
              renderer.initialize(getApplication());
              renderers.put(entry.mission().getName(), renderer);

              logger.info("Mission '{}' prepared and renderer created", entry.mission().getName());
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
