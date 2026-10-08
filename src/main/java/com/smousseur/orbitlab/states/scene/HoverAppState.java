package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.HoverState;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.HoverConfig;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.hover.HoverFade;
import com.smousseur.orbitlab.engine.scene.hover.HoverResolver;
import com.smousseur.orbitlab.engine.scene.hover.HoverTarget;
import com.smousseur.orbitlab.engine.scene.hover.OrbitHoverDetector;
import com.smousseur.orbitlab.engine.scene.hover.OrbitScreenProjector;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import com.smousseur.orbitlab.ui.SceneHoverSensor;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves, once a frame, which planet the cursor designates — by its icon or by its orbit — and
 * lights that planet's icon in a fade.
 *
 * <p>Each frame, in order:
 *
 * <ol>
 *   <li>the hover is frozen while the camera turns or flies, so that nothing sweeping under a still
 *       cursor lights up;
 *   <li>{@link HoverResolver} designates a planet from the facts the listeners recorded in {@link
 *       HoverState}; the orbit detection it may ask for projects the orbit ribbons through the far
 *       camera — the one they are drawn with — and searches them around the cursor;
 *   <li>every planet's fade moves toward lit or unlit, in real time, and its intensity is pushed to
 *       the planet's view;
 *   <li>the scene sensor follows the screen's size.
 * </ol>
 *
 * <p>A left press over the scene on a planet designated by its orbit flies to that planet, as a
 * press on its icon does.
 *
 * <p><b>Attach order matters.</b> This state runs after the orbit camera, whose pose it projects
 * through, and before the mission orchestrator and the planet markers: the freeze the mission icons
 * read and the intensities the icons are sized from must be set before the icons are placed. The
 * Moon's orbit node is moved later in the frame, by the orbit runtime, so the Moon's orbit is
 * detected where it was drawn one frame earlier.
 */
public final class HoverAppState extends BaseAppState {

  /** The name {@code OrbitLineFactory} gives a body's ribbon, prefixed to the body's name. */
  private static final String RIBBON_PREFIX = "OrbitLine-";

  private final ApplicationContext context;
  private final HoverConfig config;
  private final HoverState state;
  private final Set<SolarSystemBody> orbitBodies;
  private final OrbitScreenProjector projector = new OrbitScreenProjector();
  private final OrbitHoverDetector detector;
  private final HoverResolver.OrbitPicker orbitPicker = this::pickOrbit;
  private final Map<SolarSystemBody, Geometry> ribbons = new EnumMap<>(SolarSystemBody.class);
  private final Map<SolarSystemBody, HoverFade> fades = new EnumMap<>(SolarSystemBody.class);

  private InputManager inputManager;
  private Camera camera;
  private SceneHoverSensor sensor;

  /**
   * Creates the hover state.
   *
   * @param context the application context, for the hover state, the cameras, the planets and the
   *     orbit layer
   */
  public HoverAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
    this.config = context.getEngineConfig().hover();
    this.state = context.hoverState();
    this.orbitBodies = context.config().orbitBodies();
    this.detector = new OrbitHoverDetector(config.enterPx(), config.exitPx());
    for (SolarSystemBody body : SolarSystemBody.values()) {
      fades.put(body, HoverFade.of(config));
    }
  }

  @Override
  protected void initialize(Application app) {
    Objects.requireNonNull(context.orbitCamera(), "orbitCamera");
    Objects.requireNonNull(context.cameraTransition(), "cameraTransition");
    inputManager = app.getInputManager();
    camera = app.getCamera();
    sensor = new SceneHoverSensor(state, this::onScenePressed);
    sensor.update(camera);
    context.guiGraph().getSceneSensorNode().attachChild(sensor.getNode());
  }

  @Override
  public void update(float tpf) {
    boolean frozen = context.orbitCamera().isRotating() || context.cameraTransition().isActive();
    HoverTarget target =
        HoverResolver.resolve(
                frozen,
                state.target().orElse(null),
                state.iconUnderCursor().orElse(null),
                state.isSceneUnderCursor(),
                orbitPicker)
            .orElse(null);
    state.update(target, frozen);

    SolarSystemBody lit = target == null ? null : target.body();
    for (PlanetPresenter presenter : context.getPlanets().values()) {
      SolarSystemBody body = presenter.body();
      presenter.view().setHoverIntensity(fades.get(body).advance(body == lit, tpf));
    }
    sensor.update(camera);
  }

  private Optional<SolarSystemBody> pickOrbit(SolarSystemBody current) {
    collectRibbons();
    Vector2f cursor = inputManager.getCursorPosition();
    return detector.pick(
        projector.project(camera, ribbons, config.detectionStride()), cursor.x, cursor.y, current);
  }

  /**
   * Looks up the ribbons not found yet. They are built by the orbit states, which are attached
   * after this one, and a body whose dataset file is missing never gets one.
   */
  private void collectRibbons() {
    if (ribbons.size() == orbitBodies.size()) {
      return;
    }
    SceneGraph.OrbitLayer layer = context.sceneGraph().orbits();
    for (SolarSystemBody body : orbitBodies) {
      if (!ribbons.containsKey(body)
          && layer.orbitNode(body).getChild(RIBBON_PREFIX + body.name()) instanceof Geometry g) {
        ribbons.put(body, g);
      }
    }
  }

  private void onScenePressed() {
    state.routeScenePress(body -> context.cameraTransition().requestPlanet(body));
  }

  @Override
  protected void cleanup(Application app) {
    sensor.getNode().removeFromParent();
    state.update(null, false);
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
