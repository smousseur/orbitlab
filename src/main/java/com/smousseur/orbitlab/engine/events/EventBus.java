package com.smousseur.orbitlab.engine.events;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.orbit.OrbitPath;

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

  /** Actions that can be requested on the UI from widgets. */
  public enum UiNavigation {
    OPEN_MISSION_WIZARD
  }

  private final ConcurrentLinkedQueue<UiNavigation> uiNavigationQueue =
      new ConcurrentLinkedQueue<>();

  /**
   * Publishes a UI navigation event. Can be called from any thread.
   *
   * @param nav the navigation event
   */
  public void publishUiNavigation(UiNavigation nav) {
    uiNavigationQueue.add(Objects.requireNonNull(nav));
  }

  /** Poll one UI navigation event; returns null if none. */
  public UiNavigation pollUiNavigation() {
    return uiNavigationQueue.poll();
  }
}
