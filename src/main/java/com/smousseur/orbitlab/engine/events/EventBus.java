package com.smousseur.orbitlab.engine.events;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.orbit.OrbitPath;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Application-wide thread-safe event bus for asynchronous inter-state communication.
 *
 * <p>Producer: any thread (UI widgets, background workers).
 *
 * <p>Consumer: typically JME render thread (drain in AppState.update()).
 */
public final class EventBus {

  // -------------------------------------------------------------------------
  // Orbit events
  // -------------------------------------------------------------------------

  /**
   * Event indicating that an orbit path has been computed and is ready for rendering.
   *
   * @param body the solar system body whose orbit was computed
   * @param path the computed orbit path
   */
  public record OrbitPathReady(SolarSystemBody body, OrbitPath path) {
    public OrbitPathReady {
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(path, "path");
    }
  }

  private final ConcurrentLinkedQueue<OrbitPathReady> orbitPathReadyQueue =
      new ConcurrentLinkedQueue<>();

  /**
   * Publishes an orbit path ready event. Can be called from any thread.
   *
   * @param body the solar system body whose orbit path is ready
   * @param path the computed orbit path
   */
  public void publishOrbitPathReady(SolarSystemBody body, OrbitPath path) {
    orbitPathReadyQueue.add(new OrbitPathReady(body, path));
  }

  /** Poll one orbit path event; returns null if none. */
  public OrbitPathReady pollOrbitPathReady() {
    return orbitPathReadyQueue.poll();
  }

  // -------------------------------------------------------------------------
  // Mission action events
  // -------------------------------------------------------------------------

  /** Actions that can be requested on a mission from the UI. */
  public enum MissionAction {
    OPTIMIZE,
    TOGGLE_VISIBLE,
    DELETE
  }

  /**
   * Request to perform an action on a mission, published by the UI and consumed by the
   * orchestrator.
   *
   * @param missionName the name of the target mission
   * @param action the action to perform
   */
  public record MissionActionRequest(String missionName, MissionAction action) {
    public MissionActionRequest {
      Objects.requireNonNull(missionName, "missionName");
      Objects.requireNonNull(action, "action");
    }
  }

  private final ConcurrentLinkedQueue<MissionActionRequest> missionActionQueue =
      new ConcurrentLinkedQueue<>();

  /**
   * Publishes a mission action request. Can be called from any thread.
   *
   * @param missionName the name of the target mission
   * @param action the action to perform
   */
  public void publishMissionAction(String missionName, MissionAction action) {
    missionActionQueue.add(new MissionActionRequest(missionName, action));
  }

  /** Poll one mission action request; returns null if none. */
  public MissionActionRequest pollMissionAction() {
    return missionActionQueue.poll();
  }

  // -------------------------------------------------------------------------
  // UI navigation events
  // -------------------------------------------------------------------------

  /**
   * UI navigation events, published by widgets and consumed by AppStates. Each concrete event type
   * has its own queue so independent consumers do not race to drain a shared queue.
   */
  public sealed interface UiNavigationEvent
      permits UiNavigationEvent.OpenMissionWizard,
          UiNavigationEvent.CreateMission,
          UiNavigationEvent.OpenMissionManagement {

    /** Request to open the mission wizard. */
    record OpenMissionWizard() implements UiNavigationEvent {}

    /**
     * Request to create a mission from the wizard form values.
     *
     * @param values aggregated form values keyed by {@code FormField.key()}
     */
    record CreateMission(Map<String, Object> values) implements UiNavigationEvent {
      public CreateMission {
        Objects.requireNonNull(values, "values");
        values = Map.copyOf(values);
      }
    }

    /** Request to open the mission management modal. */
    record OpenMissionManagement() implements UiNavigationEvent {}
  }

  private final ConcurrentLinkedQueue<UiNavigationEvent.OpenMissionWizard> openWizardQueue =
      new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<UiNavigationEvent.CreateMission> createMissionQueue =
      new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<UiNavigationEvent.OpenMissionManagement> openManagementQueue =
      new ConcurrentLinkedQueue<>();

  /**
   * Publishes a UI navigation event. Routes to the per-type queue. Can be called from any thread.
   *
   * @param event the navigation event
   */
  public void publishUiNavigation(UiNavigationEvent event) {
    Objects.requireNonNull(event);
    switch (event) {
      case UiNavigationEvent.OpenMissionWizard w -> openWizardQueue.add(w);
      case UiNavigationEvent.CreateMission c -> createMissionQueue.add(c);
      case UiNavigationEvent.OpenMissionManagement m -> openManagementQueue.add(m);
    }
  }

  /** Poll one open-wizard request; returns null if none. */
  public UiNavigationEvent.OpenMissionWizard pollOpenWizard() {
    return openWizardQueue.poll();
  }

  /** Poll one create-mission request; returns null if none. */
  public UiNavigationEvent.CreateMission pollCreateMission() {
    return createMissionQueue.poll();
  }

  /** Poll one open-management request; returns null if none. */
  public UiNavigationEvent.OpenMissionManagement pollOpenManagement() {
    return openManagementQueue.poll();
  }

  // -------------------------------------------------------------------------
  // Mission telemetry focus events
  // -------------------------------------------------------------------------

  /**
   * Request to change the telemetry focus.
   *
   * @param missionName the mission to follow, or {@code null} to clear the focus
   */
  public record MissionTelemetryFocusRequest(String missionName) {}

  private final ConcurrentLinkedQueue<MissionTelemetryFocusRequest> telemetryFocusQueue =
      new ConcurrentLinkedQueue<>();

  /**
   * Publishes a telemetry focus request. Can be called from any thread.
   *
   * @param missionName the target mission, or {@code null} to clear the focus
   */
  public void publishTelemetryFocus(String missionName) {
    telemetryFocusQueue.add(new MissionTelemetryFocusRequest(missionName));
  }

  /** Poll one telemetry focus request; returns null if none. */
  public MissionTelemetryFocusRequest pollTelemetryFocus() {
    return telemetryFocusQueue.poll();
  }
}
