package com.smousseur.orbitlab.app;

import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.EngineConfig;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.scene.graph.GuiGraph;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.states.camera.CameraTransitionAppState;
import com.smousseur.orbitlab.states.camera.OrbitCameraAppState;
import com.smousseur.orbitlab.states.mission.MissionRenderer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central dependency container for the OrbitLab application.
 *
 * <p>Holds the shared simulation configuration, clock, event bus, scene graphs, and planet
 * presenters. Passed to {@code AppState} implementations and other subsystems instead of individual
 * services, providing a single point of access to cross-cutting concerns.
 */
public class ApplicationContext {
  private final EngineConfig engineConfig = EngineConfig.defaultSolarSystem();
  private final SimulationConfig config;
  private final SimulationClock clock;
  private final EventBus eventBus;
  private final SceneGraph sceneGraph;
  private final GuiGraph guiGraph;
  private final Map<SolarSystemBody, PlanetPresenter> planets =
      new EnumMap<>(SolarSystemBody.class);
  private final Map<MissionId, MissionRenderer> missionRenderers = new LinkedHashMap<>();

  private final FocusView focusView;
  private final MissionContext missionContext;
  private final HudSurfaces hudSurfaces = new HudSurfaces();
  private Camera nearCamera;
  private Camera skyCamera;
  private OrbitCameraAppState orbitCamera;
  private CameraTransitionAppState cameraTransition;

  /**
   * Creates a new application context and attaches the scene and GUI graphs to the provided JME3
   * root nodes.
   *
   * @param rootNode the JME3 root node for 3D scene rendering
   * @param guiNode the JME3 GUI node for 2D overlay rendering
   */
  public ApplicationContext(Node rootNode, Node guiNode) {
    this();
    guiGraph.attachTo(guiNode);
    sceneGraph.attachTo(rootNode);
  }

  private ApplicationContext() {
    this.config = SimulationConfig.defaultSolarSystem();
    this.eventBus = new EventBus();
    this.clock = new SimulationClock(config.computeClockStart());
    this.sceneGraph = new SceneGraph();
    this.guiGraph = new GuiGraph();
    this.focusView = new FocusView(engineConfig);
    this.missionContext = new MissionContext();
  }

  /**
   * Returns the simulation configuration.
   *
   * @return the immutable simulation configuration
   */
  public SimulationConfig config() {
    return config;
  }

  /**
   * Returns the simulation clock that manages simulation time.
   *
   * @return the thread-safe simulation clock
   */
  public SimulationClock clock() {
    return clock;
  }

  /**
   * Returns the event bus used for asynchronous inter-state communication.
   *
   * @return the application event bus
   */
  public EventBus eventBus() {
    return eventBus;
  }

  /**
   * Returns the 3D scene graph that manages far and near rendering roots.
   *
   * @return the scene graph
   */
  public SceneGraph sceneGraph() {
    return sceneGraph;
  }

  /**
   * Returns the GUI graph for 2D overlay elements.
   *
   * @return the GUI graph
   */
  public GuiGraph guiGraph() {
    return guiGraph;
  }

  /**
   * Returns the current focus view state, which tracks the active view mode and target body.
   *
   * @return the focus view
   */
  public FocusView focusView() {
    return focusView;
  }

  /**
   * Retrieves the JME3 spatial node associated with a solar system body.
   *
   * @param body the solar system body
   * @return the spatial representing the body in the scene graph
   */
  public Spatial getBodySpatial(SolarSystemBody body) {
    return sceneGraph.getBodySpatial(body);
  }

  /**
   * Registers a planet presenter for a given solar system body.
   *
   * @param body the solar system body
   * @param presenter the presenter managing the planet's rendering and logic
   */
  public void addPlanet(SolarSystemBody body, PlanetPresenter presenter) {
    planets.put(body, presenter);
  }

  /** Detaches all planet views from the scene and clears the planet presenter registry. */
  public void clearPlanets() {
    planets.values().stream().map(PlanetPresenter::view).forEach(BodyView::detach);
    planets.clear();
  }

  /**
   * Toggles the visibility of all registered planet views.
   *
   * @param enable {@code true} to show planets, {@code false} to hide them
   */
  public void enablePlanets(boolean enable) {
    planets.values().stream().map(PlanetPresenter::view).forEach(view -> view.setVisible(enable));
  }

