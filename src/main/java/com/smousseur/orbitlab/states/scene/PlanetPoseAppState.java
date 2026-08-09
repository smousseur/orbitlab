package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
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
import com.smousseur.orbitlab.engine.scene.body.CoronaView;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
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

  /**
   * Emissive tint of the Sun, multiplied into its emissive texture. This decides the colour of the
   * disc and of the residual bloom that softens its limb, since both read the same product. It has
   * nothing to do with the halo proper, which is geometry and carries its own colour — see {@link
   * #SUN_CORONA}.
   *
   * <p>The Sun is the only body that gets one: it is also the only one that keeps its GLTF material
   * rather than being re-materialised with {@code WrapLighting}, which has no {@code Glow}
   * technique and therefore cannot bloom at all. Setting this explicitly rather than relying on the
   * asset's own {@code emissiveFactor} is what keeps the bloom from silently disappearing the day
   * the model is replaced.
   *
   * <p><b>Why the components are above 1, and why the largest is on blue.</b> The asset's texture
   * is a deeply saturated orange — measured over {@code material_baseColor.jpg}, the channel
   * medians are {@code R 0.98, G 0.42, B 0.008}, with blue only rising in the bright plage regions
   * ({@code p90 0.07}). A tint is a per-channel multiplier on that, so each factor is inversely
   * proportional to what the texture already carries: red barely needs a nudge to pin at 1, green
   * needs a little over two to reach it in the granules, and blue needs several times more just to
   * lift off zero. The clamp is the mechanism, not a side effect — red and green pinned with blue
   * trailing is what a saturated yellow <em>is</em>, and it is why the numbers below look lopsided.
   *
   * <p>Blue is deliberately left short of pinning: it is what keeps the granulation visible. Push
   * it far enough for the median to reach 1 and the whole disc goes white, losing exactly the
   * mottling that reads as a star's surface. It is the knob to turn if the Sun looks too orange
   * (up) or too washed out (down).
   */
  private static final ColorRGBA SUN_GLOW = new ColorRGBA(1.4f, 2.2f, 4f, 1f);

  /**
   * Colour and strength of the Sun's corona. The alpha is the strength: the glow is composited
   * additively, so it never darkens what it covers, and lowering it only makes the halo fainter.
   *
   * <p>The hue is the disc's, one notch warmer. The disc reads yellow because red and green clamp
   * while blue trails ({@link #SUN_GLOW}); the halo is dimmer, and a dim yellow on black reads
   * brown, so it is biased towards orange to hold its colour as it fades.
   */
  private static final ColorRGBA SUN_CORONA = new ColorRGBA(1f, 0.7f, 0.2f, 0.7f);

  /**
   * Exponent of the corona's radial decay past the limb. Higher hugs the disc more tightly; 1 would
   * be a linear ramp all the way out to the mesh's edge.
   */
  private static final float SUN_CORONA_FALLOFF = 3f;

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
              "models/planets/" + name + "/" + name + ".gltf",
              RenderContext.solar());

      LodView view =
          new LodView(
              guiNode,
              config,
              () -> onSelectPlanet(body),
              show3d -> sceneGraph.setOrbitVisible(body, !show3d));
      PlanetPresenter presenter = new PlanetPresenter(body, view);
      presenter.setVisible(!body.isSatellite());

      bucket.attachChild(view.spatial());
      nearBucket.attachChild(view.nearSpatial());
      context.addPlanet(body, presenter);
      ExecutorService assetExecutor = AssetFactory.get().assetLoadingExecutor();
      Model3dView model3dView = view.getModel3dView();
      if (body == SolarSystemBody.SUN) {
        // Attached straight away rather than with the model: the corona is our own geometry and
        // owes nothing to the GLTF load. Hanging it off the model bucket is what makes the LOD
        // culling hide the two together.
        new CoronaView(
            model3dView.getModelBucket(),
            PlanetRadius.radiusFor(body) / RenderContext.PLANET_METERS_PER_UNIT,
            SUN_CORONA,
            SUN_CORONA_FALLOFF);
      }
      CompletableFuture.supplyAsync(model3dView::loadModel, assetExecutor)
          .thenApply(
              spatial ->
                  body == SolarSystemBody.SUN
                      ? AssetFactory.get().applyGlow(spatial, SUN_GLOW)
                      : AssetFactory.get().applyLambert(spatial, 0.8f))
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
    FocusView focusView = context.focusView();
    SceneGraph sceneGraph = context.sceneGraph();

    Map<SolarSystemBody, PlanetPresenter> planets = context.getPlanets();
    for (Map.Entry<SolarSystemBody, PlanetPresenter> entry : planets.entrySet()) {
      SolarSystemBody body = entry.getKey();
      if (body == SolarSystemBody.SUN) continue;

      PlanetPresenter presenter = entry.getValue();

      if (body.isSatellite()) {
        boolean visible = focusView.isSatelliteVisible(body);
        presenter.setVisible(visible);
        sceneGraph.setOrbitVisible(body, visible);
        if (!visible) continue;
      }

      presenter.updatePose(t);
    }
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
