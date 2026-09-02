package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.TextureDiagnostics;
import com.smousseur.orbitlab.engine.scene.MeshDivergence;
import com.smousseur.orbitlab.engine.scene.MeshGuard;
import com.smousseur.orbitlab.engine.scene.PlanetColors;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.CoronaView;
import com.smousseur.orbitlab.engine.scene.body.EclipseGeometry;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisServiceRegistry;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
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

  private static final Logger logger = LogManager.getLogger(PlanetPoseAppState.class);

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
      String name = body.displayName().toLowerCase(Locale.ROOT);
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
              context.model3dAttacher(),
              () -> onSelectPlanet(body),
              show3d -> sceneGraph.setOrbitVisible(body, !show3d));
      PlanetPresenter presenter = new PlanetPresenter(body, view);
      presenter.setVisible(!body.isSatellite());

      bucket.attachChild(view.spatial());
      nearBucket.attachChild(view.nearSpatial());
      context.addPlanet(body, presenter);
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
      CompletableFuture.supplyAsync(
              model3dView::loadModel, AssetFactory.get().assetLoadingExecutor())
          .thenApply(
              spatial -> {
                // Before re-materialisation, on the asset as authored: this asks whether the file
                // still carries what PlanetMeshCorrection was calibrated against, which is a
                // question about the asset, not about how it ends up shaded.
                MeshGuard.verify(body, spatial).ifPresent(PlanetPoseAppState::warnDivergence);
                isolateAtmosphereShell(body, model3dView, spatial);
                return spatial;
              })
          .thenApply(
              spatial ->
                  body == SolarSystemBody.SUN
                      ? AssetFactory.get().applyGlow(spatial, SUN_GLOW)
                      : AssetFactory.get().applyLambert(spatial, 0.8f))
          .thenApply(
              spatial -> {
                // After re-materialisation, so the report describes the textures actually bound at
                // draw time rather than the ones the GLTF loader handed over.
                TextureDiagnostics.logTextures(config.displayName(), spatial);
                return spatial;
              })
          .thenAccept(model3dView::onModelLoaded);
    }
  }

  /**
   * Gives a body's cloud deck a pivot of its own, while the model is still unattached and private
   * to this thread — Venus is the only body with one (L4 of {@code
   * docs/orientation-planetes/01-decoupage.md}). The axis is the pole the probe measured, in the
   * model's own axes, which is exactly what the committed calibration carries.
   */
  private static void isolateAtmosphereShell(
      SolarSystemBody body, Model3dView model3dView, Spatial spatial) {
    PlanetMeshCorrection.atmosphereShellFor(body)
        .ifPresent(
            shell ->
                PlanetMeshCorrection.calibrationFor(body)
                    .ifPresent(
                        calibration ->
                            model3dView.isolateShell(
                                spatial, shell.nodeNamePrefix(), calibration.measured().pole())));
  }

  /**
   * Says, once per model load, that an asset no longer matches the frame committed for it — the
   * detection that lets a mesh be replaced without the body silently ending up drawn turned (see
   * {@code docs/orientation-planetes/01-decoupage.md}, L1).
   */
  private static void warnDivergence(MeshDivergence divergence) {
    logger.warn(
        "Mesh asset for {} diverges from its committed calibration: frame moved {}{}."
            + " Re-run './gradlew meshProbe' and update PlanetMeshCorrection.",
        divergence.body().displayName(),
        String.format(Locale.ROOT, "%.1f deg", divergence.frameDeviationDeg()),
        divergence.textureChanged() ? ", and the base colour texture changed size" : "");
  }

  private void onSelectPlanet(SolarSystemBody body) {
    // The focus switch and the framing distance both belong to CameraTransitionAppState, which
    // animates its way there and drops the click if a transition is already playing.
    context.cameraTransition().requestPlanet(body);
  }

  @Override
  public void update(float tpf) {
    AbsoluteDate t = clock.now();
    FocusView focusView = context.focusView();
    SceneGraph sceneGraph = context.sceneGraph();

    // Sampled once per frame and reused for both eclipse directions below (docs/eclipses/
    // 01-decoupage.md, L3): the Moon shadowing the Earth and the Earth shadowing the Moon are the
    // same pair of positions read the other way around, not two independent lookups.
    Optional<Vector3D> earthHelio = sampleHelioPosition(SolarSystemBody.EARTH, t);
    Optional<Vector3D> moonHelio = sampleHelioPosition(SolarSystemBody.MOON, t);

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

      if (body == SolarSystemBody.MOON && earthHelio.isPresent() && moonHelio.isPresent()) {
        pushEclipseOccluder(presenter, SolarSystemBody.EARTH, earthHelio.get(), moonHelio.get());
      } else if (body == SolarSystemBody.EARTH && earthHelio.isPresent() && moonHelio.isPresent()) {
        pushEclipseOccluder(presenter, SolarSystemBody.MOON, moonHelio.get(), earthHelio.get());
      }
    }
  }

  /**
   * The given body's heliocentric ICRF position, or empty while the ephemeris buffer has not caught
   * up to {@code t} yet — the same degradation {@link PlanetPresenter#updatePose} already accepts
   * for the body's own pose.
   */
  private Optional<Vector3D> sampleHelioPosition(SolarSystemBody body, AbsoluteDate t) {
    return EphemerisServiceRegistry.get()
        .orElseThrow(() -> new OrbitlabException("Cannot get EphemerisService"))
        .trySampleHelioIcrf(body, t)
        .map(Map.Entry::getKey);
  }

  /**
   * Pushes {@code occluderBody} as {@code presenter}'s eclipse occulter (`docs/eclipses/
   * 01-decoupage.md`, L2 for the Moon eclipsed by the Earth, L3 for the Earth showing the Moon's
   * shadow spot) — the same method for both directions, since the geometry is symmetric: only which
   * body is doing the occulting and which is receiving the shading changes.
   *
   * <p>Reuses {@link EclipseGeometry#sunApparentRadius}, the L1 vessel case's shared static utility
   * — no duplicated formula.
   *
   * <p><b>Scoped to a body shown by its own focus, not a lunar mission's viewpoint.</b> The near
   * viewport's single globe sits at whatever offset {@code FloatingOriginAppState} gives {@code
   * nearFrame} that frame — zero when a planet is focused, but the negated spacecraft position when
   * a mission is followed. Computing the Earth-Moon delta directly (as done here) is exact for the
   * first case and carries a small, undocumented-until-now error in the second — a spacecraft near
   * either body offsets its assumed centre by its own orbital radius, a fraction of a degree
   * against the ~384 000 km Earth-Moon baseline. Reading {@code nearFrame}'s actual offset would
   * fix it, but that is a cross-state read {@code FloatingOriginAppState}'s own docstring warns is
   * order- sensitive (the RND-1 lesson); not worth it for an effect that only matters while the
   * eclipsed body happens to also be the backdrop of an active lunar mission.
   *
   * @param presenter the eclipsed body's presenter, whose view receives the occluder
   * @param occluderBody the body doing the occulting
   * @param occluderHelio the occulter's heliocentric ICRF position, in meters
   * @param targetHelio the eclipsed body's own heliocentric ICRF position, in meters — its distance
   *     from the Sun (the origin of this frame) is what {@link EclipseGeometry#sunApparentRadius}
   *     needs
   */
  private void pushEclipseOccluder(
      PlanetPresenter presenter,
      SolarSystemBody occluderBody,
      Vector3D occluderHelio,
      Vector3D targetHelio) {
    RenderContext ctx = RenderContext.planet(presenter.body());

    Vector3D occluderPositionMeters = occluderHelio.subtract(targetHelio);
    Vector3f occluderPositionRender =
        JmeVectorAdapter.toJmeBodyRelativePosition(occluderPositionMeters, ctx);
    float occluderRadiusRender =
        (float) (PlanetRadius.radiusFor(occluderBody) * ctx.unitsPerMeter());

    double sunDistanceMeters = targetHelio.getNorm();
    Vector3D sunDirectionIcrf = targetHelio.negate().normalize();
    Vector3f sunDirectionRender =
        JmeVectorAdapter.toVector3f(ctx.axisConvention().icrfToJme(sunDirectionIcrf));
    float sunApparentRadius = (float) EclipseGeometry.sunApparentRadius(sunDistanceMeters);

    presenter
        .view()
        .setOccluder(
            occluderPositionRender, occluderRadiusRender, sunDirectionRender, sunApparentRadius);
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
