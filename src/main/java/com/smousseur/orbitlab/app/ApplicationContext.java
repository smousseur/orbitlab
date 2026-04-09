package com.smousseur.orbitlab.app;

import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.EngineConfig;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.engine.scene.graph.GuiGraph;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.states.mission.MissionRenderer;

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
  private final Map<String, MissionRenderer> missionRenderers = new LinkedHashMap<>();

  private final FocusView focusView;
  private final MissionContext missionContext;
  private Camera nearCamera;

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
   * Registers a mission renderer so that other subsystems (e.g. the floating-origin state) can
   * look it up by mission name without going through {@code getState(...)}.
   *
   * @param name the unique mission name
   * @param renderer the mission renderer to register
   */
  public void addMissionRenderer(String name, MissionRenderer renderer) {
    missionRenderers.put(name, renderer);
  }

  /**
   * Deregisters a mission renderer. No-op if the name is unknown.
   *
   * @param name the mission name to remove
   */
  public void removeMissionRenderer(String name) {
    missionRenderers.remove(name);
  }

  /**
   * Looks up a mission renderer by name.
   *
   * @param name the mission name, may be {@code null}
   * @return the matching renderer, or {@code null} if not registered
   */
  public MissionRenderer getMissionRenderer(String name) {
    if (name == null) {
      return null;
    }
    return missionRenderers.get(name);
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
}
