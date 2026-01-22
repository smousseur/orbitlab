package com.smousseur.orbitlab.app;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.events.OrbitEventBus;
import com.smousseur.orbitlab.engine.scene.graph.GuiGraph;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import com.smousseur.orbitlab.engine.scene.planet.PlanetView;

import java.util.EnumMap;
import java.util.Map;

public class ApplicationContext {
  private final SimulationConfig config;
  private final SimulationClock clock;
  private final OrbitEventBus orbitBus;
  private final SceneGraph sceneGraph;
  private final GuiGraph guiGraph;
  private final Map<SolarSystemBody, PlanetPresenter> planets =
      new EnumMap<>(SolarSystemBody.class);

  private final FocusView focusView = new FocusView();

  public ApplicationContext(Node rootNode, Node guiNode) {
    this();
    guiGraph.attachTo(guiNode);
    sceneGraph.attachTo(rootNode);
  }

  private ApplicationContext() {
    this.config = SimulationConfig.defaultSolarSystem();
    this.orbitBus = new OrbitEventBus();
    this.clock = new SimulationClock(config.computeClockStart());
    this.sceneGraph = new SceneGraph();
    this.guiGraph = new GuiGraph();
  }

  public SimulationConfig config() {
    return config;
  }

  public SimulationClock clock() {
    return clock;
  }

  public OrbitEventBus orbitBus() {
    return orbitBus;
  }

  public SceneGraph sceneGraph() {
    return sceneGraph;
  }

  public GuiGraph guiGraph() {
    return guiGraph;
  }

  public FocusView focusView() {
    return focusView;
  }

  public Spatial getPlanetSpatial(SolarSystemBody body) {
    return sceneGraph.getPlanetSpatial(body);
  }

  public void addPlanet(SolarSystemBody body, PlanetPresenter presenter) {
    planets.put(body, presenter);
  }

  public void clearPlanets() {
    planets.values().stream().map(PlanetPresenter::view).forEach(PlanetView::detach);
    planets.clear();
  }

  public void enablePlanets(boolean enable) {
    planets.values().stream().map(PlanetPresenter::view).forEach(view -> view.setVisible(enable));
  }

  public Map<SolarSystemBody, PlanetPresenter> getPlanets() {
    return planets;
  }
}