  /**
   * Returns the map of registered planet presenters, keyed by solar system body.
   *
   * @return the planet presenter map
   */
  public Map<SolarSystemBody, PlanetPresenter> getPlanets() {
    return planets;
  }

  /**
   * Registers a mission renderer so that other subsystems (e.g. the floating-origin state) can look
   * it up by mission id without going through {@code getState(...)}.
   *
   * <p>This map is the single registry of live mission renderers. {@code
   * MissionOrchestratorAppState} owns its lifecycle — it registers a renderer right after creating
   * it and deregisters it before disposing it — so no other component should add or remove entries.
   *
   * @param missionId the mission id
   * @param renderer the mission renderer to register
   */
  public void addMissionRenderer(MissionId missionId, MissionRenderer renderer) {
    missionRenderers.put(missionId, renderer);
  }

  /**
   * Deregisters a mission renderer and returns it so the caller can dispose it. No-op returning
   * {@code null} if the id is unknown.
   *
   * @param missionId the mission id to remove
   * @return the deregistered renderer, or {@code null} if none was registered for that id
   */
  public MissionRenderer removeMissionRenderer(MissionId missionId) {
    return missionRenderers.remove(missionId);
  }

  /**
   * Returns a read-only view of the registered mission renderers, in registration order.
   *
   * <p>The view is backed by the live map: callers that dispose renderers while walking it must
   * snapshot the ids first, since disposal deregisters the entry.
   *
   * @return an unmodifiable view of the mission renderer registry
   */
  public Map<MissionId, MissionRenderer> missionRenderers() {
    return Collections.unmodifiableMap(missionRenderers);
  }

  /**
   * Looks up a mission renderer by id.
   *
   * @param missionId the mission id, may be {@code null}
   * @return the matching renderer, or {@code null} if not registered
   */
  public MissionRenderer getMissionRenderer(MissionId missionId) {
    if (missionId == null) {
      return null;
    }
    return missionRenderers.get(missionId);
  }

  /**
   * Gets engine config.
   *
   * @return the engine config
   */
  public EngineConfig getEngineConfig() {
    return engineConfig;
  }

  /**
   * Returns the mission context that manages active missions.
   *
   * @return the mission context
   */
  public MissionContext missionContext() {
    return missionContext;
  }

  /**
   * Returns the registry of dismissible HUD surfaces. Each app state registers the surface it owns
   * and closes the handle in its {@code cleanup()}; the state that owns the {@code ESC} key asks
   * this registry which surface to send away.
   *
   * @return the HUD surface registry
   */
  public HudSurfaces hudSurfaces() {
    return hudSurfaces;
  }

  /**
   * Returns the near viewport camera (planet/spacecraft scale).
   *
   * @return the near camera
   */
  public Camera nearCamera() {
    return nearCamera;
  }

  /**
   * Sets the near viewport camera.
   *
   * @param nearCamera the near viewport camera
   */
  public void setNearCamera(Camera nearCamera) {
    this.nearCamera = nearCamera;
  }

  /**
   * Returns the orbit camera state, which owns the far camera's pose.
   *
   * @return the orbit camera state, or {@code null} before the application has registered it
   */
  public OrbitCameraAppState orbitCamera() {
    return orbitCamera;
  }

  /**
   * Registers the orbit camera state. Called by the application once the state exists, so other
   * states can drive the camera without reaching for it through {@code getState}.
   *
   * @param orbitCamera the orbit camera state
   */
  public void setOrbitCamera(OrbitCameraAppState orbitCamera) {
    this.orbitCamera = orbitCamera;
  }

  /**
   * Returns the camera transition state, the single entry point for changing the camera's focus.
   *
   * @return the camera transition state, or {@code null} before the application has registered it
   */
  public CameraTransitionAppState cameraTransition() {
    return cameraTransition;
  }

  /**
   * Registers the camera transition state.
   *
   * @param cameraTransition the camera transition state
   */
  public void setCameraTransition(CameraTransitionAppState cameraTransition) {
    this.cameraTransition = cameraTransition;
  }

  /**
   * Returns the sky pre-view camera. Only its rotation and field of view are meaningful: the sky
   * shader ignores the camera translation, and the camera owns a fixed depth range.
   *
   * @return the sky camera
   */
  public Camera skyCamera() {
    return skyCamera;
  }

  /**
   * Sets the sky pre-view camera.
   *
   * @param skyCamera the sky pre-view camera
   */
  public void setSkyCamera(Camera skyCamera) {
    this.skyCamera = skyCamera;
  }
}
