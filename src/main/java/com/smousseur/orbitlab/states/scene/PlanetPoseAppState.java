package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.PlanetColors;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.planet.PlanetDescriptor;
import com.smousseur.orbitlab.engine.scene.planet.PlanetLodView;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.smousseur.orbitlab.engine.scene.planet.lod.Planet3dView;
import org.orekit.time.AbsoluteDate;

public final class PlanetPoseAppState extends BaseAppState {

  private final SimulationClock clock;
  private final ApplicationContext context;

  private final Node bucket = new Node(SceneGraph.PLANETS_BUCKET);
  private final Node bodiesNode;

  public PlanetPoseAppState(ApplicationContext context) {
    this.clock = Objects.requireNonNull(context.clock(), "clock");
    this.context = context;
    bodiesNode = context.sceneGraph().bodiesNode();
  }

  @Override
  protected void initialize(Application app) {
    Node guiNode = context.guiGraph().getPlanetBillboardsNode();
    bodiesNode.attachChild(bucket);

    for (SolarSystemBody body : SolarSystemBody.values()) {
      PlanetDescriptor desc =
          new PlanetDescriptor(body, body.displayName(), PlanetColors.colorFor(body));

      PlanetLodView view = new PlanetLodView(guiNode, desc);
      PlanetPresenter presenter = new PlanetPresenter(body, view);
      presenter.setVisible(true);

      Spatial anchor = view.spatial();
      bucket.attachChild(anchor);
      context.addPlanet(body, presenter);
      ExecutorService assetExecutor = AssetFactory.get().assetLoadingExecutor();
      Planet3dView model3dView = view.getModel3dView();
      CompletableFuture.supplyAsync(model3dView::loadModel, assetExecutor)
          .thenAccept(model3dView::onModelLoaded);
    }
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
    context.clearPlanets();
  }

  @Override
  protected void onEnable() {
    bucket.setCullHint(Node.CullHint.Inherit);
    context.enablePlanets(true);
  }

  @Override
  protected void onDisable() {
    bucket.setCullHint(Node.CullHint.Always);
    context.enablePlanets(false);
  }

  private static void attachDebugMarker(
      AssetManager assets, Spatial anchor, ColorRGBA color, String suffix) {

    // Petit marqueur 3D visible à l’emplacement exact de l’ancre.
    // Taille à ajuster selon ton échelle "solar".
    Sphere sphere = new Sphere(12, 12, 20.5f);
    Geometry g = new Geometry("AnchorDebug-" + suffix, sphere);

    Material mat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", color);
    g.setMaterial(mat);

    if (anchor instanceof Node n) {
      n.attachChild(g);
    } else {
      // Fallback: si l’ancre n’est pas un Node, on ne peut pas y attacher un enfant.
      // Dans ce cas, on met le marqueur au même parent (mais il ne suivra pas automatiquement
      // si l’ancre change de parent).
      Spatial parent = anchor.getParent();
      if (parent instanceof Node pn) {
        pn.attachChild(g);
        g.setLocalTranslation(anchor.getLocalTranslation());
      }
    }
  }
}
