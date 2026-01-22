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
import com.smousseur.orbitlab.app.SimulationContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.OrbitColors;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.planet.PlanetDescriptor;
import com.smousseur.orbitlab.engine.scene.planet.PlanetHudMarkerView;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import java.util.Map;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

public final class PlanetPoseAppState extends BaseAppState {

  private final AssetManager assets;
  private final SimulationClock clock;
  private final SimulationContext context;

  private final Node bucket = new Node(SceneGraph.PLANETS_BUCKET);
  private final Node bodiesNode;

  public PlanetPoseAppState(SimulationContext context, AssetManager assets) {
    this.assets = Objects.requireNonNull(assets, "assets");
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
          new PlanetDescriptor(body, body.displayName(), OrbitColors.colorFor(body));

      PlanetHudMarkerView view = new PlanetHudMarkerView(assets, guiNode, desc);
      PlanetPresenter presenter = new PlanetPresenter(body, view);

      presenter.setColor(desc.orbitColor());
      presenter.setVisible(true);

      Spatial anchor = view.spatial();
      bucket.attachChild(anchor);
      context.addPlanet(body, presenter);
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
