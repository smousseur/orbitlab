package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.PlanetColors;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import com.smousseur.orbitlab.engine.scene.planet.lod.PlanetLodTuning;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.orekit.time.AbsoluteDate;

/**
 * Application state responsible for creating planet scene nodes and updating their positions and
 * rotations each frame based on ephemeris data.
 *
 * <p>During initialization, constructs a {@link PlanetPresenter} and {@link LodView} for every
 * solar system body, attaches them to the scene graph, and asynchronously loads their 3D models.
 * Each frame, queries the simulation clock and updates planet poses (position and orientation) for
 * all non-Sun bodies.
 */
public final class PlanetPoseAppState extends BaseAppState {

  private final SimulationClock clock;
  private final ApplicationContext context;

  private final Node bucket = new Node(SceneGraph.PLANETS_BUCKET);
  private final Node nearBucket = new Node(SceneGraph.PLANETS_BUCKET);
  private final Node bodiesNode;
  private final Node nearBodiesNode;

  /**
   * Creates a new planet pose state.
   *
   * @param context the application context providing clock, scene graph, and planet management
   */
  public PlanetPoseAppState(ApplicationContext context) {
    this.clock = Objects.requireNonNull(context.clock(), "clock");
    this.context = context;
    bodiesNode = context.sceneGraph().bodiesNode();
    nearBodiesNode = context.sceneGraph().nearBodiesNode();
  }

  @Override
  protected void initialize(Application app) {
    Node guiNode = context.guiGraph().getPlanetBillboardsNode();
    SceneGraph sceneGraph = context.sceneGraph();
    bodiesNode.attachChild(bucket);
    nearBodiesNode.attachChild(nearBucket);

    for (SolarSystemBody body : SolarSystemBody.values()) {
      String name = body.displayName().toLowerCase();
      BodyRenderConfig config =
          new BodyRenderConfig(
              body.name(),
              body.displayName(),
              PlanetColors.colorFor(body),
              PlanetRadius.radiusFor(body),
              PlanetLodTuning.lodDistanceRatio(body),
              "models/planets/" + name + "/" + name + ".gltf",
              RenderContext.solar());

      LodView view =
          new LodView(
              guiNode,
              config,
              () -> onSelectPlanet(body),
              show3d -> sceneGraph.setOrbitVisible(body, !show3d));
      PlanetPresenter presenter = new PlanetPresenter(body, view);
      presenter.setVisible(true);

      bucket.attachChild(view.spatial());
      nearBucket.attachChild(view.nearSpatial());
      context.addPlanet(body, presenter);
      ExecutorService assetExecutor = AssetFactory.get().assetLoadingExecutor();
      Model3dView model3dView = view.getModel3dView();
      CompletableFuture.supplyAsync(model3dView::loadModel, assetExecutor)
          .thenAccept(model3dView::onModelLoaded);
    }
  }

  private void onSelectPlanet(SolarSystemBody body) {
    FocusView focusView = context.focusView();
    double radius = PlanetRadius.radiusFor(body);
    double distance = radius * 5 / RenderContext.SOLAR_METERS_PER_UNIT;
    focusView.setCameraDistance((float) distance);
    focusView.viewPlanet(body);
  }

  @Override
  public void update(float tpf) {
    AbsoluteDate t = clock.now();

    Map<SolarSystemBody, PlanetPresenter> planets = context.getPlanets();
    planets.keySet().stream()
        .filter(body -> body != SolarSystemBody.SUN)
        .forEach(
            body -> {
              PlanetPresenter presenter = planets.get(body);
              presenter.updatePose(t);
            });
  }

  @Override
  protected void cleanup(Application app) {
    bucket.removeFromParent();
    nearBucket.removeFromParent();
    context.clearPlanets();
  }

  @Override
  protected void onEnable() {
    bucket.setCullHint(Node.CullHint.Inherit);
    nearBucket.setCullHint(Node.CullHint.Inherit);
    context.enablePlanets(true);
  }

  @Override
  protected void onDisable() {
    bucket.setCullHint(Node.CullHint.Always);
    nearBucket.setCullHint(Node.CullHint.Always);
    context.enablePlanets(false);
  }
}
